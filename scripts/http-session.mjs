import assert from 'node:assert/strict';

// Test client only. Emulates the browser's cookie jar and fetches CSRF tokens before writes.
export function createSessionClient(base) {
  const cookies = new Map();
  async function send(path, options = {}) {
    const headers = new Headers(options.headers);
    if (cookies.size) headers.set('Cookie', [...cookies].map(([name, value]) => `${name}=${value}`).join('; '));
    const response = await fetch(base + path, { ...options, headers, redirect: 'manual', signal: AbortSignal.timeout(10000) });
    for (const value of response.headers.getSetCookie()) {
      const [name, ...parts] = value.split(';')[0].split('=');
      cookies.set(name, parts.join('='));
    }
    return response;
  }
  async function request(path, options = {}) {
    const headers = new Headers(options.headers);
    if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
      const sessionResponse = await send('/api/auth/session');
      assert.equal(sessionResponse.status, 200);
      const session = await sessionResponse.json();
      headers.set(session.csrfHeader, session.csrfToken);
    }
    return send(path, { ...options, headers });
  }
  async function json(path, options = {}, expected = 200) {
    const response = await request(path, options);
    const body = await response.json();
    assert.equal(response.status, expected, `${path}: ${JSON.stringify(body)}`);
    return body;
  }
  return { request, json };
}
