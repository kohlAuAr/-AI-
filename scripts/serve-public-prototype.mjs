import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../public-prototype/dist/', import.meta.url));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };
http.createServer(async (request, response) => {
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return; }
  const requested = new URL(request.url, 'http://localhost').pathname;
  // Expose only the built entry and hashed JS/CSS assets. No project files or API proxy.
  const file = requested === '/' || requested === '/index.html' ? 'index.html' : /^\/assets\/[\w.-]+\.(js|css)$/.test(requested) ? requested.slice(1) : null;
  if (!file) { response.writeHead(404); response.end('Not found'); return; }
  try {
    const body = await readFile(path.join(root, file));
    response.writeHead(200, {
      'Content-Type': types[path.extname(file)], 'Content-Length': body.length,
      'Cache-Control': file === 'index.html' ? 'no-store' : 'public, max-age=3600',
      'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'none'; object-src 'none'; base-uri 'self'"
    });
    response.end(request.method === 'HEAD' ? undefined : body);
  } catch { response.writeHead(404); response.end('Not found'); }
}).listen(5180, '127.0.0.1', () => console.log('Static prototype ready at http://127.0.0.1:5180'));
