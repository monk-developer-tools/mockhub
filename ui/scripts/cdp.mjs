import { spawn } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function connect(chromePath = process.env.CHROME ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome') {
  const port = 9400 + Math.floor(Math.random() * 500);
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
