import { ArrowClockwise, Broadcast, DownloadSimple, MagnifyingGlass, Trash, X } from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { HistoryEntry, HistoryPage } from '../../api/types';
import { CopyButton } from '../../components/CopyButton';
import { EmptyArt } from '../../components/Illustrations';
import { Loading } from '../../components/Loading';
import { MethodJack } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import { EmptyState } from '../../components/ui/EmptyState';
import { catchAllStub, hasCatchAll } from '../../model/callouts';
import { PAGE_SIZE, activeFilters, historyQuery, isValidHeader, isValidStatus, statusTone, type HistoryFilters, type MatchFilter, type TimeRange } from '../../model/history';
import { docsAt } from '../../model/navigation';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';
import { RequestDrawer } from './RequestDrawer';
import { TrafficPanel } from './TrafficPanel';

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];
const RANGES: { id: TimeRange; label: string }[] = [
  { id: '', label: 'Any time' },
  { id: '15m', label: 'Last 15 min' },
  { id: '1h', label: 'Last hour' },
  { id: '24h', label: 'Last 24 hours' },
  { id: '7d', label: 'Last 7 days' },
];
const KEYS: Record<keyof HistoryFilters, string> = { text: 'q', method: 'method', status: 'status', matched: 'matched', stub: 'stub', header: 'header', range: 'range', from: 'from', to: 'to' };

function filtersOf(params: URLSearchParams): HistoryFilters {
  const read = (k: keyof HistoryFilters) => params.get(KEYS[k]) ?? '';
  return {
    text: read('text'),
    method: read('method'),
    status: read('status'),
    matched: read('matched') as MatchFilter,
    stub: read('stub'),
    header: read('header'),
    range: read('range') as TimeRange,
    from: read('from'),
    to: read('to'),
  };
}

function time(ms: number) {
  const d = new Date(ms);
  const today = new Date().toDateString() === d.toDateString();
  const clock = d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  return today ? clock : `${d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} ${clock}`;
}

function spanLabel(from: string, to: string) {
  const at = (ms: string) => {
    const d = new Date(Number(ms));
    const sameDay = new Date().toDateString() === d.toDateString();
    return sameDay ? d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  };
  if (from && to) return `${at(from)} – ${at(to)}`;
  return from ? `After ${at(from)}` : `Before ${at(to)}`;
}

function useDraft(value: string, commit: (v: string) => void, valid: (v: string) => boolean = () => true) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  useEffect(() => {
    if (draft === value || !valid(draft)) return;
    const timer = setTimeout(() => commit(draft), 350);
    return () => clearTimeout(timer);
  }, [draft]);
  return [draft, setDraft, valid(draft)] as const;
}

