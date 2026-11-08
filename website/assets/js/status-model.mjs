export const CHECKS = [
  { id: 'app', name: 'Live demo', detail: 'The MockHub server answers its health check.' },
  { id: 'website', name: 'Website and docs', detail: 'This site loads.' },
  { id: 'tools', name: 'Free tools', detail: 'The public tools on the demo server answer.' },
];

export const STATES = {
  up: 'All systems operational',
  degraded: 'Partly degraded',
  down: 'Down',
  unknown: 'Status unknown',
};

const HOUR = 3_600_000;

export function stale(status, now) {
  if (!status || !status.checkedAt) return true;
  const at = Date.parse(status.checkedAt);
  const interval = (status.intervalSeconds || 300) * 1000;
  return !Number.isFinite(at) || now - at > interval * 3;
}

export function overall(status, now) {
  if (stale(status, now) || !Array.isArray(status.checks) || status.checks.length === 0) return 'unknown';
  const up = status.checks.filter((c) => c.ok).length;
  if (up === status.checks.length) return 'up';
  return up === 0 ? 'down' : 'degraded';
}

export function parseHistory(csv) {
  const lines = String(csv ?? '').trim().split('\n');
  if (lines.length < 2) return [];
  const header = lines[0].split(',');
  return lines.slice(1).flatMap((line) => {
    const cells = line.split(',');
    if (cells.length !== header.length) return [];
    const t = Number(cells[0]) * 1000;
    if (!Number.isFinite(t)) return [];
    const checks = {};
    for (const { id } of CHECKS) {
      const ok = header.indexOf(id);
      const ms = header.indexOf(`${id}_ms`);
      if (ok < 0) continue;
      checks[id] = { ok: cells[ok] === '1', ms: ms < 0 ? null : Number(cells[ms]) };
    }
    return [{ t, checks }];
  });
}

export function uptime(rows, id, since) {
  const seen = rows.filter((r) => r.t >= since && r.checks[id]);
  if (seen.length === 0) return null;
  return (seen.filter((r) => r.checks[id].ok).length / seen.length) * 100;
}

export function formatUptime(percent) {
  if (percent === null) return 'No data';
  if (percent === 100) return '100%';
  return `${Math.min(99.99, Math.floor(percent * 100) / 100).toFixed(2)}%`;
}

export function hourly(rows, id, now, hours = 24) {
  const end = Math.floor(now / HOUR) * HOUR + HOUR;
  return Array.from({ length: hours }, (_, i) => {
    const from = end - (hours - i) * HOUR;
    const inHour = rows.filter((r) => r.t >= from && r.t < from + HOUR && r.checks[id]);
    const failed = inHour.filter((r) => !r.checks[id].ok).length;
    const state = inHour.length === 0 ? 'none' : failed === 0 ? 'up' : failed === inHour.length ? 'down' : 'degraded';
    return { from, state, checks: inHour.length, failed };
  });
}

export function ago(iso, now) {
  const seconds = Math.max(0, Math.round((now - Date.parse(iso)) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;
  const hours = Math.round(minutes / 60);
  return `${hours} hour${hours === 1 ? '' : 's'} ago`;
}
