import { spawn } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const base = (process.argv[2] ?? 'http://localhost:8080').replace(/\/$/, '');
const out = resolve(process.argv[3] ?? 'screens');
const chromePath = process.env.CHROME ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const port = 9400 + Math.floor(Math.random() * 500);
const ui = `${base}/__admin/mockhub/ui`;
const api = `${base}/__admin/mockhub/api`;

const viewports = [
  { name: 'desktop', width: 1440, height: 900, mobile: false },
  { name: 'phone', width: 390, height: 844, mobile: true },
];
const themes = ['light', 'dark'];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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

async function connect() {
  const chrome = spawn(chromePath, ['--headless=new', `--remote-debugging-port=${port}`, '--hide-scrollbars', `--user-data-dir=${mkdtempSync(join(tmpdir(), 'mockhub-screens-'))}`, 'about:blank'], { stdio: 'ignore' });
  let targets;
  for (let i = 0; i < 50 && !targets; i++) {
    try {
      targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
    } catch {
      await sleep(200);
    }
  }
  const socket = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => socket.addEventListener('open', r));
  let id = 0;
  const pending = new Map();
  socket.addEventListener('message', (m) => {
    const data = JSON.parse(m.data);
    if (data.method === 'Page.javascriptDialogOpening') {
      id += 1;
      socket.send(JSON.stringify({ id, method: 'Page.handleJavaScriptDialog', params: { accept: true } }));
      return;
    }
    pending.get(data.id)?.(data);
    pending.delete(data.id);
  });
  const send = (method, params = {}) => new Promise((r, reject) => {
    id += 1;
    const call = id;
    const timer = setTimeout(() => {
      pending.delete(call);
      reject(new Error(`${method} timed out`));
    }, 20000);
    pending.set(call, (data) => {
      clearTimeout(timer);
      r(data);
    });
    socket.send(JSON.stringify({ id: call, method, params }));
  });
  return { send, close: () => { socket.close(); chrome.kill(); } };
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
