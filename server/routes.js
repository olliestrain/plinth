'use strict';
// API handlers. Each handler gets ctx = { req, ip, url, user, json(), raw() } and returns
// a JSON-able object, or { status, body } / { raw: Buffer, contentType } for special cases.
const store = require('./store');
const mail = require('./mail');
const cfg = require('./config');
const {
  HttpError, hashPassword, verifyPassword, fakeVerify, issueCode, checkCode,
  rateLimit, rateAllowed, validEmail, validPassword, cleanName, pickLang,
} = require('./auth');

const MIN = 60 * 1000;
const WINDOW = 15 * MIN;
const BOARD_MAX = 50;

const publicUser = (u) => ({ id: u.id, email: u.email, name: u.name, createdAt: u.createdAt });

/** Max 6 e-mails per address per window, whatever the route (protects inboxes from spam). */
const mailBudget = (email) => rateLimit(`mail:${email}`, 6, WINDOW);

async function sendCodeOrFail(kind, user, code, lang) {
  try {
    await mail.sendCode(kind, { to: user.email, name: user.name, code, lang });
  } catch (e) {
    console.error(`[mail] failed to send ${kind} code to ${user.email}: ${e.message}`);
    throw new HttpError(502, 'mail_failed', 'Could not send the e-mail right now. Try again in a moment.');
  }
}

// ---------- public ----------

function ping() {
  return { app: 'rowan', version: cfg.version, time: Date.now() };
}

async function register(ctx) {
  rateLimit(`register:ip:${ctx.ip}`, 15, WINDOW);
  const b = await ctx.json();
  const email = validEmail(b.email);
  const password = validPassword(b.password);
  const lang = pickLang(b.lang);
  const name = cleanName(b.name) || email.split('@')[0].slice(0, 40);
  rateLimit(`register:email:${email}`, 6, WINDOW);
  mailBudget(email);

  const passHash = await hashPassword(password); // async: do it before touching state

  let user = store.findUserByEmail(email);
  if (user && user.verified) {
    throw new HttpError(409, 'email_taken', 'An account with this e-mail already exists. Log in instead.');
  }
  if (user) { // unverified leftover: take over with the new password/name
    user.passHash = passHash;
    user.name = name;
  } else {
    user = store.createUser({ email, name, passHash });
  }
  const code = issueCode(user, 'verify');
  store.saveUsers();
  await sendCodeOrFail('verify', user, code, lang);
  return { ok: true };
}

async function verify(ctx) {
  rateLimit(`verify:ip:${ctx.ip}`, 40, WINDOW);
  const b = await ctx.json();
  const email = validEmail(b.email);
  rateLimit(`verify:email:${email}`, 30, WINDOW);

  const user = store.findUserByEmail(email);
  if (!user) throw new HttpError(400, 'invalid_code', 'That code is not valid. Request a new one.');
  try {
    checkCode(user, 'verify', b.code);
  } finally {
    store.saveUsers(); // persists the attempt counter even when the check throws
  }
  user.verified = true;
  store.saveUsers();
  return { token: store.createSession(user.id), user: publicUser(user) };
}

async function resend(ctx) {
  rateLimit(`resend:ip:${ctx.ip}`, 15, WINDOW);
  const b = await ctx.json();
  const email = validEmail(b.email);
  const lang = pickLang(b.lang);
  mailBudget(email);

  const user = store.findUserByEmail(email);
  if (user && !user.verified) { // silently ignore unknown / already verified addresses
    const code = issueCode(user, 'verify');
    store.saveUsers();
    await sendCodeOrFail('verify', user, code, lang);
  }
  return { ok: true };
}

