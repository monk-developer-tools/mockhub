import { spawn } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { OG_HEIGHT, OG_WIDTH, ogKey, ogPages } from '../site-src/og.mjs';

const website = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(website, 'assets', 'img', 'og');
const CHROME = process.env.CHROME_PATH ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PORT = 9444;

const esc = (text) => String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const logo = `data:image/svg+xml;base64,${readFileSync(join(website, 'assets', 'img', 'logo.svg')).toString('base64')}`;

const card = ({ path, eyebrow, title, description }) => `<!doctype html>
<html><head><meta charset="utf-8"><style>
* { box-sizing: border-box; margin: 0; }
body { width: ${OG_WIDTH}px; height: ${OG_HEIGHT}px; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Inter', 'Helvetica Neue', Arial, sans-serif; color: #f5f5f7;
  background: radial-gradient(900px 520px at 100% 0%, rgba(91, 124, 255, 0.38), transparent 60%), radial-gradient(700px 480px at 0% 100%, rgba(31, 191, 159, 0.26), transparent 60%), #0b0b0c; }
.card { position: absolute; inset: 0; padding: 64px 72px 56px; display: flex; flex-direction: column; }
.brand { display: flex; align-items: center; gap: 14px; font-size: 30px; font-weight: 700; letter-spacing: -0.01em; }
.brand img { width: 44px; height: 44px; }
.eyebrow { margin-top: 56px; align-self: flex-start; font-size: 22px; font-weight: 600; letter-spacing: 0.02em; color: #9fb2ff; background: rgba(91, 124, 255, 0.16); border: 1px solid rgba(91, 124, 255, 0.4); padding: 8px 18px; border-radius: 999px; }
h1 { margin-top: 26px; font-size: ${title.length > 48 ? 58 : 68}px; line-height: 1.08; letter-spacing: -0.03em; font-weight: 700; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
p { margin-top: 22px; font-size: 27px; line-height: 1.4; color: #a1a1a6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; max-width: 1000px; }
.foot { margin-top: auto; display: flex; justify-content: space-between; font-size: 21px; color: #86868b; }
.foot strong { color: #d2d2d7; font-weight: 600; }
</style></head><body><div class="card">
<div class="brand"><img src="${logo}" alt="">MockHub</div>
<div class="eyebrow">${esc(eyebrow)}</div>
<h1>${esc(title)}</h1>
<p>${esc(description)}</p>
<div class="foot"><span><strong>mockhub.devops-monk.com</strong>${esc(path === '/' ? '' : path)}</span><span>Free and open source · self-hosted</span></div>
</div></body></html>`;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function connect(profile) {
  const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' });
  for (let i = 0; i < 50; i++) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
      const page = targets.find((t) => t.type === 'page');
      if (page) return { chrome, url: page.webSocketDebuggerUrl };
    } catch {}
    await sleep(200);
  }
  chrome.kill();
  throw new Error('Chrome did not start. Set CHROME_PATH to your Chrome binary.');
}

function session(url) {
  const ws = new WebSocket(url);
  let id = 0;
  const waiting = new Map();
  ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.id && waiting.has(message.id)) {
      waiting.get(message.id)(message);
      waiting.delete(message.id);
    }
  };
  const send = (method, params = {}) => new Promise((resolve) => {
    id += 1;
    waiting.set(id, resolve);
    ws.send(JSON.stringify({ id, method, params }));
  });
  return new Promise((resolve) => (ws.onopen = () => resolve({ send, close: () => ws.close() })));
}

const profile = mkdtempSync(join(tmpdir(), 'mockhub-og-'));
const { chrome, url } = await connect(profile);
try {
  const { send, close } = await session(url);
  await send('Emulation.setDeviceMetricsOverride', { width: OG_WIDTH, height: OG_HEIGHT, deviceScaleFactor: 1, mobile: false });
  rmSync(out, { recursive: true, force: true });
  mkdirSync(out, { recursive: true });
  const pages = ogPages();
  for (const page of pages) {
    const { result } = await send('Page.getFrameTree');
    await send('Page.setDocumentContent', { frameId: result.frameTree.frame.id, html: card(page) });
    await sleep(120);
    const shot = await send('Page.captureScreenshot', { format: 'jpeg', quality: 82 });
    writeFileSync(join(out, `${ogKey(page.path)}.jpg`), Buffer.from(shot.result.data, 'base64'));
  }
  close();
  console.log(`Wrote ${pages.length} preview images to website/assets/img/og`);
} finally {
  const exited = new Promise((resolve) => chrome.once('exit', resolve));
  chrome.kill();
  await exited;
  rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}
