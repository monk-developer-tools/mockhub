import { ArrowClockwise, ArrowSquareOut, Sparkle, Trash, X } from '@phosphor-icons/react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { CalloutEntry } from '../../api/types';
import { HeaderTable } from '../../components/HeaderTable';
import { EmptyArt } from '../../components/Illustrations';
import { EmptyState } from '../../components/ui/EmptyState';
import { docsAt } from '../../model/navigation';
import { Loading } from '../../components/Loading';
import { MethodJack } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import { askAssistantAbout, calloutCounts, describeOutcome, outcomeOf } from '../../model/callouts';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';

function time(iso: string) {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function CalloutsTab() {
  const { project } = useProject();
  const { canEdit, aiStatus } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [entries, setEntries] = useState<CalloutEntry[] | null>(null);
  const [failedOnly, setFailedOnly] = useState(false);
  const [selected, setSelected] = useState<CalloutEntry | null>(null);
  const [auto, setAuto] = useState(true);

  const load = useCallback(async () => {
    try {
      setEntries(await api.callouts(project.id));
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, toast.fail]);

  useEffect(() => {
    load();
    if (!auto) return;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [load, auto]);

  const clear = async () => {
    await api.clearCallouts(project.id);
    setSelected(null);
    await load();
  };

  const ask = (entry: CalloutEntry) => navigate(`/projects/${project.id}/assistant`, { state: { prompt: askAssistantAbout(entry) } });

  if (!entries) return <Loading />;
  const counts = calloutCounts(entries);
  const shown = failedOnly ? entries.filter((e) => outcomeOf(e) !== 'ok') : entries;
  const aiOn = Boolean(aiStatus?.enabled) && canEdit;

  return (
    <div className="panel">
      <div className="panel-head">
        <div className="btn-row">
          <div className="segmented" role="group" aria-label="Filter">
            <button aria-pressed={!failedOnly} onClick={() => setFailedOnly(false)}>All {counts.total > 0 && <span className="muted">{counts.total}</span>}</button>
            <button aria-pressed={failedOnly} onClick={() => setFailedOnly(true)}>Failed {counts.failed > 0 && <span className="count-alert">{counts.failed}</span>}</button>
          </div>
          <label className="check">
            <input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} /> Live
          </label>
        </div>
        <div className="btn-row">
          <button className="btn ghost icon" onClick={load} aria-label="Refresh" title="Refresh">
            <ArrowClockwise size={15} />
          </button>
          {canEdit && (
            <button className="btn" onClick={clear} disabled={entries.length === 0}>
              <Trash size={15} /> Clear log
            </button>
          )}
        </div>
      </div>

      {shown.length === 0 ? (
        <EmptyState
          art={<EmptyArt kind="requests" />}
          title={failedOnly ? 'No failed callouts' : 'No callouts yet'}
          docs={docsAt('webhooks')}
          actions={
            aiOn &&
            !failedOnly && (
              <button
                className="btn"
                onClick={() => navigate(`/projects/${project.id}/assistant`, { state: { prompt: 'Add a webhook to one of my stubs: after it answers, POST the request body to https://example.com/hooks with an X-Event header.' } })}
              >
                <Sparkle size={15} /> Set one up with the assistant
              </button>
            )
          }
        >
          <p>
            Webhooks and “wait for callout” calls your stubs make show up here with what was sent, the answer and the time it
            took. Add one in a stub under <strong>Then call</strong>, or pick <strong>Wait for callout</strong> as the response.
          </p>
        </EmptyState>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="log-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Called</th>
                <th>Result</th>
                <th>Stub</th>
                <th>Mode</th>
                <th>ms</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((e) => {
                const outcome = outcomeOf(e);
                return (
                  <tr key={e.id} onClick={() => setSelected(e)} aria-selected={selected?.id === e.id}>
                    <td className="num muted">{time(e.at)}</td>
                    <td className="callout-cell">
                      <span className="route">
                        <MethodJack method={e.request?.method ?? 'POST'} />
                        <span className="path" title={e.request?.url}>{e.request?.url ?? '—'}</span>
                      </span>
                    </td>
                    <td className={`callout-result ${outcome}`}>{outcome === 'failed' ? 'failed' : describeOutcome(e)}</td>
                    <td><span className="callout-cell-text" title={e.stubName ?? undefined}>{e.stubName ?? <span className="muted">unnamed stub</span>}</span></td>
                    <td><span className="tag">{e.mode === 'sync' ? 'wait' : 'webhook'}</span></td>
                    <td className="num muted">{e.durationMs}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {selected && (
        <aside className="drawer" aria-label="Callout details">
          <div className="panel-head">
            <span className="route" style={{ minWidth: 0 }}>
              <MethodJack method={selected.request?.method ?? 'POST'} />
              <span className="path">{selected.request?.url}</span>
            </span>
            <button className="btn ghost icon" onClick={() => setSelected(null)} aria-label="Close">
              <X size={16} />
            </button>
          </div>
          <div className="drawer-body">
            {selected.error && (
              <div className="callout danger" role="alert">
                <div>{selected.error}</div>
              </div>
            )}
            <div className="btn-row">
              {selected.stubId && (
                <Link className="btn" to={`/projects/${project.id}/stubs/${selected.stubId}`}>
                  <ArrowSquareOut size={15} /> Open the stub
                </Link>
              )}
              {aiOn && outcomeOf(selected) !== 'ok' && (
                <button className="btn" onClick={() => ask(selected)}>
                  <Sparkle size={15} /> Ask the assistant to fix it
                </button>
              )}
            </div>
            <p className="muted small">
              {selected.mode === 'sync' ? 'The caller waited for this call' : 'Sent after answering'}
              {selected.trigger && <> · triggered by <span className="mono">{selected.trigger.method} {selected.trigger.url}</span></>}
              {' · '}{new Date(selected.at).toLocaleString()} · {selected.durationMs} ms
            </p>
            {selected.request && (
              <>
                <section className="stack" style={{ gap: 6 }}>
                  <h3>Sent headers</h3>
                  <HeaderTable headers={selected.request.headers} />
                </section>
                {selected.request.body && (
                  <section className="stack" style={{ gap: 6 }}>
                    <h3>Sent body</h3>
                    <pre className="block">{selected.request.body}</pre>
                  </section>
                )}
              </>
            )}
            {selected.response && (
              <>
                <section className="stack" style={{ gap: 6 }}>
                  <h3>Answer {selected.response.status}</h3>
                  <HeaderTable headers={selected.response.headers} />
                </section>
                {selected.response.body && (
                  <section className="stack" style={{ gap: 6 }}>
                    <h3>Answer body{selected.response.truncated && <span className="muted small"> (cut short)</span>}</h3>
                    <pre className="block">{selected.response.body}</pre>
                  </section>
                )}
              </>
            )}
          </div>
        </aside>
      )}
    </div>
  );
}
