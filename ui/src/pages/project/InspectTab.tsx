import { ArrowsLeftRight, CheckCircle, Circle, Copy, GearSix, LinkSimple, MagicWand, MagnifyingGlass, PaperPlaneTilt, Sparkle, Trash } from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { CalloutEntry, HistoryEntry, HistoryPage, Stub } from '../../api/types';
import { CopyButton } from '../../components/CopyButton';
import { HeaderTable } from '../../components/HeaderTable';
import { MethodJack } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import { describeOutcome, outcomeOf } from '../../model/callouts';
import { historyQuery, loggedRequestOf, NO_FILTERS, replayRequest, statusTone, stubFromRequest } from '../../model/history';
import { inspectSteps, queryOf, storageKey, testPayload, testTarget } from '../../model/inspect';
import { headerValue, prettyBody, replayOf } from '../../model/playground';
import { snippet } from '../../model/snippets';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';
import { ReadyPanel } from './ReadyPanel';

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

function writeFlag(key: string) {
  try {
    localStorage.setItem(key, '1');
  } catch {
    return;
  }
}

function Body({ body, headers, truncated }: { body: string; headers: HistoryEntry['requestHeaders']; truncated: boolean }) {
  if (!body) return <p className="muted small">No body</p>;
  return (
    <>
      <pre className="block inspect-body">{prettyBody(body, headerValue(headers, 'Content-Type')).text}</pre>
      {truncated && <p className="muted small">Only the first 64 KB were kept.</p>}
    </>
  );
}

