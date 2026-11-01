'use strict';
// Rowan hub: plain Node http server for the Rowan app.
const http = require('http');
const cfg = require('./config');
const store = require('./store');
const mail = require('./mail');
const { HttpError, bearerToken } = require('./auth');
const { routes } = require('./routes');

const INDEX_HTML = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Rowan hub</title>
<style>
  body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0B0B0D;color:#F2F2F4;font-family:system-ui,sans-serif}
  main{text-align:center;padding:24px}
  .grid{display:inline-grid;grid-template-columns:repeat(6,16px);gap:5px;margin-bottom:20px}
  .grid i{width:16px;height:16px;border-radius:4px;background:#26262B}
  .grid i.a{background:#3B4513}.grid i.b{background:#7F9628}.grid i.c{background:#D7FF3A}
  h1{margin:0 0 6px;font-size:26px}p{margin:0;color:#9A9AA3}
  code{color:#D7FF3A;font-family:ui-monospace,Consolas,monospace}
</style></head>
<body><main>
<div class="grid"><i class="a"></i><i class="b"></i><i class="c"></i><i class="b"></i><i class="a"></i><i></i>
<i></i><i class="a"></i><i class="b"></i><i class="c"></i><i class="c"></i><i class="b"></i>
<i class="a"></i><i class="c"></i><i class="c"></i><i class="b"></i><i class="a"></i><i></i></div>
<h1>Rowan hub is up</h1>
<p>Rowan server v${cfg.version} &middot; <code>/api/ping</code></p>
</main></body></html>`;

function send(res, status, body, headers = {}) {
  let payload;
  let type = 'application/json; charset=utf-8';
  if (Buffer.isBuffer(body)) {
    payload = body;
    type = headers['Content-Type'] || type;
  } else {
    payload = Buffer.from(JSON.stringify(body));
  }
  res.writeHead(status, {
    'Content-Type': type,
    'Content-Length': payload.length,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    ...headers,
  });
  res.end(payload);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const declared = Number(req.headers['content-length']);
    if (declared > limit) return reject(new HttpError(413, 'too_large', 'Request body too large.'));
    const chunks = [];
    let size = 0;
    let done = false;
    req.on('data', (chunk) => {
      if (done) return;
      size += chunk.length;
      if (size > limit) {
        done = true;
        chunks.length = 0;
        return reject(new HttpError(413, 'too_large', 'Request body too large.'));
      }
      chunks.push(chunk);
    });
    req.on('end', () => { if (!done) { done = true; resolve(Buffer.concat(chunks)); } });
    req.on('error', (e) => { if (!done) { done = true; reject(e); } });
  });
}

async function handle(req, res) {
  const ip = req.socket.remoteAddress || 'unknown';
  const url = new URL(req.url, 'http://rowan');

  if (url.pathname === '/' && (req.method === 'GET' || req.method === 'HEAD')) {
    return send(res, 200, Buffer.from(INDEX_HTML), { 'Content-Type': 'text/html; charset=utf-8' });
  }

  const methods = Object.prototype.hasOwnProperty.call(routes, url.pathname) ? routes[url.pathname] : null;
  if (!methods) throw new HttpError(404, 'not_found', 'No such endpoint.');
  const route = methods[req.method];
  if (!route) {
    throw new HttpError(405, 'method_not_allowed', `Use ${Object.keys(methods).join(' or ')} here.`, {
      Allow: Object.keys(methods).join(', '),
    });
  }

  const ctx = { req, ip, url, user: null };
  if (route.auth) {
    ctx.user = store.userForToken(bearerToken(req));
    if (!ctx.user) throw new HttpError(401, 'unauthorized', 'Log in again.');
  }

  let rawCache = null;
  ctx.raw = async () => (rawCache = rawCache || (await readBody(req, route.maxBody)));
  ctx.json = async () => {
    const buf = await ctx.raw();
    let data;
    try {
      data = JSON.parse(buf.toString('utf8') || 'null');
    } catch {
      throw new HttpError(400, 'invalid_json', 'Body must be valid JSON.');
    }
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
      throw new HttpError(400, 'invalid_json', 'Body must be a JSON object.');
    }
    return data;
  };

  const result = await route.handler(ctx);
  if (result && result.raw) {
    return send(res, 200, result.raw, { 'Content-Type': result.contentType });
  }
  send(res, 200, result);
}

const server = http.createServer((req, res) => {
  const started = Date.now();
  res.on('finish', () => {
    const path = (req.url || '').split('?')[0];
    console.log(`${new Date().toISOString()} ${req.socket.remoteAddress} ${req.method} ${path} ${res.statusCode} ${Date.now() - started}ms`);
  });

  handle(req, res).catch((err) => {
    if (res.headersSent) { res.destroy(); return; }
    if (err instanceof HttpError) {
      const headers = { ...(err.headers || {}) };
      if (err.status === 413) headers.Connection = 'close';
      const body = { error: err.code, message: err.message };
      if (err.status === 413) { // stop the upload as soon as the reply is out
        send(res, 413, body, headers);
        res.on('finish', () => req.destroy());
        return;
      }
      return send(res, err.status, body, headers);
    }
    console.error('[error]', err);
    send(res, 500, { error: 'server_error', message: 'Something went wrong on the hub.' });
  });
});

server.requestTimeout = 120000;
server.headersTimeout = 20000;

server.listen(cfg.port, '0.0.0.0', () => {
  console.log(`Rowan hub v${cfg.version} listening on http://0.0.0.0:${cfg.port}`);
  console.log(`  data dir: ${cfg.dataDir}`);
  console.log(mail.devMode
    ? '  e-mail: DEV MODE (SMTP_PASS empty) - verification codes are printed here, nothing is sent'
    : `  e-mail: SMTP ${cfg.smtp.host}:${cfg.smtp.port} as ${cfg.smtp.user}`);
});

function shutdown(signal) {
  console.log(`${signal} received, shutting down`);
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 3000).unref();
}
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
