import { mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { connect, sleep } from './cdp.mjs';

const base = (process.argv[2] ?? 'http://localhost:8080').replace(/\/$/, '');
const out = resolve(process.argv[3] ?? 'screens');
const ui = `${base}/__admin/mockhub/ui`;
const api = `${base}/__admin/mockhub/api`;

const viewports = [
  { name: 'desktop', width: 1440, height: 900, mobile: false },
  { name: 'phone', width: 390, height: 844, mobile: true },
];
const themes = ['light', 'dark'];


async function signIn() {
  if (!process.env.MOCKHUB_USER) return null;
  const response = await fetch(`${api}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: process.env.MOCKHUB_USER, password: process.env.MOCKHUB_PASSWORD }),
  });
  if (!response.ok) throw new Error(`Sign-in failed: ${response.status}`);
  return (await response.json()).token;
}

const token = await signIn();
const authHeaders = token ? { Authorization: `Bearer ${token}` } : {};

async function pages() {
  const projects = await (await fetch(`${api}/projects`, { headers: authHeaders })).json();
  const list = [
    ['dashboard', '/'],
    ['playground', '/playground'],
    ['examples', '/examples'],
    ['server', '/server'],
  ];
  const project = projects[0];
  if (!project) return list;
  const stubs = await (await fetch(`${api}/projects/${project.id}/stubs`, { headers: authHeaders })).json();
  const p = `/projects/${project.id}`;
  list.push(['project-overview', `${p}/overview`], ['project-stubs', `${p}/stubs`], ['project-requests', `${p}/requests`], ['project-callouts', `${p}/callouts`], ['project-settings', `${p}/settings`]);
  if (stubs[0]) list.push(['stub-editor', `${p}/stubs/${stubs[0].id}`]);
  return list;
}

const list = await pages();
const { send, close } = await connect();
await send('Page.enable');
mkdirSync(out, { recursive: true });
let count = 0;
for (const theme of themes) {
  for (const vp of viewports) {
    await send('Emulation.setDeviceMetricsOverride', { width: vp.width, height: vp.height, deviceScaleFactor: 1, mobile: vp.mobile });
    await send('Page.navigate', { url: `${ui}/` });
    await sleep(1500);
    const signedIn = token ? `sessionStorage.setItem('mockhub.auth', 'Bearer ${token}');` : '';
    await send('Runtime.evaluate', { expression: `${signedIn} localStorage.setItem('mockhub.theme', '${theme}'); localStorage.setItem('mockhub.sidebar', 'expanded')` });
    for (const [name, path] of list) {
      await send('Page.navigate', { url: `${ui}${path}` });
      await sleep(1800);
      try {
        const shot = await send('Page.captureScreenshot', { format: 'png' });
        writeFileSync(join(out, `${name}-${vp.name}-${theme}.png`), Buffer.from(shot.result.data, 'base64'));
        count += 1;
      } catch (e) {
        console.error(`Skipped ${name} (${vp.name}, ${theme}): ${e.message}`);
      }
    }
  }
}
close();
console.log(`Saved ${count} screenshots to ${out}`);
