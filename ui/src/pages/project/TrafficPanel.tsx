import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { TrafficChart } from '../../components/TrafficChart';
import { CHART_WINDOWS, fromDays, fromView, type ChartWindow, type TrafficSummary } from '../../model/traffic';

interface Props {
  projectId: string;
  live: boolean;
  tick: number;
  onSelect: (from: number, to: number) => void;
}

export function TrafficPanel({ projectId, live, tick, onSelect }: Props) {
  const [span, setSpan] = useState<ChartWindow>('15m');
  const [summary, setSummary] = useState<TrafficSummary | null>(null);

  const load = useCallback(async () => {
    try {
      setSummary(span === '30d' ? fromDays(await api.trafficDaily(projectId)) : fromView(await api.traffic(projectId, span)));
    } catch {
      setSummary(null);
    }
  }, [projectId, span]);

  useEffect(() => {
    load();
  }, [load, tick]);

  useEffect(() => {
    if (!live) return;
    const timer = setInterval(load, span === '5m' ? 2000 : 5000);
    return () => clearInterval(timer);
  }, [load, live, span]);

  const perDay = span === '30d';
  return (
    <section className="panel traffic-panel" aria-label="Traffic">
      <div className="traffic-head">
        <dl className="traffic-figures">
          <div>
            <dt>Requests</dt>
            <dd className="num">{summary?.total.toLocaleString() ?? '—'}</dd>
          </div>
          <div>
            <dt>Unmatched</dt>
            <dd className={`num${summary && summary.unmatched > 0 ? ' warn-text' : ''}`}>{summary?.unmatched.toLocaleString() ?? '—'}</dd>
          </div>
          <div>
            <dt>{perDay ? 'Avg per active day' : 'Avg req/s'}</dt>
            <dd className="num">{summary?.average ?? '—'}</dd>
          </div>
          <div>
            <dt>{perDay ? 'Busiest day' : 'Peak req/s'}</dt>
            <dd className="num">{summary?.peak ?? '—'}</dd>
          </div>
        </dl>
        <div className="segmented" role="group" aria-label="Traffic window">
          {CHART_WINDOWS.map((w) => (
            <button key={w.id} type="button" aria-pressed={span === w.id} onClick={() => setSpan(w.id)}>{w.label}</button>
          ))}
        </div>
      </div>
      {summary && <TrafficChart points={summary.points} axis={summary.axis} label={`Requests over the last ${span}`} onSelect={onSelect} />}
      <div className="traffic-legend muted small" aria-hidden="true">
        <span><i className="legend-swatch matched" /> Matched</span>
        <span><i className="legend-swatch unmatched" /> Unmatched</span>
        <span className="traffic-hint">Drag across the chart to list those requests</span>
      </div>
    </section>
  );
}
