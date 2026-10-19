import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const mode = process.argv[2] || 'flow';
assert(['flow', 'restored'].includes(mode));
const fixtureFile = new URL('../.run/account-security-fixture.json', import.meta.url);
const oldPassword = 'FixtureOld123!', newPassword = 'FixtureNew456!';
const body = value => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
const login = (client, username, password, expected = 200) => client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) }, expected);
const first = createSessionClient(base), second = createSessionClient(base), fresh = createSessionClient(base);

if (mode === 'restored') {
  const fixture = JSON.parse(await readFile(fixtureFile, 'utf8'));
  await login(fresh, fixture.username, oldPassword, 401);
  await login(fresh, fixture.username, newPassword);
  assert.equal((await fresh.json('/api/profile')).id, fixture.id);
  await fresh.json('/api/auth/logout', { method: 'POST' });
  console.log(`PASS account security restored: changed password persisted for isolated synthetic account ${fixture.username}; old password rejected, new password accepted.`);
} else {
  const username = `security_${Date.now().toString(36)}`;
  const created = await first.json('/api/auth/register', body({ username, password: oldPassword, name: '密码安全验收（虚构）', major: '软件工程' }));
  assert.equal(created.role, 'STUDENT');
  await fresh.json('/api/auth/password', body({ currentPassword: oldPassword, newPassword }), 401);
  await login(first, username, oldPassword); await login(second, username, oldPassword);
  await first.json('/api/auth/password', body({ currentPassword: 'WrongPassword', newPassword }), 400);
  await first.json('/api/auth/password', body({ currentPassword: oldPassword, newPassword: oldPassword }), 400);
  assert.equal((await second.json('/api/profile')).id, created.id);
  await first.json('/api/auth/password', body({ currentPassword: oldPassword, newPassword }));
  await first.json('/api/profile', {}, 401); await second.json('/api/profile', {}, 401);
  await login(fresh, username, oldPassword, 401); await login(fresh, username, newPassword);
  assert.equal((await fresh.json('/api/profile')).id, created.id);
  await fresh.json('/api/auth/logout', { method: 'POST' });
  await writeFile(fixtureFile, JSON.stringify({ username, id: created.id }, null, 2) + '\n');

  const locked = `${username}_lock`, probe = createSessionClient(base);
  await probe.json('/api/auth/register', body({ username: locked, password: oldPassword, name: '登录频率验收（虚构）', major: '软件工程' }));
  for (let i = 0; i < 5; i++) await login(probe, locked, 'WrongPassword', 401);
  const limited = await probe.request('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: locked, password: oldPassword }) });
  assert.equal(limited.status, 429); assert(Number(limited.headers.get('Retry-After')) > 0);
  let limitedRegistration = false;
  for (let i = 0; i < 11; i++) {
    const response = await probe.request('/api/auth/register', body({}));
    if (response.status === 429) { assert(Number(response.headers.get('Retry-After')) > 0); limitedRegistration = true; break; }
    assert.equal(response.status, 400);
  }
  assert(limitedRegistration, 'invalid registration attempts must also be limited');
  await login(fresh, username, newPassword);
  assert.equal((await fresh.json('/api/profile')).id, created.id, 'one locked account does not prevent another account logging in');
  await fresh.json('/api/auth/logout', { method: 'POST' });
  console.log(`PASS account security HTTP flow: retained synthetic account ${username}, id ${created.id}; password validation, two old sessions revoked, new password login, account lock and registration 429/Retry-After. No existing account passwords changed.`);
}
