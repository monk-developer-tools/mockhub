import type { TrafficDay, TrafficView, TrafficWindow } from '../api/types';
import type { TrafficPoint } from '../components/TrafficChart';

export type ChartWindow = TrafficWindow | '30d';

export const CHART_WINDOWS: { id: ChartWindow; label: string }[] = [
  { id: '5m', label: '5m' },
  { id: '15m', label: '15m' },
  { id: '1h', label: '1h' },
  { id: '6h', label: '6h' },
  { id: '24h', label: '24h' },
  { id: '30d', label: '30d' },
];

const AXIS: Record<TrafficWindow, [string, string, string]> = {
  '5m': ['5 min ago', '2.5 min', 'now'],
  '15m': ['15 min ago', '7.5 min', 'now'],
  '1h': ['1 hour ago', '30 min', 'now'],
  '6h': ['6 hours ago', '3 hours', 'now'],
  '24h': ['24 hours ago', '12 hours', 'now'],
};

export interface TrafficSummary {
  points: TrafficPoint[];
  axis: [string, string, string];
  total: number;
  unmatched: number;
  average: string;
  peak: string;
}

function clock(at: number, seconds: boolean) {
  return new Date(at).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', ...(seconds ? { second: '2-digit' } : {}) });
}

export function rate(n: number): string {
  if (n === 0) return '0';
  if (n < 0.1) return '<0.1';
  return n >= 10 ? String(Math.round(n)) : n.toFixed(1);
}

export function fromView(view: TrafficView): TrafficSummary {
  const seconds = view.bucketSeconds < 60;
  const points = view.points.map((p) => ({
    from: p.at,
    to: p.at + view.bucketSeconds * 1000,
    label: view.bucketSeconds === 1 ? clock(p.at, true) : `${clock(p.at, seconds)}–${clock(p.at + view.bucketSeconds * 1000, seconds)}`,
    requests: p.requests,
    unmatched: p.unmatched,
  }));
  return { points, axis: AXIS[view.window], total: view.total, unmatched: view.unmatched, average: rate(view.averageRps), peak: String(view.peakRps) };
}

export function fromDays(days: TrafficDay[]): TrafficSummary {
  const total = days.reduce((a, d) => a + d.requests, 0);
  const unmatched = days.reduce((a, d) => a + d.unmatched, 0);
  const active = days.filter((d) => d.requests > 0).length;
  const busiest = Math.max(0, ...days.map((d) => d.requests));
  const label = (date: string) => new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' });
  return {
    points: days.map((d) => {
      const from = Date.parse(`${d.date}T00:00:00Z`);
      return { label: label(d.date), requests: d.requests, unmatched: d.unmatched, from, to: from + 86_400_000 };
    }),
    axis: [days.length ? label(days[0].date) : '', '15 days', 'today'],
    total,
    unmatched,
    average: active === 0 ? '0' : String(Math.round(total / active)),
    peak: String(busiest),
  };
}
