import { $, show, wireCopyButtons } from './common.js';
import { call } from './http.js';
import { binLink, rememberBin, tunnelCommand } from './bin.js';
import { forwardExample, forwardRouting, forwardTarget, localTarget, tunnelRouting } from './wrap.js';

const tool = $('[data-wrap-start]').dataset.wrapTool;
const LIST_KEY = 'mockhub-bins';
const POLL_MS = 3000;
let timer = null;

function remember(entry) {
  try {
    const list = JSON.parse(localStorage.getItem(LIST_KEY) ?? '[]');
    localStorage.setItem(LIST_KEY, JSON.stringify(rememberBin(Array.isArray(list) ? list : [], entry)));
  } catch (e) {
    return;
  }
}

function plan() {
  const value = $('[data-wrap-input]').value;
  if (tool === 'forward') {
    const target = forwardTarget(value);
    return { routing: forwardRouting(target), target };
  }
  return { routing: tunnelRouting(), local: localTarget(value) };
}

async function create(e) {
  const button = e.currentTarget;
  let chosen;
  try {
    chosen = plan();
  } catch (err) {
    show($('[data-wrap-error]'), err.message);
    return;
  }
  show($('[data-wrap-error]'), '');
  button.disabled = true;
  const made = await call('/__public/bins', { method: 'POST' });
  if (made.error || !made.json?.id) {
    button.disabled = false;
    show($('[data-wrap-error]'), made.error ?? 'The URL could not be created.');
    return;
  }
  const bin = { id: made.json.id, token: made.json.token };
  const routed = await call(`/__public/inspect/${bin.id}/routing`, {
    method: 'PUT',
    headers: { 'X-Bin-Token': bin.token, 'Content-Type': 'application/json' },
    body: JSON.stringify(chosen.routing),
  });
  button.disabled = false;
  if (routed.error) {
    show($('[data-wrap-error]'), routed.error);
    return;
  }
  remember(bin);
  render(bin, chosen);
}

function render(bin, chosen) {
  const url = `${location.origin}/__public/bins/${bin.id}`;
  $('[data-wrap-start]').hidden = true;
  $('[data-wrap-live]').hidden = false;
  $('[data-wrap-url]').textContent = url;
  $('[data-wrap-log]').href = binLink(location.origin, '/tools/webhook-bin/', bin.id, bin.token);
  if (tool === 'forward') {
    const example = forwardExample(url, chosen.target);
    $('[data-wrap-title]').textContent = 'Your watching URL';
    $('[data-wrap-explain]').textContent = `Requests to this URL go on to ${chosen.target.target}${chosen.target.pathPrefix}, and you see each one with its answer. For example ${example.from} calls ${example.to}.`;
    $('[data-wrap-curl]').textContent = `curl -i '${example.from}'`;
    $('[data-wrap-state]').textContent = 'Forwarding';
    $('[data-wrap-state]').className = 'tool-badge ok';
    return;
  }
  $('[data-wrap-title]').textContent = 'Your public URL';
  $('[data-wrap-explain]').textContent = `Once the command below is running, requests to this URL reach ${chosen.local} on your machine, and you see each one with its answer.`;
  $('[data-wrap-command-box]').hidden = false;
  $('[data-wrap-command]').textContent = tunnelCommand(location.origin, bin.id, bin.token, chosen.local);
  $('[data-wrap-curl]').textContent = `curl -i '${url}/'`;
  watchTunnel(bin);
}

async function watchTunnel(bin) {
  clearTimeout(timer);
  const result = await call(`/__public/inspect/${bin.id}/settings`, { headers: { 'X-Bin-Token': bin.token } });
  const online = Boolean(result.json?.tunnel?.online);
  $('[data-wrap-state]').textContent = online ? 'Tunnel connected' : 'Waiting for the tunnel';
  $('[data-wrap-state]').className = `tool-badge ${online ? 'ok' : 'warn'}`;
  if (!$('[data-wrap-live]').hidden) timer = setTimeout(() => watchTunnel(bin), POLL_MS);
}

$('[data-wrap-create]').addEventListener('click', create);
$('[data-wrap-input]').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    $('[data-wrap-create]').click();
  }
});
$('[data-wrap-again]').addEventListener('click', () => {
  clearTimeout(timer);
  $('[data-wrap-live]').hidden = true;
  $('[data-wrap-command-box]').hidden = true;
  $('[data-wrap-start]').hidden = false;
});
wireCopyButtons();
