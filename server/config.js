'use strict';
// Tiny .env loader (no dotenv) + central configuration.
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;

function loadEnvFile(file) {
  let text;
  try {
    text = fs.readFileSync(file, 'utf8');
  } catch {
    return; // no .env is fine
  }
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const m = line.match(/^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
    if (!m) continue;
    let value = m[2].trim();
    const quoted = value.length >= 2 && (value[0] === '"' || value[0] === "'") && value.endsWith(value[0]);
    if (quoted) value = value.slice(1, -1);
    else value = value.replace(/\s+#.*$/, ''); // strip inline comment
    // real environment variables win over the file
    if (process.env[m[1]] === undefined) process.env[m[1]] = value;
  }
}

loadEnvFile(path.join(ROOT, '.env'));

const env = process.env;
const smtpPort = Number(env.SMTP_PORT) || 465;

module.exports = {
  ROOT,
  version: require('./package.json').version,
  port: Number(env.PORT) || 8787,
  dataDir: path.resolve(ROOT, env.DATA_DIR || 'data'),
  smtp: {
    host: env.SMTP_HOST || 'smtp.gmail.com',
    port: smtpPort,
    secure: env.SMTP_SECURE ? env.SMTP_SECURE === 'true' : smtpPort === 465,
    user: (env.SMTP_USER || '').trim(),
    // Gmail shows app passwords as "abcd efgh ijkl mnop": drop the spaces.
    pass: (env.SMTP_PASS || '').replace(/\s+/g, ''),
    fromName: env.MAIL_FROM_NAME || 'Rowan',
  },
};