async function login(ctx) {
  rateLimit(`login:ip:${ctx.ip}`, 40, WINDOW);
  const b = await ctx.json();
  const email = validEmail(b.email);
  const lang = pickLang(b.lang);
  rateLimit(`login:email:${email}`, 10, WINDOW);
  if (typeof b.password !== 'string' || !b.password || b.password.length > 200) {
    throw new HttpError(400, 'invalid_password', 'Enter your password.');
  }

  const user = store.findUserByEmail(email);
  if (!user) {
    await fakeVerify(b.password);
    throw new HttpError(401, 'invalid_credentials', 'Wrong e-mail or password.');
  }
  if (!(await verifyPassword(b.password, user.passHash))) {
    throw new HttpError(401, 'invalid_credentials', 'Wrong e-mail or password.');
  }
  if (!user.verified) {
    if (rateAllowed(`mail:${email}`, 6, WINDOW)) {
      const code = issueCode(user, 'verify');
      store.saveUsers();
      await sendCodeOrFail('verify', user, code, lang);
    }
    throw new HttpError(403, 'unverified', 'Verify your e-mail first: a new code has been sent.');
  }
  return { token: store.createSession(user.id), user: publicUser(user) };
}

async function forgot(ctx) {
  rateLimit(`forgot:ip:${ctx.ip}`, 15, WINDOW);
  const b = await ctx.json();
  const email = validEmail(b.email);
  const lang = pickLang(b.lang);
  mailBudget(email);

  const user = store.findUserByEmail(email);
  if (user) { // same answer whether or not the account exists
    const code = issueCode(user, 'reset');
    store.saveUsers();
    await sendCodeOrFail('reset', user, code, lang);
  }
  return { ok: true };
}

async function reset(ctx) {
  rateLimit(`reset:ip:${ctx.ip}`, 40, WINDOW);
  const b = await ctx.json();
  const email = validEmail(b.email);
  const password = validPassword(b.password);
  rateLimit(`reset:email:${email}`, 30, WINDOW);

  const passHash = await hashPassword(password);
  const user = store.findUserByEmail(email);
  if (!user) throw new HttpError(400, 'invalid_code', 'That code is not valid. Request a new one.');
  try {
    checkCode(user, 'reset', b.code);
  } finally {
    store.saveUsers();
  }
  user.passHash = passHash;
  user.verified = true; // receiving the reset code proves ownership of the address
  store.saveUsers();
  store.revokeSessions(user.id); // log out every old device
  return { token: store.createSession(user.id), user: publicUser(user) };
}

// ---------- authenticated ----------

function me(ctx) {
  return { user: publicUser(ctx.user) };
}

/** Whole numbers only, 0..1e9. */
function wholeNumber(v, field, fallback) {
  if (v === undefined || v === null) {
    if (fallback !== undefined) return fallback;
    throw new HttpError(400, 'invalid_field', `"${field}" is required.`);
  }
  if (typeof v !== 'number' || !Number.isFinite(v) || v < 0 || v > 1e9) {
    throw new HttpError(400, 'invalid_field', `"${field}" must be a number between 0 and 1000000000.`);
  }
  return Math.floor(v);
}

