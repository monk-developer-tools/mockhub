import { RefreshCw, Trash2, Wand2, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { ServeEvent, Stub } from '../../api/types';
import { Loading } from '../../components/Loading';
import { MethodJack, StubRoute } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import { useApp } from '../../state/AppContext';
import { CodeSnippets } from '../../components/CodeSnippets';
import type { SampleRequest } from '../../model/sampleRequest';
import { useProject } from './ProjectPage';

export function stubFromRequest(event: ServeEvent, basePath: string): Stub {
  const { request } = event;
  const [rawPath, rawQuery] = request.url.split('?');
  const path = rawPath === basePath ? '/' : rawPath.startsWith(basePath + '/') ? rawPath.slice(basePath.length) : rawPath;
  const stub: Stub = { name: `${request.method} ${path}`, request: { method: request.method, urlPath: path }, response: { status: 200 } };
  const req = stub.request as Record<string, unknown>;
  if (rawQuery) {
    const params: Record<string, { equalTo: string }> = {};
    new URLSearchParams(rawQuery).forEach((v, k) => (params[k] = { equalTo: v }));
    req.queryParameters = params;
  }
  const body = request.body?.trim();
  if (body) {
    try {
      JSON.parse(body);
      req.bodyPatterns = [{ equalToJson: body, ignoreExtraElements: true }];
      (stub.response as Record<string, unknown>).headers = { 'Content-Type': 'application/json' };
      (stub.response as Record<string, unknown>).jsonBody = {};
    } catch {
      if (body.startsWith('<')) {
        const op = /<(?:\w+:)?Body[^>]*>\s*<(?:\w+:)?([\w-]+)/.exec(body)?.[1];
        if (op) {
          req.bodyPatterns = [{ matchesXPath: `//*[local-name()='Body']/*[local-name()='${op}']` }];
          stub.metadata = { mockhub: { type: 'soap', operation: op, sampleRequest: { body } } };
        }
      }
    }
  }
  return stub;
}

const SKIPPED_HEADERS = new Set(['host', 'content-length', 'connection', 'accept-encoding', 'user-agent', 'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host', 'x-real-ip']);

export function replayRequest(event: ServeEvent, basePath: string): SampleRequest {
  const { request } = event;
  const path = request.url === basePath ? '/' : request.url.startsWith(basePath) ? request.url.slice(basePath.length) : request.url;
  const headers = Object.entries(request.headers ?? {})
    .filter(([k]) => !SKIPPED_HEADERS.has(k.toLowerCase()))
    .map(([k, v]): [string, string] => [k, Array.isArray(v) ? v.join(', ') : v]);
  return { method: request.method, path: path.startsWith('/') || path.startsWith('?') ? path : `/${path}`, headers, body: request.body ?? '' };
}

function time(ms: number) {
  return new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function RequestsTab() {
  const { project, reloadProject } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [events, setEvents] = useState<ServeEvent[] | null>(null);
  const [unmatchedOnly, setUnmatchedOnly] = useState(false);
  const [selected, setSelected] = useState<ServeEvent | null>(null);
  const [nearMisses, setNearMisses] = useState<{ stubMapping: Stub; matchResult: { distance: number } }[]>([]);
  const [auto, setAuto] = useState(true);

  const load = useCallback(async () => {
    try {
      setEvents(await api.requests(project.id, unmatchedOnly));
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, unmatchedOnly, toast.fail]);

  useEffect(() => {
    load();
    if (!auto) return;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [load, auto]);

  useEffect(() => {
    setNearMisses([]);
    if (selected && !selected.wasMatched) {
      api.nearMisses(selected.request).then((r) => setNearMisses(r.nearMisses.slice(0, 3))).catch(() => setNearMisses([]));
    }
  }, [selected]);

  const clear = async () => {
    await api.clearRequests(project.id);
    setSelected(null);
    await load();
    reloadProject();
  };

  const createStub = (event: ServeEvent) => navigate(`/projects/${project.id}/stubs`, { state: { draft: stubFromRequest(event, project.basePath) } });

  if (!events) return <Loading />;
  return (
    <div className="panel">
      <div className="panel-head">
        <div className="btn-row">
          <div className="segmented" role="group" aria-label="Filter">
            <button aria-pressed={!unmatchedOnly} onClick={() => setUnmatchedOnly(false)}>All</button>
            <button aria-pressed={unmatchedOnly} onClick={() => setUnmatchedOnly(true)}>Unmatched</button>
          </div>
          <label className="check">
            <input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} /> Live
          </label>
        </div>
        <div className="btn-row">
          <button className="btn ghost icon" onClick={load} aria-label="Refresh" title="Refresh">
            <RefreshCw size={15} />
          </button>
          {canEdit && (
            <button className="btn" onClick={clear} disabled={events.length === 0}>
              <Trash2 size={15} /> Clear log
            </button>
          )}
        </div>
      </div>
      {events.length === 0 ? (
        <div className="empty">
          <h2>{unmatchedOnly ? 'No unmatched requests' : 'No requests yet'}</h2>
          <p>
            Requests sent to <span className="mono">{project.mockUrl}</span> show up here as they arrive. Unmatched ones are the
            quickest way to find a missing or wrong stub.
          </p>
        </div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="log-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Request</th>
                <th>Status</th>
                <th>Matched stub</th>
                <th>ms</th>
              </tr>
            </thead>
            <tbody>
              {events.map((e) => (
                <tr key={e.id} onClick={() => setSelected(e)} aria-selected={selected?.id === e.id}>
                  <td className="num muted">{time(e.request.loggedDate)}</td>
                  <td>
                    <span className="route">
                      <MethodJack method={e.request.method} />
                      <span className="path">{e.request.url}</span>
                    </span>
                  </td>
                  <td className="num">{e.response?.status ?? '—'}</td>
                  <td>{e.wasMatched ? String(e.stubMapping?.name ?? 'unnamed stub') : <span className="unmatched">no match</span>}</td>
                  <td className="num muted">{e.timing?.totalTime ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selected && (
        <aside className="drawer" aria-label="Request details">
          <div className="panel-head">
            <span className="route" style={{ minWidth: 0 }}>
              <MethodJack method={selected.request.method} />
              <span className="path">{selected.request.url}</span>
            </span>
            <button className="btn ghost icon" onClick={() => setSelected(null)} aria-label="Close">
              <X size={16} />
            </button>
          </div>
          <div className="drawer-body">
            {canEdit && (
              <div>
                <button className="btn" onClick={() => createStub(selected)}>
                  <Wand2 size={15} /> Create stub from this request
                </button>
              </div>
            )}
            {!selected.wasMatched && (
              <div className="stack" style={{ gap: 8 }}>
                <h3>Closest stubs</h3>
                {nearMisses.length === 0 && <p className="muted small">No stub comes close.</p>}
                {nearMisses.map((n, i) => (
                  <div key={i} className="callout warn" style={{ flexDirection: 'column', gap: 4 }}>
                    <StubRoute stub={n.stubMapping} />
                    <span className="small">
                      {String(n.stubMapping.name ?? 'unnamed')} — {Math.round((1 - n.matchResult.distance) * 100)}% match
                    </span>
                  </div>
                ))}
              </div>
            )}
            <section className="stack" style={{ gap: 6 }}>
              <h3>Request headers</h3>
              <table className="kv-table">
                <tbody>
                  {Object.entries(selected.request.headers ?? {}).map(([k, v]) => (
                    <tr key={k}>
                      <td>{k}</td>
                      <td>{Array.isArray(v) ? v.join(', ') : v}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
            {selected.request.body && (
              <section className="stack" style={{ gap: 6 }}>
                <h3>Request body</h3>
                <pre className="block">{selected.request.body}</pre>
              </section>
            )}
            {selected.response && (
              <section className="stack" style={{ gap: 6 }}>
                <h3>Response {selected.response.status}</h3>
                {selected.response.body && <pre className="block">{selected.response.body}</pre>}
              </section>
            )}
            <section className="stack" style={{ gap: 6 }}>
              <h3>Replay it</h3>
              <CodeSnippets request={replayRequest(selected, project.basePath)} baseUrl={project.mockUrl} />
            </section>
            <p className="muted small">
              From {selected.request.clientIp} at {selected.request.loggedDateString}
            </p>
          </div>
        </aside>
      )}
    </div>
  );
}
