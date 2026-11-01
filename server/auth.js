'use strict';
// Passwords (scrypt), verification codes, rate limiting and input validation helpers.
const crypto = require('crypto');
const { promisify } = require('util');

const scrypt = promisify(crypto.scrypt);

class HttpError extends Error {
  constructor(status, code, message, headers) {
    super(message || code);
    this.status = status;
    this.code = code;
    this.headers = headers;
  }
}

// ---------- passwords ----------

const SCRYPT = { N: 16384, r: 8, p: 1 };
const KEYLEN = 64;

async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const key = await scrypt(password, salt, KEYLEN, SCRYPT);
  return `scrypt$${SCRYPT.N}$${SCRYPT.r}$${SCRYPT.p}$${salt.toString('hex')}$${key.toString('hex')}`;
}

async function verifyPassword(password, stored) {
  const parts = String(stored).split('$');
  if (parts.length !== 6 || parts[0] !== 'scrypt') return false;
  const [N, r, p] = parts.slice(1, 4).map(Number);
  const salt = Buffer.from(parts[4], 'hex');
  const expected = Buffer.from(parts[5], 'hex');
  const key = await scrypt(password, salt, expected.length, { N, r, p });
  return crypto.timingSafeEqual(key, expected);
}

let dummyHash = null;
/** Burn the same CPU as a real check so unknown e-mails are not distinguishable by timing. */
async function fakeVerify(password) {
  dummyHash = dummyHash || (await hashPassword('rowan-dummy-password'));
  await verifyPassword(password, dummyHash);
}

// ---------- 6-digit codes ----------

const CODE_TTL_MS = 10 * 60 * 1000;
const MAX_CODE_ATTEMPTS = 5;

const hashCode = (code, salt) => crypto.createHash('sha256').update(`${salt}:${code}`).digest();

/** Creates a fresh pending code on the user (caller must save) and returns the plain code. */
function issueCode(user, purpose) {
  const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
  const salt = crypto.randomBytes(8).toString('hex');
  user.pending = {
    purpose,
    salt,
    hash: hashCode(code, salt).toString('hex'),
    expiresAt: Date.now() + CODE_TTL_MS,
    attempts: 0,
    sentAt: Date.now(),
  };
  return code;
}

/** Throws unless `code` is the live code of this purpose. Counts wrong attempts. Caller saves. */
function checkCode(user, purpose, code) {
  const p = user && user.pending;
  if (!p || p.purpose !== purpose || typeof code !== 'string' || !/^\d{6}$/.test(code)) {
    throw new HttpError(400, 'invalid_code', 'That code is not valid. Request a new one.');
  }
  if (Date.now() > p.expiresAt) {
    user.pending = null;
    throw new HttpError(400, 'code_expired', 'This code has expired. Request a new one.');
  }
  if (p.attempts >= MAX_CODE_ATTEMPTS) {
    throw new HttpError(429, 'too_many_attempts', 'Too many wrong attempts. Request a new code.');
  }
  const ok = crypto.timingSafeEqual(hashCode(code, p.salt), Buffer.from(p.hash, 'hex'));
  if (!ok) {
    p.attempts += 1;
    if (p.attempts >= MAX_CODE_ATTEMPTS) {
      throw new HttpError(429, 'too_many_attempts', 'Too many wrong attempts. Request a new code.');
    }
    throw new HttpError(400, 'invalid_code', `Wrong code. ${MAX_CODE_ATTEMPTS - p.attempts} attempt(s) left.`);
  }
  user.pending = null;
}

// ---------- rate limiting (sliding window, in memory) ----------

const buckets = new Map(); // key -> [timestamps]

/** Throws 429 if `key` already had `max` hits within `windowMs`; otherwise records a hit. */
function rateLimit(key, max, windowMs) {
  const now = Date.now();
  const hits = (buckets.get(key) || []).filter((t) => now - t < windowMs);
  if (hits.length >= max) {
    const retryAfter = Math.max(1, Math.ceil((hits[0] + windowMs - now) / 1000));
    buckets.set(key, hits);
    throw new HttpError(429, 'rate_limited', 'Too many requests. Try again later.', { 'Retry-After': String(retryAfter) });
  }
  hits.push(now);
  buckets.set(key, hits);
}

/** Same as rateLimit but returns false instead of throwing. */
function rateAllowed(key, max, windowMs) {
  try { rateLimit(key, max, windowMs); return true; } catch (e) { if (e instanceof HttpError) return false; throw e; }
}

setInterval(() => {
  const now = Date.now();
  for (const [k, hits] of buckets) {
    if (!hits.length || now - hits[hits.length - 1] > 60 * 60 * 1000) buckets.delete(k);
  }
}, 10 * 60 * 1000).unref();

// ---------- validation ----------

function validEmail(v) {
  const email = typeof v === 'string' ? v.trim().toLowerCase() : '';
  if (email.length > 254 || !/^[^\s@<>()",;:\\]+@[^\s@<>()",;:\\]+\.[^\s@<>()",;:\\]{2,}$/.test(email)) {
    throw new HttpError(400, 'invalid_email', 'Enter a valid e-mail address.');
  }
  return email;
}

function validPassword(v) {
  if (typeof v !== 'string' || v.length < 8 || v.length > 200) {
    throw new HttpError(400, 'invalid_password', 'Password must be 8 to 200 characters.');
  }
  return v;
}

/** Returns a cleaned display name, or '' when absent/blank. */
function cleanName(v) {
  if (typeof v !== 'string') return '';
  return v.replace(/[\u0000-\u001f\u007f<>]/g, '').replace(/\s+/g, ' ').trim().slice(0, 40);
}

const pickLang = (v) => (v === 'it' ? 'it' : 'en');

function bearerToken(req) {
  const m = /^Bearer\s+([0-9a-fA-F]{64})\s*$/.exec(req.headers.authorization || '');
  return m ? m[1].toLowerCase() : null;
}

module.exports = {
  HttpError,
  hashPassword, verifyPassword, fakeVerify,
  issueCode, checkCode, CODE_TTL_MS, MAX_CODE_ATTEMPTS,
  rateLimit, rateAllowed,
  validEmail, validPassword, cleanName, pickLang, bearerToken,
};