export function InspectTab() {
  const { project } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const selectedId = params.get('r');
  const [text, setText] = useState('');
  const [method, setMethod] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState<HistoryPage | null>(null);
  const [stubs, setStubs] = useState<Stub[]>([]);
  const [detail, setDetail] = useState<{ entry: HistoryEntry; callouts: CalloutEntry[] } | null>(null);
  const [sending, setSending] = useState(false);
  const [flags, setFlags] = useState(() => ({
    looked: readFlag(storageKey(project.id, 'looked')),
    answered: readFlag(storageKey(project.id, 'answered')),
    shared: readFlag(storageKey(project.id, 'shared')),
    hidden: readFlag(storageKey(project.id, 'hidden')),
  }));
  const mark = (what: 'looked' | 'answered' | 'shared' | 'hidden') => {
    writeFlag(storageKey(project.id, what));
    setFlags((f) => ({ ...f, [what]: true }));
  };

  const query = useMemo(() => historyQuery({ ...NO_FILTERS, text, method, status }, Date.now(), 0, 100), [text, method, status]);
  const load = useCallback(() => api.history(project.id, query).then(setPage).catch(() => undefined), [project.id, query]);
  useEffect(() => {
    load();
    const timer = setInterval(load, 2500);
    return () => clearInterval(timer);
  }, [load]);
  useEffect(() => {
    api.stubs(project.id).then(setStubs).catch(() => setStubs([]));
  }, [project.id]);
  useEffect(() => {
    let live = true;
    setDetail(null);
    if (selectedId) api.historyEntry(project.id, selectedId).then((d) => live && setDetail(d)).catch(() => live && setDetail(null));
    return () => {
      live = false;
    };
  }, [project.id, selectedId]);

  const select = (id: string) => {
    mark('looked');
    const next = new URLSearchParams(params);
    next.set('r', id);
    next.delete('ready');
    setParams(next, { replace: true });
  };
  const sendTest = async () => {
    setSending(true);
    try {
      const res = await fetch(testTarget(stubs, project.mockUrl), { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-MockHub-Test': 'true' }, body: testPayload(new Date()) });
      toast.notify(`Sent a test request: ${res.status}`);
      load();
    } catch (e) {
      toast.fail(e);
    } finally {
      setSending(false);
    }
  };
  const clear = async () => {
    try {
      await api.clearRequests(project.id);
      setParams(new URLSearchParams(), { replace: true });
      load();
    } catch (e) {
      toast.fail(e);
    }
  };
  const share = async (id?: string) => {
    const url = new URL(window.location.href);
    url.search = id ? `?r=${encodeURIComponent(id)}` : '';
    try {
      await navigator.clipboard.writeText(url.toString());
      toast.notify(id ? 'Link to this request copied' : 'Link to this project copied');
      mark('shared');
    } catch (e) {
      toast.fail(e);
    }
  };
  const makeStub = (entry: HistoryEntry) => {
    mark('answered');
    navigate(`/projects/${project.id}/stubs`, { state: { draft: stubFromRequest({ method: entry.method, url: entry.url, body: entry.requestBody, headers: entry.requestHeaders }, project.basePath) } });
  };

  const items = page?.items ?? [];
  const steps = inspectSteps({ requests: project.requestCount + items.length, looked: flags.looked, answered: flags.answered, shared: flags.shared });
  const entry = detail?.entry ?? null;
  const curl = entry ? snippet('curl', replayRequest({ method: entry.method, url: entry.url, headers: entry.requestHeaders, body: entry.requestBody }, project.basePath), project.mockUrl) : '';

  return (
    <div className="inspect">
      <ReadyPanel project={project} />
      <section className="panel inspect-head" aria-label="Your URL">
        <div className="code-line">
          <code className="mono">{project.mockUrl}</code>
          <CopyButton text={project.mockUrl} />
        </div>
        <div className="btn-row">
          {canEdit && <button className="btn small primary" disabled={sending} onClick={sendTest}><PaperPlaneTilt size={14} /> {sending ? 'Sending…' : 'Send test'}</button>}
          <Link className="btn small" to={`/projects/${project.id}/stubs`}><MagicWand size={14} /> Answers</Link>
          <Link className="btn small" to={`/projects/${project.id}/assistant`}><Sparkle size={14} /> AI</Link>
          <Link className="btn small" to={`/projects/${project.id}/callouts`}><ArrowsLeftRight size={14} /> Callouts</Link>
          <Link className="btn small" to={`/projects/${project.id}/settings`}><GearSix size={14} /> Settings</Link>
          <button className="btn small" onClick={() => share()}><LinkSimple size={14} /> Share</button>
        </div>
      </section>

      {!flags.hidden && steps.some((s) => !s.done) && (
        <section className="panel inspect-steps" aria-label="Get started">
          <div className="panel-head">
            <h2>Get started</h2>
            <button className="btn ghost small" onClick={() => mark('hidden')}>Hide</button>
          </div>
          <ol className="inspect-step-list">
            {steps.map((s) => (
              <li key={s.id} className={s.done ? 'done' : undefined}>
                {s.done ? <CheckCircle size={18} weight="fill" color="var(--ok)" aria-label="Done" /> : <Circle size={18} aria-label="To do" />}
                <div>
                  <strong>{s.title}</strong>
                  <p className="muted small">{s.hint}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}

      <div className="inspect-panes">
        <section className="panel inspect-list" aria-label="Requests">
          <div className="panel-head">
            <h2>Requests <span className="count">{page?.total ?? 0}</span></h2>
            <span className="btn-row">
              <span className="muted small live-dot">Listening</span>
              {canEdit && items.length > 0 && <button className="btn ghost small" onClick={clear}><Trash size={14} /> Clear</button>}
            </span>
          </div>
          <div className="inspect-filters">
            <label className="table-search">
              <MagnifyingGlass size={15} aria-hidden="true" />
              <input type="search" aria-label="Search requests" placeholder="Search path, body or headers" value={text} onChange={(e) => setText(e.target.value)} />
            </label>
            <select className="table-select" aria-label="Method" value={method} onChange={(e) => setMethod(e.target.value)}>
              <option value="">Any method</option>
              {METHODS.map((m) => <option key={m}>{m}</option>)}
            </select>
            <select className="table-select" aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">Any status</option>
              <option value="2xx">2xx</option>
              <option value="4xx">4xx</option>
              <option value="5xx">5xx</option>
            </select>
          </div>
          {items.length === 0 ? (
            <div className="inspect-empty">
              <p><strong>Waiting for requests</strong></p>
              <p className="muted small">Send anything to <code className="mono">{project.mockUrl}</code>{canEdit ? ', or select Send test.' : '.'}</p>
            </div>
          ) : (
            <ul className="inspect-rows">
              {items.map((r) => (
                <li key={r.id}>
                  <button className={r.id === selectedId ? 'selected' : undefined} onClick={() => select(r.id)} aria-current={r.id === selectedId}>
                    <MethodJack method={r.method} />
                    <span className="path mono">{r.url.startsWith(project.basePath) ? r.url.slice(project.basePath.length) || '/' : r.url}</span>
                    <span className={`status-pill ${statusTone(r.status)}`}>{r.status > 0 ? r.status : 'error'}</span>
                    <time className="muted small num" dateTime={new Date(r.at).toISOString()}>{new Date(r.at).toLocaleTimeString()}</time>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {page && page.total > items.length && (
            <p className="muted small inspect-more">Showing the newest {items.length}. <Link to={`/projects/${project.id}/requests`}>Search all {page.total}</Link></p>
          )}
        </section>

        <section className="panel inspect-detail" aria-label="Request and answer">
          <div className="panel-head">
            <h2>Request and answer</h2>
            {entry && <span className="btn-row"><CopyButton text={curl} label="Copy as cURL" showLabel /></span>}
          </div>
          {!selectedId && <p className="muted inspect-empty">Select a request to see what was sent and what was answered.</p>}
          {selectedId && !entry && <p className="muted inspect-empty">Loading…</p>}
          {entry && (
            <div className="stack">
              <div className="btn-row">
                {canEdit && <button className="btn small" onClick={() => makeStub(entry)}><MagicWand size={14} /> Make a stub from it</button>}
                <button className="btn small" onClick={() => navigate('/playground', { state: { request: replayOf(loggedRequestOf(entry)) } })}><PaperPlaneTilt size={14} /> Replay</button>
                <button className="btn small" onClick={() => share(entry.id)}><Copy size={14} /> Copy link</button>
              </div>
              <div className="inspect-columns">
                <div className="stack">
                  <h3>Request</h3>
                  <p className="route"><MethodJack method={entry.method} /> <span className="path mono">{entry.url}</span></p>
                  <p className="muted small">{new Date(entry.at).toLocaleString()} · from {entry.clientIp || 'unknown'}</p>
                  {queryOf(entry.url).length > 0 && (<><h4>Query</h4><HeaderTable headers={queryOf(entry.url)} /></>)}
                  <h4>Headers</h4>
                  <HeaderTable headers={entry.requestHeaders} />
                  <h4>Body</h4>
                  <Body body={entry.requestBody} headers={entry.requestHeaders} truncated={entry.requestBodyTruncated} />
                </div>
                <div className="stack">
                  <h3>Answer</h3>
                  <p>
                    <span className={`status-pill ${statusTone(entry.status)}`}>{entry.status > 0 ? entry.status : 'error'}</span>{' '}
                    <span className="small">{entry.matched ? (entry.stubId ? <Link to={`/projects/${project.id}/stubs/${entry.stubId}`}>{entry.stubName ?? 'unnamed stub'}</Link> : entry.stubName ?? 'matched') : 'No stub matched'}</span>
                    <span className="muted small"> · {entry.durationMs} ms</span>
                  </p>
                  <h4>Headers</h4>
                  <HeaderTable headers={entry.responseHeaders} />
                  <h4>Body</h4>
                  <Body body={entry.responseBody} headers={entry.responseHeaders} truncated={entry.responseBodyTruncated} />
                  {detail && detail.callouts.length > 0 && (
                    <>
                      <h4>Callouts it sent</h4>
                      <ul className="linked-callouts">
                        {detail.callouts.map((c) => (
                          <li key={c.id}>
                            <MethodJack method={c.request?.method ?? 'POST'} />
                            <span className="path mono">{c.request?.url ?? '—'}</span>
                            <span className={`callout-result ${outcomeOf(c)}`}>{describeOutcome(c)}</span>
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </div>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
