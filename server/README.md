# Rowan Rowan hub

Small Node server (no framework, one dependency: `nodemailer`) for the Rowan gym app: accounts with e-mail
verification, leaderboard and optional backup. Data lives in plain JSON files under `data/`.

## Run it locally

```bash
npm install
npm start            # http://localhost:8787  (PORT=9000 npm start to change the port)
```

Without an SMTP password the server runs in **DEV MODE**: nothing is e-mailed, the code is printed in the console
(`DEV MODE: code for you@example.com is 123456`). Handy for testing the app.

## Where to paste the Gmail app password

1. Google account `okofficial.info@gmail.com` -> Security -> 2-Step Verification (must be on) -> **App passwords**.
2. Create one (name it "Rowan") and copy the 16 letters.
3. Open `.env` (next to this README; copy `.env.example` if missing) and paste it after `SMTP_PASS=`:

   ```
   SMTP_USER=okofficial.info@gmail.com
   SMTP_PASS=abcd efgh ijkl mnop
   MAIL_FROM_NAME=Rowan
   ```
   Spaces are ignored. Never commit `.env` (it is in `.gitignore`).
4. Restart: `sudo systemctl restart rowan-hub` (or stop/start `npm start`). The start-up log says
   `e-mail: SMTP smtp.gmail.com:465` when the password is picked up.

Emails are bilingual (`lang` = `en` or `it`), dark graphite card with the lime Rowan dot-grid motif.

## Install on the Raspberry Pi (hostname `rowan`)

1. Copy this whole folder to the Pi as `/home/pi/rowan-server` (without `node_modules` and `data`), e.g.
   `scp -r server pi@mosaic.local:/home/pi/rowan-server`
2. On the Pi: `cd ~/rowan-server && bash deploy/install.sh`

The installer is idempotent. It installs Node 22 via NodeSource if Node 20+ is missing, runs
`npm install --omit=dev`, creates `.env` from `.env.example` if absent, installs and enables the systemd unit
`rowan-hub` (starts on boot, restarts on crash) and prints `http://mosaic.local:8787`. Run it again after
updating the code. If the folder is elsewhere or the user is not `pi`, it adapts the unit
(`RUN_USER=me bash deploy/install.sh`). If the script has Windows line endings: `sed -i 's/\r$//' deploy/install.sh`.

Tip: set the Pi's timezone (`sudo timedatectl set-timezone Europe/Rome`); the weekly leaderboard resets on Monday
in the server's local time.

## Logs and service control

```bash
journalctl -u rowan-hub -f           # live log (one line per request, never bodies or tokens)
journalctl -u rowan-hub -n 100 --no-pager
sudo systemctl status rowan-hub
sudo systemctl restart rowan-hub
```

## Test with curl

```bash
H=http://mosaic.local:8787        # or http://localhost:8787
curl $H/api/ping
curl -X POST $H/api/register -d '{"email":"me@example.com","password":"supersecret1","name":"Me","lang":"it"}'
# DEV MODE: read the 6-digit code in the log; otherwise in your inbox
curl -X POST $H/api/verify -d '{"email":"me@example.com","code":"123456"}'     # -> {"token":"...","user":{...}}
T=<token>
curl -H "Authorization: Bearer $T" $H/api/me
curl -X POST -H "Authorization: Bearer $T" $H/api/sync \
     -d '{"xp":500,"weekXp":120,"level":4,"workouts":10,"streakWeeks":2,"volume":12345,"prs":3,"name":"Me"}'
curl -H "Authorization: Bearer $T" "$H/api/leaderboard?period=week"
curl -X PUT -H "Authorization: Bearer $T" --data-binary @backup.json $H/api/backup
curl -H "Authorization: Bearer $T" $H/api/backup
curl -X POST $H/api/login  -d '{"email":"me@example.com","password":"supersecret1"}'
curl -X POST $H/api/forgot -d '{"email":"me@example.com","lang":"en"}'
curl -X POST $H/api/reset  -d '{"email":"me@example.com","code":"123456","password":"newsecret123"}'
```

Open `http://mosaic.local:8787/` in a browser for the "Rowan hub is up" page.

## Behaviour notes

- Timestamps (`time`, `createdAt`, `updatedAt`, `savedAt`) are Unix epoch **milliseconds**.
- Codes: 6 digits, valid 10 minutes, 5 wrong attempts then the code is dead (HTTP 429 `too_many_attempts`;
  ask for a new one with `/api/resend` or `/api/forgot`). Passwords: 8 to 200 characters, stored with scrypt.
- Rate limits (per IP and per e-mail, 15 minute window) on register, login, resend, forgot, verify, reset, plus at
  most 6 e-mails per address per window. Exceeded -> HTTP 429 `rate_limited` with `Retry-After`.
- A password reset signs out all other devices and also verifies the address.
- Leaderboard: ties are ranked by who reached the score first. Top 50; if you are lower, you take the 50th slot
  with your real rank. `period=week` ranks by `weekXp` (returned in `xp`); a user who did not sync during the
  current week counts as 0 for that week.
- Backups: `data/backups/<userId>.json`, the previous copy is kept as `<userId>.json.prev`. Body limit 20 MB on
  `/api/backup`, 64 KB elsewhere.
- Back up the `data/` folder to keep accounts; deleting it resets the hub.
- Errors are JSON: `{"error":"code","message":"..."}`.