/** Monday 00:00 (server local time) of the week containing ts, as YYYY-MM-DD. */
function weekStart(ts) {
  const d = new Date(ts);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

async function sync(ctx) {
  const b = await ctx.json();
  const user = ctx.user;
  const name = cleanName(b.name) || user.name;
  const now = Date.now();
  const snapshot = {
    xp: wholeNumber(b.xp, 'xp'),
    weekXp: wholeNumber(b.weekXp, 'weekXp', 0),
    level: Math.max(1, wholeNumber(b.level, 'level', 1)),
    workouts: wholeNumber(b.workouts, 'workouts', 0),
    streakWeeks: wholeNumber(b.streakWeeks, 'streakWeeks', 0),
    volume: wholeNumber(b.volume, 'volume', 0),
    prs: wholeNumber(b.prs, 'prs', 0),
    name,
    weekStart: weekStart(now), // lets the weekly board ignore stale weekXp
    updatedAt: now,
  };
  store.setSnapshot(user.id, snapshot);
  if (user.name !== name) {
    user.name = name;
    store.saveUsers();
  }
  const board = buildBoard(user, 'all');
  return { rank: board.me.rank, total: board.rows.length, xp: snapshot.xp };
}

/** Full sorted board for a period, with ranks, always containing the caller. */
function buildBoard(user, period) {
  const thisWeek = weekStart(Date.now());
  const rows = [];
  for (const [uid, s] of Object.entries(store.allSnapshots())) {
    const xp = period === 'week' ? (s.weekStart === thisWeek ? s.weekXp : 0) : s.xp;
    rows.push({ uid, name: s.name, xp, level: s.level, streakWeeks: s.streakWeeks, at: s.updatedAt });
  }
  if (!store.getSnapshot(user.id)) { // caller never synced: they still appear, at the bottom of the zeros
    rows.push({ uid: user.id, name: user.name, xp: 0, level: 1, streakWeeks: 0, at: user.createdAt });
  }
  rows.sort((a, b) => b.xp - a.xp || a.at - b.at || (a.uid < b.uid ? -1 : 1));
  rows.forEach((r, i) => { r.rank = i + 1; r.me = r.uid === user.id; });
  return { rows, me: rows.find((r) => r.me) };
}

function leaderboard(ctx) {
  const period = ctx.url.searchParams.get('period') || 'all';
  if (period !== 'week' && period !== 'all') {
    throw new HttpError(400, 'invalid_period', 'period must be "week" or "all".');
  }
  const { rows, me } = buildBoard(ctx.user, period);
  // top 50 at most; a caller outside it takes the last slot with their real rank
  const shown = me.rank <= BOARD_MAX ? rows.slice(0, BOARD_MAX) : [...rows.slice(0, BOARD_MAX - 1), me];
  return {
    entries: shown.map((r) => ({
      rank: r.rank, name: r.name, xp: r.xp, level: r.level, streakWeeks: r.streakWeeks, me: r.me,
    })),
    updatedAt: Date.now(),
  };
}

async function putBackup(ctx) {
  const body = await ctx.raw();
  if (!body.length) throw new HttpError(400, 'empty_body', 'Send the backup JSON as the request body.');
  try {
    JSON.parse(body.toString('utf8'));
  } catch {
    throw new HttpError(400, 'invalid_json', 'The backup is not valid JSON.');
  }
  const info = store.saveBackup(ctx.user.id, body);
  return { ok: true, size: info.size, savedAt: info.savedAt };
}

function getBackup(ctx) {
  const data = store.readBackup(ctx.user.id);
  if (!data) throw new HttpError(404, 'no_backup', 'No backup stored for this account yet.');
  return { raw: data, contentType: 'application/json; charset=utf-8' };
}

// path -> method -> { auth, maxBody, handler }
const BIG = 20 * 1024 * 1024;
const SMALL = 64 * 1024;
const routes = {
  '/api/ping': { GET: { auth: false, handler: ping } },
  '/api/register': { POST: { auth: false, maxBody: SMALL, handler: register } },
  '/api/verify': { POST: { auth: false, maxBody: SMALL, handler: verify } },
  '/api/resend': { POST: { auth: false, maxBody: SMALL, handler: resend } },
  '/api/login': { POST: { auth: false, maxBody: SMALL, handler: login } },
  '/api/forgot': { POST: { auth: false, maxBody: SMALL, handler: forgot } },
  '/api/reset': { POST: { auth: false, maxBody: SMALL, handler: reset } },
  '/api/me': { GET: { auth: true, handler: me } },
  '/api/sync': { POST: { auth: true, maxBody: SMALL, handler: sync } },
  '/api/leaderboard': { GET: { auth: true, handler: leaderboard } },
  '/api/backup': {
    PUT: { auth: true, maxBody: BIG, handler: putBackup },
    GET: { auth: true, handler: getBackup },
  },
};

module.exports = { routes, SMALL };
