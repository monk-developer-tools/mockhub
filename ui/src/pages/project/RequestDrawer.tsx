import { ArrowSquareOut, MagicWand, PaperPlaneTilt, X } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { CalloutEntry, HistoryEntry, Project, Stub } from '../../api/types';
import { CodeSnippets } from '../../components/CodeSnippets';
import { HeaderTable } from '../../components/HeaderTable';
import { MethodJack, StubRoute } from '../../components/StubRoute';
import { describeOutcome, outcomeOf } from '../../model/callouts';
import { loggedRequestOf, replayRequest, statusTone, stubFromRequest } from '../../model/history';
import { headerValue, prettyBody, replayOf } from '../../model/playground';

interface Props {
  project: Project;
  summary: HistoryEntry;
  canEdit: boolean;
  onClose: () => void;
}

type NearMiss = { stubMapping: Stub; matchResult: { distance: number } };

function Body({ title, body, headers, truncated }: { title: string; body: string; headers: HistoryEntry['requestHeaders']; truncated: boolean }) {
  if (!body) return null;
  const pretty = prettyBody(body, headerValue(headers, 'Content-Type'));
  return (
    <section className="stack" style={{ gap: 6 }}>
      <h3>{title}</h3>
      <pre className="block">{pretty.text}</pre>
      {truncated && <p className="muted small">Only the first 64 KB were kept.</p>}
    </section>
  );
}

export function RequestDrawer({ project, summary, canEdit, onClose }: Props) {
  const navigate = useNavigate();
  const [entry, setEntry] = useState<HistoryEntry | null>(null);
  const [callouts, setCallouts] = useState<CalloutEntry[]>([]);
  const [nearMisses, setNearMisses] = useState<NearMiss[]>([]);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    let live = true;
    setEntry(null);
    setCallouts([]);
    setNearMisses([]);
    setMissing(false);
    api.historyEntry(project.id, summary.id).then((detail) => {
      if (!live) return;
      setEntry(detail.entry);
      setCallouts(detail.callouts);
      if (!detail.entry.matched) {
        api.nearMisses(loggedRequestOf(detail.entry)).then((r) => live && setNearMisses(r.nearMisses.slice(0, 3))).catch(() => undefined);
      }
    }).catch(() => live && setMissing(true));
    return () => {
      live = false;
    };
  }, [project.id, summary.id]);

  useEffect(() => {
    const close = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [onClose]);

  const shown = entry ?? summary;
  const createStub = () => navigate(`/projects/${project.id}/stubs`, { state: { draft: stubFromRequest({ method: shown.method, url: shown.url, body: entry?.requestBody ?? '' }, project.basePath) } });
  const replay = () => entry && navigate('/playground', { state: { request: replayOf(loggedRequestOf(entry)) } });

  return (
    <aside className="drawer" aria-label="Request details">
      <div className="panel-head">
        <span className="route" style={{ minWidth: 0 }}>
          <MethodJack method={shown.method} />
          <span className="path">{shown.url}</span>
        </span>
        <button className="btn ghost icon" onClick={onClose} aria-label="Close">
          <X size={16} />
        </button>
      </div>
      <div className="drawer-body">
        <div className="request-facts">
          <span className={`status-pill ${statusTone(shown.status)}`}>{shown.status > 0 ? shown.status : 'error'}</span>
          {shown.matched ? (
            shown.stubId ? <Link to={`/projects/${project.id}/stubs/${shown.stubId}`}>{shown.stubName ?? 'unnamed stub'}</Link> : <span>{shown.stubName ?? 'matched'}</span>
          ) : (
            <span className="unmatched">{shown.stubName ?? 'no match'}</span>
          )}
          <span className="muted">{shown.durationMs} ms</span>
          <span className="muted">{new Date(shown.at).toLocaleString()}</span>
          <span className="muted">from {shown.clientIp || 'unknown'}</span>
        </div>
        {missing && <div className="callout warn">This request is no longer in the history. Its details were pruned.</div>}
        {canEdit && (
          <div className="btn-row">
            <button className="btn" onClick={createStub}>
              <MagicWand size={15} /> Create stub from this request
            </button>
            <button className="btn" onClick={replay} disabled={!entry}>
              <PaperPlaneTilt size={15} /> Replay in Playground
            </button>
          </div>
        )}
        {entry && !entry.matched && (
          <section className="stack" style={{ gap: 8 }}>
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
          </section>
        )}
        {entry && (
          <>
            <section className="stack" style={{ gap: 6 }}>
              <h3>Request headers</h3>
              <HeaderTable headers={entry.requestHeaders} />
            </section>
            <Body title="Request body" body={entry.requestBody} headers={entry.requestHeaders} truncated={entry.requestBodyTruncated} />
            <section className="stack" style={{ gap: 6 }}>
              <h3>Response headers</h3>
              <HeaderTable headers={entry.responseHeaders} />
            </section>
            <Body title={`Response body`} body={entry.responseBody} headers={entry.responseHeaders} truncated={entry.responseBodyTruncated} />
            {callouts.length > 0 && (
              <section className="stack" style={{ gap: 6 }}>
                <h3>Callouts it triggered</h3>
                <ul className="linked-callouts">
                  {callouts.map((c) => (
                    <li key={c.id}>
                      <MethodJack method={c.request?.method ?? 'POST'} />
                      <span className="path mono">{c.request?.url ?? '—'}</span>
                      <span className={`callout-result ${outcomeOf(c)}`}>{describeOutcome(c)}</span>
                      <span className="muted num">{c.durationMs} ms</span>
                    </li>
                  ))}
                </ul>
                <Link className="small" to={`/projects/${project.id}/callouts`}>
                  Open the callout log <ArrowSquareOut size={12} />
                </Link>
              </section>
            )}
            <section className="stack" style={{ gap: 6 }}>
              <h3>Replay it</h3>
              <CodeSnippets request={replayRequest({ method: entry.method, url: entry.url, headers: entry.requestHeaders, body: entry.requestBody }, project.basePath)} baseUrl={project.mockUrl} />
            </section>
          </>
        )}
      </div>
    </aside>
  );
}
