#!/usr/bin/env bash
# Idempotent installer for the Rowan hub on a Raspberry Pi (Raspberry Pi OS / Debian).
# Run from the copied server folder as the normal user:   bash deploy/install.sh
# Safe to run again after updating the code: it reinstalls dependencies and restarts the service.
set -euo pipefail

SERVICE="rowan-hub"
RUN_USER="${RUN_USER:-pi}"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT_DEFAULT=8787

say() { printf '\n==> %s\n' "$*"; }

if [ "$(id -u)" -eq 0 ]; then SUDO=""; else SUDO="sudo"; fi
as_run_user() { if [ "$(id -un)" = "$RUN_USER" ]; then "$@"; else $SUDO -u "$RUN_USER" "$@"; fi; }

if ! id "$RUN_USER" >/dev/null 2>&1; then
  echo "User '$RUN_USER' does not exist. Re-run with RUN_USER=<your user> bash deploy/install.sh" >&2
  exit 1
fi

# --- 1. Node.js 20+ ---------------------------------------------------------
node_major() { command -v node >/dev/null 2>&1 && node -p 'process.versions.node.split(".")[0]' || echo 0; }
if [ "$(node_major)" -lt 20 ]; then
  say "Installing Node.js 22 (NodeSource)"
  $SUDO apt-get update
  $SUDO apt-get install -y curl ca-certificates
  curl -fsSL https://deb.nodesource.com/setup_22.x | $SUDO -E bash -
  $SUDO apt-get install -y nodejs
else
  say "Node.js $(node -v) already installed"
fi
NODE_BIN="$(command -v node)"

# --- 2. mDNS so that mosaic.local works (the Pi's hostname is "mosaic") ------------------------------------
if ! dpkg -s avahi-daemon >/dev/null 2>&1; then
  say "Installing avahi-daemon (mosaic.local)"
  $SUDO apt-get install -y avahi-daemon
fi
if [ "$(hostname)" != "rowan" ]; then
  echo "Note: this Pi's hostname is '$(hostname)'. For http://mosaic.local run: sudo hostnamectl set-hostname mosaic"
fi

# --- 3. Dependencies, .env, data dir ---------------------------------------
say "Installing npm dependencies (production only)"
cd "$APP_DIR"
as_run_user npm install --omit=dev --no-audit --no-fund

if [ ! -f .env ]; then
  say "Creating .env from .env.example"
  as_run_user cp .env.example .env
fi
chmod 600 .env
$SUDO chown "$RUN_USER":"$(id -gn "$RUN_USER")" .env 2>/dev/null || true
as_run_user mkdir -p data

# --- 4. systemd service -----------------------------------------------------
say "Installing systemd service '$SERVICE'"
UNIT_TMP="$(mktemp)"
sed -e "s|^User=.*|User=$RUN_USER|" \
    -e "s|/home/pi/rowan-server|$APP_DIR|g" \
    -e "s|^ExecStart=.*|ExecStart=$NODE_BIN server.js|" \
    "$APP_DIR/deploy/rowan-hub.service" > "$UNIT_TMP"
$SUDO install -m 644 "$UNIT_TMP" "/etc/systemd/system/$SERVICE.service"
rm -f "$UNIT_TMP"
$SUDO systemctl daemon-reload
$SUDO systemctl enable "$SERVICE" >/dev/null
$SUDO systemctl restart "$SERVICE"

# --- 5. Check ----------------------------------------------------------------
PORT="$(grep -E '^PORT=' .env 2>/dev/null | tail -n1 | cut -d= -f2 | tr -d '"' || true)"
PORT="${PORT:-$PORT_DEFAULT}"
say "Waiting for the hub to answer"
for _ in $(seq 1 20); do
  if curl -fsS "http://127.0.0.1:$PORT/api/ping" >/dev/null 2>&1; then
    echo "Rowan hub is running."
    echo
    echo "  URL:   http://mosaic.local:$PORT"
    echo "  Logs:  journalctl -u $SERVICE -f"
    if ! grep -Eq '^SMTP_PASS=.+' .env; then
      echo
      echo "  E-mail is in DEV MODE (codes only appear in the logs)."
      echo "  Paste the Gmail app password after SMTP_PASS= in $APP_DIR/.env, then: sudo systemctl restart $SERVICE"
    fi
    exit 0
  fi
  sleep 1
done
echo "The service did not answer in time. Check: journalctl -u $SERVICE -n 50 --no-pager" >&2
exit 1
