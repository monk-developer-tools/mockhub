import { CHECKS, STATES, ago, formatUptime, hourly, overall, parseHistory, uptime } from './status-model.js';

const DATA = location.hostname === 'mockhub.devops-monk.com' ? '/status/data' : 'https://mockhub.devops-monk.com/status/data';
const DAY = 86_400_000;

const el = (tag, attrs = {}, text = '') => {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  if (text) node.textContent = text;
  return node;
};

const hourLabel = (from) => {
  const start = new Date(from).toISOString().slice(11, 16);
  const end = new Date(from + 3_600_000).toISOString().slice(11, 16);
  return `${start}–${end} UTC`;
};

async function load(path, parse) {
  try {
    const res = await fetch(`${DATA}/${path}`, { cache: 'no-store' });
    return res.ok ? parse(res) : null;
  } catch (e) {
    return null;
  }
}

function card(check, status, rows, now) {
  const latest = status?.checks?.find((c) => c.id === check.id);
  const state = overall(status, now) === 'unknown' || !latest ? 'unknown' : latest.ok ? 'up' : 'down';
  const article = el('article', { class: 'status-card', 'data-state': state });
  const head = el('div', { class: 'status-card-head' });
  head.append(el('span', { class: 'try-dot', 'aria-hidden': 'true' }), el('h3', {}, check.name),
    el('span', { class: 'status-now' }, state === 'up' ? `Up · ${latest.ms} ms` : state === 'down' ? `Failing (HTTP ${latest.status || 'no answer'})` : 'Unknown'));
  const facts = el('p', { class: 'status-facts' }, `${check.detail} Uptime: ${formatUptime(uptime(rows, check.id, now - DAY))} in 24 hours, ${formatUptime(uptime(rows, check.id, now - 7 * DAY))} in 7 days.`);
  const bars = el('div', { class: 'status-bars', role: 'img', 'aria-label': `${check.name}, last 24 hours` });
  for (const bar of hourly(rows, check.id, now)) {
    const text = bar.state === 'none' ? `${hourLabel(bar.from)}: no checks` : `${hourLabel(bar.from)}: ${bar.checks} checks, ${bar.failed} failed`;
    bars.append(el('span', { 'data-state': bar.state, title: text, tabindex: '0', 'aria-label': text }));
  }
  const scale = el('div', { class: 'status-scale' });
  scale.append(el('span', {}, '24 hours ago'), el('span', {}, 'Now'));
  article.append(head, facts, bars, scale);
  return article;
}

async function render() {
  const now = Date.now();
  const [status, csv] = await Promise.all([load('status.json', (r) => r.json()), load('history.csv', (r) => r.text())]);
  const rows = parseHistory(csv);
  const state = overall(status, now);
  const banner = document.querySelector('[data-status-overall]');
  banner.dataset.state = state;
  banner.querySelector('[data-status-text]').textContent = STATES[state];
  banner.querySelector('[data-status-checked]').textContent = status?.checkedAt ? `Last checked ${ago(status.checkedAt, now)}` : 'No results yet';
  const list = document.querySelector('[data-status-checks]');
  list.replaceChildren(...CHECKS.map((c) => card(c, status, rows, now)));
}

render();
setInterval(render, 60_000);
