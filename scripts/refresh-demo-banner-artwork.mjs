import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Explicit one-time refresh of unchanged built-in artwork, never a startup migration.
const client = createSessionClient(process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178');
await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: 'platform_admin', password: 'CampusDemo123!' }) });
const backup = new URL(`../.run/banner-artwork-backup-${Date.now()}/`, import.meta.url);
let updated = 0;
for (const banner of await client.json('/api/platform/banners')) {
  const response = await client.request(banner.imageUrl);
  assert.equal(response.status, 200);
  const original = Buffer.from(await response.arrayBuffer());
  let asset;
  for (const key of ['photo', 'code', 'music']) {
    const svg = await readFile(new URL(`../campus-service/src/main/resources/demo-banners/${key}.svg`, import.meta.url));
    if (original.equals(svg)) asset = key;
  }
  if (!asset) continue; // Includes every administrator upload and previously refreshed image.
  await mkdir(backup, { recursive: true });
  await writeFile(new URL(`${banner.id}.json`, backup), JSON.stringify(banner, null, 2));
  await writeFile(new URL(`${banner.id}.svg`, backup), original);
  const current = (await client.json('/api/platform/banners')).find(row => row.id === banner.id);
  assert.deepEqual(current, banner, 'Banner changed during refresh; stop without overwriting it');
  const latest = await client.request(banner.imageUrl);
  assert(Buffer.from(await latest.arrayBuffer()).equals(original), 'Artwork changed during refresh');
  const metadata = Object.fromEntries(['title', 'targetType', 'targetId', 'sortOrder', 'startsAt', 'endsAt'].map(key => [key, banner[key]]));
  const jpeg = await readFile(new URL(`../campus-service/src/main/resources/demo-banners/${asset}-editorial.jpg`, import.meta.url));
  const form = new FormData();
  form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
  form.append('image', new Blob([jpeg], { type: 'image/jpeg' }), `${asset}-editorial.jpg`);
  const result = await client.json(`/api/platform/banners/${banner.id}`, { method: 'PUT', body: form });
  for (const key of [...Object.keys(metadata), 'enabled', 'targetPath']) assert.deepEqual(result[key], banner[key], key);
  const image = await client.request(result.imageUrl);
  assert.equal(image.headers.get('content-type'), 'image/jpeg');
  assert((await image.arrayBuffer()).byteLength > 1000);
  updated++;
  console.log(`Updated built-in ${asset} artwork on banner ${banner.id}; metadata and visibility retained.`);
}
console.log(`PASS: ${updated} unchanged built-in SVG images refreshed; all other images untouched. Backups: ${backup.pathname}`);
