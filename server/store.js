'use strict';
// JSON-file persistence with an in-memory cache and atomic writes (tmp + fsync + rename).
// Everything is synchronous on purpose: Node is single threaded, so every read-modify-write
// below is naturally atomic with respect to other requests.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const cfg = require('./config');

const DATA_DIR = cfg.dataDir;
const BACKUP_DIR = path.join(DATA_DIR, 'backups');
fs.mkdirSync(BACKUP_DIR, { recursive: true });

const MAX_SESSIONS_PER_USER = 20;

// ---------- low level ----------

function writeFileAtomic(file, data) {
  const tmp = `${file}.${process.pid}.tmp`;
  const fd = fs.openSync(tmp, 'w');
  try {
    fs.writeFileSync(fd, data);
    fs.fsyncSync(fd); // survive a power cut on the Pi's SD card
  } finally {
    fs.closeSync(fd);
  }
  fs.renameSync(tmp, file);
}

function loadJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    if (e.code === 'ENOENT') return fallback;
    // Corrupt file: keep it for inspection instead of silently overwriting it.
    const bad = `${file}.corrupt-${Date.now()}`;
    console.error(`[store] ${path.basename(file)} unreadable (${e.message}); moved to ${path.basename(bad)}`);
    try { fs.renameSync(file, bad); } catch { /* ignore */ }
    return fallback;
  }
}

const files = {
  users: path.join(DATA_DIR, 'users.json'),
  sessions: path.join(DATA_DIR, 'sessions.json'),
  leaderboard: path.join(DATA_DIR, 'leaderboard.json'),
};

// ---------- in-memory cache ----------

const db = {
  users: loadJson(files.users, {}),             // id -> user
  sessions: loadJson(files.sessions, {}),       // sha256(token) -> { userId, createdAt }
  leaderboard: loadJson(files.leaderboard, {}), // userId -> snapshot
};

const emailIndex = new Map();
for (const u of Object.values(db.users)) emailIndex.set(u.email, u.id);

function persist(name) {
  writeFileAtomic(files[name], JSON.stringify(db[name]));
}

// ---------- users ----------

function findUserByEmail(email) {
  const id = emailIndex.get(email);
  return id ? db.users[id] : null;
}

function findUserById(id) {
  return Object.prototype.hasOwnProperty.call(db.users, id) ? db.users[id] : null;
}

function createUser({ email, name, passHash }) {
  const user = {
    id: crypto.randomBytes(12).toString('hex'),
    email,
    name,
    passHash,
    verified: false,
    createdAt: Date.now(),
    pending: null, // { purpose, salt, hash, expiresAt, attempts, sentAt }
  };
  db.users[user.id] = user;
  emailIndex.set(email, user.id);
  persist('users');
  return user;
}

/** Call after mutating a user object returned by this module. */
function saveUsers() {
  persist('users');
}

// ---------- sessions ----------

const hashToken = (token) => crypto.createHash('sha256').update(token).digest('hex');

function createSession(userId) {
  const token = crypto.randomBytes(32).toString('hex');
  db.sessions[hashToken(token)] = { userId, createdAt: Date.now() };

  // keep only the newest sessions per user
  const mine = Object.entries(db.sessions)
    .filter(([, s]) => s.userId === userId)
    .sort((a, b) => b[1].createdAt - a[1].createdAt);
  for (const [key] of mine.slice(MAX_SESSIONS_PER_USER)) delete db.sessions[key];

  persist('sessions');
  return token;
}

function userForToken(token) {
  if (typeof token !== 'string' || !/^[0-9a-f]{64}$/.test(token)) return null;
  const s = db.sessions[hashToken(token)];
  if (!s) return null;
  const user = findUserById(s.userId);
  return user && user.verified ? user : null;
}

function revokeSessions(userId) {
  for (const [key, s] of Object.entries(db.sessions)) {
    if (s.userId === userId) delete db.sessions[key];
  }
  persist('sessions');
}

// ---------- leaderboard ----------

function setSnapshot(userId, snapshot) {
  db.leaderboard[userId] = snapshot;
  persist('leaderboard');
}

function getSnapshot(userId) {
  return db.leaderboard[userId] || null;
}

function allSnapshots() {
  return db.leaderboard;
}

// ---------- backups ----------

const backupFile = (userId) => path.join(BACKUP_DIR, `${userId}.json`);

/** Stores the raw JSON bytes; the previous copy is kept as <id>.json.prev. */
function saveBackup(userId, buffer) {
  const file = backupFile(userId);
  if (fs.existsSync(file)) fs.copyFileSync(file, `${file}.prev`);
  writeFileAtomic(file, buffer);
  return { size: buffer.length, savedAt: Date.now() };
}

function readBackup(userId) {
  try {
    return fs.readFileSync(backupFile(userId));
  } catch (e) {
    if (e.code === 'ENOENT') return null;
    throw e;
  }
}

module.exports = {
  findUserByEmail, findUserById, createUser, saveUsers,
  createSession, userForToken, revokeSessions,
  setSnapshot, getSnapshot, allSnapshots,
  saveBackup, readBackup,
};
