import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtempSync, readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { ago, formatUptime, hourly, overall, parseHistory, stale, uptime } from '../assets/js/status-model.js';

const NOW = Date.parse('2026-11-08T12:00:00Z');
const status = (checks, checkedAt = '2026-11-08T11:58:00Z') => ({ checkedAt, intervalSeconds: 300, checks });

test('says operational only when every check passed recently', () => {
  // Given
  const all = status([{ id: 'app', ok: true }, { id: 'website', ok: true }]);
  const some = status([{ id: 'app', ok: false }, { id: 'website', ok: true }]);
  const none = status([{ id: 'app', ok: false }, { id: 'website', ok: false }]);
  const old = status([{ id: 'app', ok: true }], '2026-11-08T11:40:00Z');

  // When
  const states = [all, some, none, old, null].map((s) => overall(s, NOW));

  // Then
  assert.deepEqual(states, ['up', 'degraded', 'down', 'unknown', 'unknown']);
  assert.equal(stale(old, NOW), true);
});

test('reads the history and works out uptime and hourly bars', () => {
  // Given
  const t = (iso) => Date.parse(iso) / 1000;
  const csv = ['time,app,app_ms,website,website_ms,tools,tools_ms',
    `${t('2026-11-08T10:05:00Z')},1,80,1,120,1,70`,
    `${t('2026-11-08T10:10:00Z')},0,0,1,110,1,60`,
    `${t('2026-11-08T11:05:00Z')},1,90,1,100,1,65`,
    'broken,line',
    `${t('2026-11-08T11:10:00Z')},1,85,1,105,0,0`].join('\n');

  // When
  const rows = parseHistory(csv);
  const bars = hourly(rows, 'app', NOW, 3);

  // Then
  assert.equal(rows.length, 4);
  assert.deepEqual(rows[0].checks.app, { ok: true, ms: 80 });
  assert.equal(uptime(rows, 'app', 0), 75);
  assert.equal(uptime(rows, 'website', 0), 100);
  assert.equal(uptime(rows, 'app', NOW), null);
  assert.deepEqual(bars.map((b) => b.state), ['degraded', 'up', 'none']);
  assert.deepEqual(parseHistory(''), []);
});

test('never rounds a failure up to 100 percent', () => {
  // Given
  const values = [100, 99.999, 75, null];

  // When
  const shown = values.map(formatUptime);

  // Then
  assert.deepEqual(shown, ['100%', '99.99%', '75.00%', 'No data']);
  assert.equal(ago('2026-11-08T11:59:30Z', NOW), 'just now');
  assert.equal(ago('2026-11-08T11:55:00Z', NOW), '5 minutes ago');
});

test('the checker script records a working and a failing site', async () => {
  // Given
  const server = createServer((req, res) => {
    if (req.url === '/__admin/health') return res.end('{"status" : "healthy"}');
    if (req.url === '/') return res.end('<title>MockHub</title>');
    res.statusCode = 503;
    res.end('down');
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const out = mkdtempSync(join(tmpdir(), 'status-'));
  const env = { ...process.env, BASE_URL: `http://127.0.0.1:${server.address().port}`, OUT_DIR: out, TIMEOUT: '3' };

  // When
  try {
    await promisify(execFile)('sh', ['deploy/vps/status/check.sh', '--once'], { env, cwd: new URL('../..', import.meta.url) });
  } finally {
    server.close();
  }

  // Then
  const written = JSON.parse(readFileSync(join(out, 'status.json'), 'utf8'));
  assert.deepEqual(written.checks.map((c) => [c.id, c.ok, c.status]), [['app', true, 200], ['website', true, 200], ['tools', false, 503]]);
  assert.equal(overall(written, Date.parse(written.checkedAt)), 'degraded');
  const rows = parseHistory(readFileSync(join(out, 'history.csv'), 'utf8'));
  assert.equal(rows.length, 1);
  assert.equal(rows[0].checks.tools.ok, false);
});
