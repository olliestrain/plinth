'use strict';
// Bilingual (en/it) e-mail content. HTML is table based with inline styles only, so it renders in Gmail.

const C = {
  bg: '#0B0B0D', card: '#1A1A1E', line: '#2A2A30', text: '#F2F2F4', muted: '#9A9AA3', lime: '#D7FF3A',
};
const FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
const MONO = "'SFMono-Regular',Menlo,Consolas,'Liberation Mono','Courier New',monospace";

// cell "heat" levels, dark to bright lime
const LEVELS = ['#26262B', '#3B4513', '#7F9628', '#D7FF3A'];

const STRINGS = {
  en: {
    verify: {
      subject: (code) => `${code} is your Rowan verification code`,
      title: 'Verify your e-mail',
      intro: 'Enter this code in the Rowan app to confirm your address and start training.',
    },
    reset: {
      subject: (code) => `${code} is your Rowan password reset code`,
      title: 'Reset your password',
      intro: 'Enter this code in the Rowan app to choose a new password.',
    },
    hello: (name) => `Hi ${name},`,
    expires: 'The code is valid for 10 minutes.',
    ignore: 'Did not ask for this? You can safely ignore this e-mail, nothing will change.',
    tagline: 'Every workout counts.',
    codeLabel: 'Your code',
  },
  it: {
    verify: {
      subject: (code) => `${code} è il tuo codice di verifica Rowan`,
      title: 'Verifica la tua e-mail',
      intro: "Inserisci questo codice nell'app Rowan per confermare il tuo indirizzo e iniziare ad allenarti.",
    },
    reset: {
      subject: (code) => `${code} è il tuo codice per reimpostare la password di Rowan`,
      title: 'Reimposta la password',
      intro: "Inserisci questo codice nell'app Rowan per scegliere una nuova password.",
    },
    hello: (name) => `Ciao ${name},`,
    expires: 'Il codice è valido per 10 minuti.',
    ignore: 'Non l’hai richiesto tu? Puoi ignorare questa e-mail: non cambierà nulla.',
    tagline: 'Ogni allenamento conta.',
    codeLabel: 'Il tuo codice',
  },
};

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/** Dot grid as a table. rows: array of strings, one digit (0-3) per cell. */
function cellGrid(rows, size, gap) {
  const cols = rows[0].length;
  const spacerCell = `<td width="${gap}" style="width:${gap}px;font-size:0;line-height:0;">&nbsp;</td>`;
  const trs = rows.map((row) => {
    const cells = [...row].map((ch) => {
      const color = LEVELS[Number(ch)];
      return `<td width="${size}" height="${size}" bgcolor="${color}" style="width:${size}px;height:${size}px;background-color:${color};border-radius:3px;font-size:0;line-height:0;">&nbsp;</td>`;
    });
    return `<tr>${cells.join(spacerCell)}</tr>`;
  });
  const spacerRow = `<tr><td colspan="${cols * 2 - 1}" height="${gap}" style="height:${gap}px;font-size:0;line-height:0;">&nbsp;</td></tr>`;
  return `<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;">${trs.join(spacerRow)}</table>`;
}

const HEADER_GRID = cellGrid(['01121023', '12232133', '01323333'], 11, 4);
const FOOTER_GRID = cellGrid(['0112233233'], 7, 3);

/**
 * kind: 'verify' | 'reset'. Returns { subject, text, html }.
 */
function renderCodeEmail(kind, { name, code, lang }) {
  const s = STRINGS[lang === 'it' ? 'it' : 'en'];
  const k = s[kind];
  const safeName = name || 'Rowan';

  const text = [
    'ROWAN',
    '',
    s.hello(safeName),
    '',
    k.title,
    k.intro,
    '',
    `${s.codeLabel}: ${code}`,
    '',
    s.expires,
    s.ignore,
    '',
    `-- ${s.tagline}`,
  ].join('\n');

  const html = `<!doctype html>
<html lang="${lang === 'it' ? 'it' : 'en'}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark">
<meta name="supported-color-schemes" content="dark">
<title>Rowan</title>
</head>
<body style="margin:0;padding:0;background-color:${C.bg};" bgcolor="${C.bg}">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:${C.bg};font-size:1px;line-height:1px;">${esc(k.title)}: ${esc(code)}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="${C.bg}" style="background-color:${C.bg};">
<tr><td align="center" style="padding:32px 16px;">
  <table role="presentation" width="520" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:520px;">
    <tr><td style="padding:0 4px 22px 4px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
        <td valign="middle" style="padding-right:16px;">${HEADER_GRID}</td>
        <td valign="middle" style="font-family:${FONT};font-size:26px;line-height:30px;font-weight:800;letter-spacing:6px;color:${C.text};">ROWAN</td>
      </tr></table>
    </td></tr>
    <tr><td bgcolor="${C.card}" style="background-color:${C.card};border:1px solid ${C.line};border-radius:22px;padding:36px 32px;font-family:${FONT};">
      <p style="margin:0 0 6px 0;font-family:${FONT};font-size:15px;line-height:22px;color:${C.muted};">${esc(s.hello(safeName))}</p>
      <h1 style="margin:0 0 14px 0;font-family:${FONT};font-size:26px;line-height:32px;font-weight:700;color:${C.text};">${esc(k.title)}</h1>
      <p style="margin:0;font-family:${FONT};font-size:15px;line-height:23px;color:${C.muted};">${esc(k.intro)}</p>
      <table role="presentation" align="center" cellpadding="0" cellspacing="0" border="0" style="margin:30px auto 26px auto;"><tr>
        <td align="center" bgcolor="${C.bg}" style="background-color:${C.bg};border:1px solid ${C.line};border-radius:16px;padding:20px 22px 20px 34px;">
          <div style="font-family:${FONT};font-size:11px;line-height:14px;letter-spacing:3px;text-transform:uppercase;color:${C.muted};padding:0 12px 10px 0;">${esc(s.codeLabel)}</div>
          <div style="font-family:${MONO};font-size:42px;line-height:46px;font-weight:700;letter-spacing:12px;color:${C.lime};">${esc(code)}</div>
        </td>
      </tr></table>
      <p style="margin:0 0 10px 0;font-family:${FONT};font-size:14px;line-height:21px;color:${C.text};">${esc(s.expires)}</p>
      <p style="margin:0;font-family:${FONT};font-size:13px;line-height:20px;color:${C.muted};">${esc(s.ignore)}</p>
    </td></tr>
    <tr><td style="padding:22px 4px 0 4px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
        <td valign="middle" style="padding-right:12px;">${FOOTER_GRID}</td>
        <td valign="middle" style="font-family:${FONT};font-size:12px;line-height:16px;color:${C.muted};">${esc(s.tagline)}</td>
      </tr></table>
    </td></tr>
  </table>
</td></tr>
</table>
</body>
</html>`;

  return { subject: k.subject(code), text, html };
}

module.exports = { renderCodeEmail };
