'use strict';
// E-mail sending through SMTP (Gmail). Without SMTP_PASS it runs in DEV MODE: the code is logged instead.
const nodemailer = require('nodemailer');
const cfg = require('./config');
const { renderCodeEmail } = require('./emails');

const devMode = !cfg.smtp.pass || !cfg.smtp.user;

let transport = null;
function getTransport() {
  if (!transport) {
    transport = nodemailer.createTransport({
      host: cfg.smtp.host,
      port: cfg.smtp.port,
      secure: cfg.smtp.secure,
      auth: { user: cfg.smtp.user, pass: cfg.smtp.pass },
      connectionTimeout: 10000,
      greetingTimeout: 10000,
      socketTimeout: 20000,
    });
  }
  return transport;
}

/**
 * kind: 'verify' | 'reset'. Resolves when the mail is accepted by the SMTP server
 * (or immediately in DEV MODE). Rejects if sending fails.
 */
async function sendCode(kind, { to, name, code, lang }) {
  if (devMode) {
    console.log(`DEV MODE: code for ${to} is ${code} (${kind})`);
    return;
  }
  const mail = renderCodeEmail(kind, { name, code, lang });
  await getTransport().sendMail({
    from: { name: cfg.smtp.fromName, address: cfg.smtp.user },
    to,
    subject: mail.subject,
    text: mail.text,
    html: mail.html,
  });
}

module.exports = { sendCode, devMode };
