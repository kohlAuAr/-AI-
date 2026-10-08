import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { deflateSync } from 'node:zlib';
import { createSessionClient } from './http-session.mjs';
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const stage = process.argv[2] || 'flow'; assert(['flow', 'restored'].includes(stage));
const admin = createSessionClient(base), student = createSessionClient(base), manager = createSessionClient(base), anonymous = createSessionClient(base);
const jsonPost = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
async function login(client, username) { await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: 'CampusDemo123!' }) }); }
await login(admin, 'platform_admin');
assert.equal((await admin.json('/api/auth/session')).user.role, 'PLATFORM_ADMIN');
await anonymous.json('/api/platform/banners', {}, 401);
await login(student, 'student'); await login(manager, 'photo_manager');
for (const client of [student, manager]) await client.json('/api/platform/banners', {}, 403);
const photo = (await admin.json('/api/clubs')).find(c => c.slug === 'photo'); assert(photo);
await admin.json(`/api/manage/clubs/${photo.id}/members`, {}, 403);
const proofPath = new URL('../.run/banner-smoke.json', import.meta.url);
if (stage === 'restored') {
  const proof = JSON.parse(await readFile(proofPath, 'utf8'));
  const banner = (await admin.json('/api/platform/banners')).find(b => b.id === proof.id);
  assert.equal(banner.title, proof.title); assert.equal(banner.sortOrder, 997); assert.equal(banner.enabled, false);
  assert.equal((await admin.request(banner.imageUrl)).status, 200);
  assert(!(await anonymous.json('/api/banners')).some(b => b.id === proof.id));
  console.log(`PASS: SAME uploaded banner ${proof.id}, edited metadata, retained image and offline status survived a campus restart; role boundaries preserved.`);
} else {
  // Small original solid-color PNG fixture, generated without image libraries; not a product illustration.
  function chunk(type, data) {
    const bytes = Buffer.concat([Buffer.from(type), data]); let crc = 0xffffffff;
    for (const byte of bytes) { crc ^= byte; for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); }
    const size = Buffer.alloc(4), tail = Buffer.alloc(4); size.writeUInt32BE(data.length); tail.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
    return Buffer.concat([size, bytes, tail]);
  }
  const header = Buffer.alloc(13); header.writeUInt32BE(120, 0); header.writeUInt32BE(50, 4); header[8] = 8; header[9] = 2;
  const pixels = Buffer.alloc(50 * (120 * 3 + 1));
  for (let y = 0; y < 50; y++) for (let x = 0; x < 120; x++) pixels.set([22, 129, 105], y * 361 + x * 3 + 1);
  const png = Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', deflateSync(pixels)), chunk('IEND', Buffer.alloc(0))]);
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(new URL('../.run/banner-test-poster.png', import.meta.url), png);
  const body = { title: '首页海报链验证（虚构测试）', targetType: 'CLUB', targetId: photo.id, sortOrder: 999, startsAt: null, endsAt: null };
  function form(metadata, image) { const data = new FormData(); data.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' })); if (image) data.append('image', new Blob([image], { type: 'image/png' }), 'poster.png'); return data; }
  await student.json('/api/platform/banners', { method: 'POST', body: form(body, png) }, 403);
  await manager.json('/api/platform/banners', { method: 'POST', body: form(body, png) }, 403);
  await admin.json('/api/platform/banners', { method: 'POST', body: form(body, Buffer.from('<svg onload="alert(1)"/>')) }, 400);
  const banner = await admin.json('/api/platform/banners', { method: 'POST', body: form(body, png) });
  assert.equal(banner.enabled, false); assert(!(await anonymous.json('/api/banners')).some(b => b.id === banner.id));
  assert.equal((await anonymous.request(`/api/banners/${banner.id}/image`)).status, 404);
  await admin.json(`/api/platform/banners/${banner.id}/visibility`, jsonPost({ enabled: true }));
  assert((await anonymous.json('/api/banners')).some(b => b.id === banner.id && b.targetPath === '/clubs/photo'));
  const image = await anonymous.request(`/api/banners/${banner.id}/image`); assert.equal(image.headers.get('content-type'), 'image/png');
  body.title = '首页海报链验证已编辑（虚构测试）'; body.sortOrder = 997;
  const edited = await admin.json(`/api/platform/banners/${banner.id}`, { method: 'PUT', body: form(body) }); assert.equal(edited.title, body.title);
  await admin.json(`/api/platform/banners/${banner.id}/visibility`, jsonPost({ enabled: false }));
  assert(!(await anonymous.json('/api/banners')).some(b => b.id === banner.id));
  assert.equal((await admin.request(`/api/platform/banners/${banner.id}/image`)).status, 200);
  await writeFile(proofPath, JSON.stringify({ id: banner.id, title: body.title }, null, 2));
  console.log(`PASS: roles, actual multipart PNG upload, forged SVG rejection, draft/confirm visibility, public image/link, metadata edit/order and reversible offline; test banner ${banner.id} retained OFFLINE.`);
}