export function RequestsTab() {
  const { project, reloadProject } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => filtersOf(params), [params]);
  const [page, setPage] = useState<HistoryPage | null>(null);
  const [offset, setOffset] = useState(0);
  const [selected, setSelected] = useState<HistoryEntry | null>(null);
  const [live, setLive] = useState(true);
  const [tick, setTick] = useState(0);
  const [catching, setCatching] = useState<boolean | null>(null);

  const setFilter = useCallback((key: keyof HistoryFilters, value: string) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (value) next.set(KEYS[key], value);
      else next.delete(KEYS[key]);
      return next;
    }, { replace: true });
    setOffset(0);
  }, [setParams]);

  const [text, setText] = useDraft(filters.text, (v) => setFilter('text', v));
  const [status, setStatus, statusOk] = useDraft(filters.status, (v) => setFilter('status', v), isValidStatus);
  const [header, setHeader, headerOk] = useDraft(filters.header, (v) => setFilter('header', v), isValidHeader);

  useEffect(() => {
    api.stubs(project.id).then((stubs) => setCatching(hasCatchAll(stubs))).catch(() => setCatching(null));
  }, [project.id]);

  const load = useCallback(async () => {
    try {
      setPage(await api.history(project.id, historyQuery(filters, Date.now(), offset)));
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, filters, offset, toast.fail]);

  useEffect(() => {
    load();
  }, [load, tick]);

  useEffect(() => {
    if (!live || offset > 0) return;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [load, live, offset]);

  const refresh = () => setTick((t) => t + 1);

  const catchEverything = async () => {
    try {
      await api.createStub(project.id, catchAllStub());
      setCatching(true);
      toast.notify('Every request to this project now gets 200 and shows up here');
      reloadProject();
    } catch (e) {
      toast.fail(e);
    }
  };

  const clear = async () => {
    try {
      await api.clearRequests(project.id);
      setSelected(null);
      setOffset(0);
      refresh();
      reloadProject();
    } catch (e) {
      toast.fail(e);
    }
  };

  const exportHar = () => api.exportHistory(project.id, historyQuery(filters, Date.now(), 0, 500)).catch(toast.fail);
  const resetFilters = () => {
    setParams(new URLSearchParams(), { replace: true });
    setOffset(0);
  };

  const closeDrawer = useCallback(() => setSelected(null), []);

  const selectSpan = useCallback((from: number, to: number) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      next.set('from', String(from));
      next.set('to', String(to));
      next.delete('range');
      return next;
    }, { replace: true });
    setOffset(0);
  }, [setParams]);

  const clearSpan = () => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      next.delete('from');
      next.delete('to');
      return next;
    }, { replace: true });
    setOffset(0);
  };

  if (!page) return <Loading />;
  const filtered = activeFilters(filters) > 0;
  const { facets } = page;
  const stubOptions = facets.stubs.some((s) => s.id === filters.stub) || !filters.stub ? facets.stubs : [{ id: filters.stub, name: 'Selected stub', count: 0 }, ...facets.stubs];
  const first = page.total === 0 ? 0 : offset + 1;
  const last = offset + page.items.length;

  return (
    <div className="stack requests-tab">
      <TrafficPanel projectId={project.id} live={live} tick={tick} onSelect={selectSpan} />

      <div className="panel">
        <div className="history-filters" role="search" aria-label="Search requests">
          <label className="table-search history-search">
            <MagnifyingGlass size={15} aria-hidden="true" />
            <input type="search" placeholder="Search URL, body, headers…" aria-label="Search" value={text} onChange={(e) => setText(e.target.value)} />
          </label>
          <select className="table-select" aria-label="Method" value={filters.method} onChange={(e) => setFilter('method', e.target.value)}>
            <option value="">Any method</option>
            {METHODS.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
          <input type="text" className={`table-select history-status${statusOk ? '' : ' invalid'}`} aria-label="Status" aria-invalid={!statusOk} placeholder="Status, e.g. 4xx" value={status} onChange={(e) => setStatus(e.target.value)} />
          <select className="table-select" aria-label="Match" value={filters.matched} onChange={(e) => setFilter('matched', e.target.value)}>
            <option value="">Matched or not</option>
            <option value="matched">Matched</option>
            <option value="unmatched">Unmatched</option>
          </select>
          <select className="table-select" aria-label="Stub" value={filters.stub} onChange={(e) => setFilter('stub', e.target.value)}>
            <option value="">Any stub</option>
            {stubOptions.map((s) => <option key={s.id} value={s.id}>{s.name ?? 'unnamed stub'}</option>)}
          </select>
          <input type="text" className={`table-select history-header${headerOk ? '' : ' invalid'}`} aria-label="Header" aria-invalid={!headerOk} placeholder="Header:value" value={header} onChange={(e) => setHeader(e.target.value)} />
          {filters.from || filters.to ? (
            <span className="span-chip">
              {spanLabel(filters.from, filters.to)}
              <button type="button" className="btn ghost icon small" aria-label="Clear the selected time" onClick={clearSpan}>
                <X size={12} />
              </button>
            </span>
          ) : (
            <select className="table-select" aria-label="Time range" value={filters.range} onChange={(e) => setFilter('range', e.target.value)}>
              {RANGES.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
            </select>
          )}
          {filtered && (
            <button type="button" className="btn ghost small" onClick={resetFilters}>
              <X size={13} /> Clear filters
            </button>
          )}
        </div>

        <div className="panel-head history-bar">
          <div className="chips history-facets" aria-label="Quick filters">
            {Object.entries(facets.statuses).map(([cls, n]) => (
              <button key={cls} type="button" className={`chip status-chip ${statusTone(cls === 'error' ? 0 : Number(cls[0]) * 100)}`} aria-pressed={filters.status === cls} onClick={() => setFilter('status', filters.status === cls ? '' : cls)}>
                {cls} <span className="num">{n}</span>
              </button>
            ))}
            {facets.unmatched > 0 && (
              <button type="button" className="chip" aria-pressed={filters.matched === 'unmatched'} onClick={() => setFilter('matched', filters.matched === 'unmatched' ? '' : 'unmatched')}>
                unmatched <span className="num">{facets.unmatched}</span>
              </button>
            )}
          </div>
          <div className="btn-row">
            <label className="check" title={offset > 0 ? 'Live updates pause while you look at older pages' : undefined}>
              <input type="checkbox" checked={live} onChange={(e) => setLive(e.target.checked)} /> Live
            </label>
            <button className="btn ghost icon" onClick={refresh} aria-label="Refresh" title="Refresh">
              <ArrowClockwise size={15} />
            </button>
            <button className="btn" onClick={exportHar} disabled={page.total === 0} title="Download what matches the filters (up to 500 requests) as a HAR file">
              <DownloadSimple size={15} /> Export HAR
            </button>
            {canEdit && catching === false && (
              <button className="btn" onClick={catchEverything} title="Add a low-priority stub that answers 200 to any request no other stub matched">
                <Broadcast size={15} /> Catch all webhooks
              </button>
            )}
            {canEdit && (
              <button className="btn" onClick={clear} disabled={page.total === 0 && !filtered}>
                <Trash size={15} /> Clear history
              </button>
            )}
          </div>
        </div>

        {page.items.length === 0 ? (
          filtered ? (
            <EmptyState art={<EmptyArt kind="requests" />} title="Nothing matches these filters">
              <p>Try a wider time range or fewer filters.</p>
              <button type="button" className="btn" onClick={resetFilters}>Clear filters</button>
            </EmptyState>
          ) : (
            <EmptyState art={<EmptyArt kind="requests" />} title="No requests yet" docs={docsAt('requests')}>
              <p>
                Requests sent to <span className="mono">{project.mockUrl}</span> show up here as they arrive and stay searchable for days.
                Unmatched ones are the quickest way to find a missing or wrong stub.
              </p>
              <p className="muted small webhook-url">
                Testing a third-party webhook? Give it <span className="mono">{project.mockUrl}/webhooks</span> <CopyButton text={`${project.mockUrl}/webhooks`} label="Copy webhook URL" />
                {catching === false && canEdit ? ' and turn on Catch all webhooks so it gets 200.' : ' and watch it arrive here.'}
              </p>
            </EmptyState>
          )
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table className="log-table history-table">
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Request</th>
                    <th>Status</th>
                    <th>Matched stub</th>
                    <th className="num-col">ms</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((e) => (
                    <tr key={e.id} onClick={() => setSelected(e)} aria-selected={selected?.id === e.id}>
                      <td className="num muted nowrap">{time(e.at)}</td>
                      <td>
                        <span className="route">
                          <MethodJack method={e.method} />
                          <span className="path" title={e.url}>{e.url}</span>
                        </span>
                      </td>
                      <td><span className={`status-pill ${statusTone(e.status)}`}>{e.status > 0 ? e.status : 'error'}</span></td>
                      <td>{e.matched ? <span className="history-stub">{e.stubName ?? 'unnamed stub'}</span> : <span className="unmatched">{e.stubName ?? 'no match'}</span>}</td>
                      <td className="num muted num-col">{e.durationMs}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="history-pager">
              <span className="muted small">
                {first.toLocaleString()}–{last.toLocaleString()} of {page.total.toLocaleString()}{page.truncated && '+'} requests
                {page.truncated && ' · narrow the search to scan older history'}
              </span>
              <div className="btn-row">
                <button className="btn small" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>Newer</button>
                <button className="btn small" disabled={last >= page.total} onClick={() => setOffset(offset + PAGE_SIZE)}>Older</button>
              </div>
            </div>
          </>
        )}
      </div>

      {selected && <RequestDrawer key={selected.id} project={project} summary={selected} canEdit={canEdit} onClose={closeDrawer} />}
    </div>
  );
}
