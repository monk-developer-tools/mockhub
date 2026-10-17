import {
  ArrowCounterClockwise,
  ClockCounterClockwise,
  FloppyDisk,
  Globe,
  HardDrives,
  MagnifyingGlass,
  PaperPlaneTilt,
  Plus,
  Trash,
  X,
} from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { HeaderPair, PlaygroundEntry, PlaygroundRequest } from '../api/types';
import { CodeEditor } from '../components/CodeEditor';
import { CopyButton } from '../components/CopyButton';
import { EmptyArt } from '../components/Illustrations';
import { useToast } from '../components/Toasts';
import {
  curlOf,
  entryLabel,
  formatBytes,
  headerValue,
  joinUrl,
  PLAYGROUND_METHODS,
  prettyBody,
  splitUrl,
  statusTone,
} from '../model/playground';
import { timeAgo } from '../model/time';
import { useApp } from '../state/AppContext';
import { SaveAsStubDialog } from './SaveAsStubDialog';

type RequestTab = 'params' | 'headers' | 'body';
type ResponseTab = 'body' | 'headers';

const BODYLESS = ['GET', 'HEAD'];

function PairRows({ label, rows, onChange }: { label: string; rows: HeaderPair[]; onChange: (rows: HeaderPair[]) => void }) {
  const set = (i: number, patch: Partial<HeaderPair>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  return (
    <div className="rows">
      {rows.map((row, i) => (
        <div className="row kv" key={i}>
          <input type="text" className="mono" aria-label={`${label} ${i + 1} name`} placeholder="name" value={row.name} onChange={(e) => set(i, { name: e.target.value })} />
          <input type="text" className="mono" aria-label={`${label} ${i + 1} value`} placeholder="value" value={row.value} onChange={(e) => set(i, { value: e.target.value })} />
          <button type="button" className="btn ghost icon small" aria-label={`Remove ${label.toLowerCase()} ${i + 1}`} onClick={() => onChange(rows.filter((_, j) => j !== i))}>
            <X size={14} />
          </button>
        </div>
      ))}
      <div>
        <button type="button" className="btn small" onClick={() => onChange([...rows, { name: '', value: '' }])}>
          <Plus size={13} /> Add {label.toLowerCase()}
        </button>
      </div>
    </div>
  );
}

export function PlaygroundPage() {
  const { orgProjects } = useApp();
  const toast = useToast();
  const [params] = useSearchParams();
  const replay = (useLocation().state as { request?: PlaygroundRequest } | null)?.request;
  const [method, setMethod] = useState(replay?.method ?? params.get('method')?.toUpperCase() ?? 'GET');
  const [url, setUrl] = useState(replay?.url ?? params.get('url') ?? (orgProjects[0] ? `${orgProjects[0].mockUrl}/` : 'https://'));
  const [headers, setHeaders] = useState<HeaderPair[]>(replay?.headers ?? [{ name: 'Accept', value: 'application/json' }]);
  const [body, setBody] = useState(replay?.body ?? '');
  const [requestTab, setRequestTab] = useState<RequestTab>('params');
  const [responseTab, setResponseTab] = useState<ResponseTab>('body');
  const [current, setCurrent] = useState<PlaygroundEntry | null>(null);
  const [history, setHistory] = useState<PlaygroundEntry[] | null>(null);
  const [filter, setFilter] = useState('');
  const [busy, setBusy] = useState(false);
  const [saving, setSaving] = useState(false);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await api.playgroundHistory());
    } catch (e) {
      setHistory([]);
      toast.fail(e);
    }
  }, [toast.fail]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  const { base, params: query } = useMemo(() => splitUrl(url), [url]);
  const setQuery = (next: HeaderPair[]) => setUrl(joinUrl(base, next));

  const send = async () => {
    setBusy(true);
    try {
      const entry = await api.sendPlayground({
        method,
        url: url.trim(),
        headers: headers.filter((h) => h.name.trim()),
        body: BODYLESS.includes(method) ? '' : body,
      });
      setCurrent(entry);
      setResponseTab('body');
      setHistory((h) => [entry, ...(h ?? []).filter((x) => x.id !== entry.id)].slice(0, 100));
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const open = (entry: PlaygroundEntry) => {
    setMethod(entry.request.method);
    setUrl(entry.request.url);
    setHeaders(entry.request.headers.length ? entry.request.headers : []);
    setBody(entry.request.body);
    setCurrent(entry);
  };

  const remove = async (entry: PlaygroundEntry) => {
    try {
      await api.deletePlaygroundEntry(entry.id);
      setHistory((h) => (h ?? []).filter((x) => x.id !== entry.id));
      if (current?.id === entry.id) setCurrent(null);
    } catch (e) {
      toast.fail(e);
    }
  };

  const clear = async () => {
    try {
      await api.clearPlaygroundHistory();
      setHistory([]);
      toast.notify('History cleared');
    } catch (e) {
      toast.fail(e);
    }
  };

  const visible = (history ?? []).filter((e) => {
    const q = filter.trim().toLowerCase();
    return !q || `${e.request.method} ${e.request.url} ${e.response?.status ?? ''}`.toLowerCase().includes(q);
  });

  const response = current?.response ?? null;
  const contentType = response ? headerValue(response.headers, 'Content-Type') : '';
  const pretty = response ? prettyBody(response.body, contentType) : null;
  const tone = statusTone(response?.status);
  const origin = window.location.origin;

  return (
    <div className="page playground-page">
      <div className="page-header">
        <div>
          <h1><PaperPlaneTilt size={24} weight="duotone" className="title-icon" /> Playground</h1>
          <p className="muted">
            Call your mocks or any public API and inspect the response. Every request is saved in your history, and you can turn any
            response into a stub.
          </p>
        </div>
      </div>

      <div className="playground">
        <aside className="panel playground-history" aria-label="History">
          <div className="panel-head">
            <h2><ClockCounterClockwise size={16} weight="duotone" /> History</h2>
            {(history ?? []).length > 0 && (
              <button className="btn ghost small" onClick={clear}>Clear</button>
            )}
          </div>
          <div className="playground-search">
            <MagnifyingGlass size={14} />
            <input type="search" aria-label="Search history" placeholder="Filter by URL, method or status" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
          <ul>
            {history === null && <li className="muted small pad">Loading…</li>}
            {history !== null && visible.length === 0 && (
              <li className="muted small pad">{history.length === 0 ? 'Requests you send appear here.' : 'Nothing matches.'}</li>
            )}
            {visible.map((e) => (
              <li key={e.id} className={current?.id === e.id ? 'active' : ''}>
                <button className="history-item" onClick={() => open(e)}>
                  <span className={`jack ${e.request.method}`}>{e.request.method}</span>
                  <span className="history-text">
                    <span className="history-url mono" title={e.request.url}>{entryLabel(e)}</span>
                    <span className="history-meta">
                      {e.response ? <span className={`status-dot ${statusTone(e.response.status)}`}>{e.response.status}</span> : <span className="status-dot server">failed</span>}
                      {e.local ? <HardDrives size={12} aria-label="Mock" /> : <Globe size={12} aria-label="External" />}
                      <span>{timeAgo(e.at)}</span>
                    </span>
                  </span>
                </button>
                <button className="btn ghost icon small history-delete" aria-label={`Delete ${e.request.method} ${e.request.url} from history`} onClick={() => remove(e)}>
                  <Trash size={13} />
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <div className="stack" style={{ minWidth: 0 }}>
          <form className="panel" onSubmit={(e) => { e.preventDefault(); if (!busy) send(); }}>
            <div className="request-bar">
              <select aria-label="Method" value={method} onChange={(e) => setMethod(e.target.value)} className={`method-select ${method}`}>
                {PLAYGROUND_METHODS.map((m) => <option key={m}>{m}</option>)}
              </select>
              <input
                type="text"
                className="mono"
                aria-label="URL"
                list="playground-targets"
                placeholder="https://api.example.com/users or /payments/42"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
              />
              <datalist id="playground-targets">
                {orgProjects.map((p) => <option key={p.id} value={`${p.mockUrl}/`}>{p.name}</option>)}
              </datalist>
              <button className="btn primary" type="submit" disabled={busy || !url.trim()}>
                <PaperPlaneTilt size={15} /> {busy ? 'Sending…' : 'Send'}
              </button>
            </div>
            <div className="tabs-line" role="tablist" aria-label="Request">
              {(['params', 'headers', 'body'] as RequestTab[]).map((t) => (
                <button type="button" key={t} role="tab" aria-selected={requestTab === t} onClick={() => setRequestTab(t)}>
                  {t === 'params' ? 'Query' : t === 'headers' ? 'Headers' : 'Body'}
                  {t === 'params' && query.length > 0 && <span className="count">{query.length}</span>}
                  {t === 'headers' && headers.length > 0 && <span className="count">{headers.length}</span>}
                </button>
              ))}
            </div>
            <div className="panel-body">
              {requestTab === 'params' && <PairRows label="Parameter" rows={query} onChange={setQuery} />}
              {requestTab === 'headers' && <PairRows label="Header" rows={headers} onChange={setHeaders} />}
              {requestTab === 'body' &&
                (BODYLESS.includes(method) ? (
                  <p className="muted small">{method} requests don't send a body. Switch to POST, PUT or PATCH to add one.</p>
                ) : (
                  <CodeEditor label="Request body" value={body} onChange={setBody} minHeight="140px" />
                ))}
            </div>
          </form>

          <div className="panel playground-response" aria-live="polite">
            <div className="panel-head">
              <h2>Response</h2>
              {response && (
                <span className="response-meta">
                  <span className={`status-pill ${tone}`}>{response.status}</span>
                  <span>{response.durationMs} ms</span>
                  <span>{formatBytes(response.size)}{response.truncated ? ' (cut off)' : ''}</span>
                  {current?.local ? <span className="tag"><HardDrives size={11} /> mock</span> : <span className="tag"><Globe size={11} /> external</span>}
                </span>
              )}
            </div>
            {!current && (
              <div className="empty slim">
                <EmptyArt kind="requests" />
                <h2>Send a request</h2>
                <p>Pick one of your projects from the URL suggestions, or paste any public API address.</p>
              </div>
            )}
            {current?.error && (
              <div className="panel-body">
                <div className="callout danger" role="alert">{current.error}</div>
              </div>
            )}
            {response && pretty && (
              <>
                <div className="tabs-line" role="tablist" aria-label="Response">
                  <button type="button" role="tab" aria-selected={responseTab === 'body'} onClick={() => setResponseTab('body')}>Body</button>
                  <button type="button" role="tab" aria-selected={responseTab === 'headers'} onClick={() => setResponseTab('headers')}>
                    Headers <span className="count">{response.headers.length}</span>
                  </button>
                  <span className="tabs-actions">
                    <CopyButton text={responseTab === 'body' ? pretty.text : response.headers.map((h) => `${h.name}: ${h.value}`).join('\n')} label={responseTab === 'body' ? 'Copy body' : 'Copy headers'} showLabel />
                    <CopyButton text={curlOf(current!.request, origin)} label="Copy as cURL" showLabel />
                    <button type="button" className="btn small" onClick={() => setSaving(true)}>
                      <FloppyDisk size={13} /> Save as stub
                    </button>
                  </span>
                </div>
                <div className="panel-body">
                  {responseTab === 'body' ? (
                    response.body ? (
                      <CodeEditor label="Response body" value={pretty.text} language={pretty.language} readOnly minHeight="220px" maxHeight="560px" />
                    ) : (
                      <p className="muted small">No body.</p>
                    )
                  ) : (
                    <table className="kv-table">
                      <tbody>
                        {response.headers.map((h, i) => (
                          <tr key={i}><td className="mono">{h.name}</td><td className="mono">{h.value}</td></tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </>
            )}
            {current && (
              <div className="panel-foot muted small">
                <button type="button" className="link-button small" onClick={() => open(current)}>
                  <ArrowCounterClockwise size={12} /> Load this request again
                </button>
                <span>Sent {timeAgo(current.at)}</span>
              </div>
            )}
          </div>
        </div>
      </div>

      {saving && current && <SaveAsStubDialog entry={current} onClose={() => setSaving(false)} />}
    </div>
  );
}
