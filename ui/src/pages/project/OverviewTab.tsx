import { BracketsCurly, Check, CheckCircle, Clock, DownloadSimple, FileArrowUp, Plus, Pulse, Warning } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { ServeEvent } from '../../api/types';
import { ActivityBars } from '../../components/ActivityBars';
import { MethodJack } from '../../components/StubRoute';
import { StatTile } from '../../components/StatTile';
import { useToast } from '../../components/Toasts';
import { emptyForm, toStub } from '../../model/stubForm';
import { timeAgo } from '../../model/time';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';

const SHARED_KEY = 'mockhub.shared.';

type ConnectTarget = 'env' | 'spring' | 'compose' | 'curl';
const CONNECT_TARGETS: { id: ConnectTarget; label: string }[] = [
  { id: 'env', label: 'Env var' },
  { id: 'spring', label: 'Spring' },
  { id: 'compose', label: 'Compose' },
  { id: 'curl', label: 'cURL' },
];

export function envName(projectSlug: string) {
  return `${projectSlug.replace(/-/g, '_').toUpperCase()}_URL`;
}

export function connectSnippet(target: ConnectTarget, slug: string, mockUrl: string): string {
  const env = envName(slug);
  switch (target) {
    case 'env':
      return `${env}=${mockUrl}`;
    case 'spring':
      return `# application-test.yml\nclients:\n  ${slug}:\n    base-url: ${mockUrl}`;
    case 'compose':
      return `services:\n  my-service:\n    environment:\n      ${env}: ${mockUrl}`;
    case 'curl':
      return `curl -i ${mockUrl}/`;
  }
}

function wasShared(projectId: string) {
  try {
    return localStorage.getItem(SHARED_KEY + projectId) === '1';
  } catch {
    return false;
  }
}

function markShared(projectId: string) {
  try {
    localStorage.setItem(SHARED_KEY + projectId, '1');
  } catch {
    return;
  }
}

export function OverviewTab() {
  const { project } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [recent, setRecent] = useState<ServeEvent[]>([]);
  const [target, setTarget] = useState<ConnectTarget>('env');
  const [shared, setShared] = useState(() => wasShared(project.id));

  useEffect(() => {
    api.requests(project.id, false, 6).then(setRecent).catch(() => setRecent([]));
  }, [project.id, project.requestCount]);

  const exportAs = async (format: string) => {
    try {
      await api.exportProject(project.id, format);
      markShared(project.id);
      setShared(true);
    } catch (e) {
      toast.fail(e);
    }
  };

  const newStub = (kind: 'rest' | 'soap') =>
    navigate(`/projects/${project.id}/stubs`, { state: { draft: toStub(emptyForm(kind)) } });

  const steps = [
    { title: 'Create the project', done: true },
    { title: 'Add a stub', done: project.stubCount > 0, action: canEdit ? () => newStub('rest') : undefined, actionLabel: 'Add stub' },
    { title: 'Call your mock', done: project.requestCount > 0, href: 'stubs', actionLabel: 'Try it' },
    { title: 'Share with your team', done: shared, action: () => exportAs('postman'), actionLabel: 'Postman' },
  ];
  const remaining = steps.filter((s) => !s.done).length;
  const restCount = project.stubCount - project.soapStubCount;
  const snippet = connectSnippet(target, project.slug, project.mockUrl);

  return (
    <div>
      <div className="stats">
        <StatTile
          label="Stubs"
          value={project.stubCount}
          detail={project.stubCount === 0 ? 'none yet' : `${restCount} REST, ${project.soapStubCount} SOAP`}
          icon={<BracketsCurly size={18} weight="duotone" />}
        />
        <StatTile label="Requests" value={project.requestCount} detail="in the request log" icon={<Pulse size={18} weight="duotone" />} />
        <StatTile
          label="Unmatched"
          value={project.unmatchedCount}
          tone={project.unmatchedCount > 0 ? 'warn' : 'ok'}
          icon={project.unmatchedCount > 0 ? <Warning size={18} weight="duotone" /> : <CheckCircle size={18} weight="duotone" />}
          detail={
            project.unmatchedCount > 0 ? <Link to="requests">Find the missing stubs</Link> : 'every request found a stub'
          }
        />
        <StatTile label="Last request" value={timeAgo(project.lastRequestAt)} detail={project.lastRequestAt ? new Date(project.lastRequestAt).toLocaleTimeString() : 'waiting for traffic'} icon={<Clock size={18} weight="duotone" />} />
      </div>

      <div className="overview">
        <div className="stack">
          <div className="panel">
            <div className="panel-head">
              <h2>Connect your app</h2>
              <div className="segmented" role="group" aria-label="Snippet format">
                {CONNECT_TARGETS.map((t) => (
                  <button key={t.id} aria-pressed={target === t.id} onClick={() => setTarget(t.id)}>
                    {t.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="panel-body stack" style={{ gap: 10 }}>
              <p className="muted small">Replace the real dependency's base URL with this project's mock URL in your test environment.</p>
              <div style={{ position: 'relative' }}>
                <pre className="block" style={{ paddingRight: 44 }}>{snippet}</pre>
                <span style={{ position: 'absolute', top: 6, right: 6 }}>
                  <CopyInline text={snippet} />
                </span>
              </div>
            </div>
          </div>

          <div className="panel">
            <div className="panel-head">
              <h2>Traffic, last hour</h2>
              <Link to="requests" className="small">Open request log</Link>
            </div>
            <div className="panel-body stack" style={{ gap: 14 }}>
              <ActivityBars counts={project.activity ?? []} label={`${project.name} requests`} />
              {recent.length === 0 ? (
                <p className="muted small">No requests yet. Call {project.mockUrl} and they show up here.</p>
              ) : (
                <ul className="mini-log">
                  {recent.map((e) => (
                    <li key={e.id}>
                      <MethodJack method={e.request.method} />
                      <span className="path">{e.request.url.startsWith(project.basePath) ? e.request.url.slice(project.basePath.length) || '/' : e.request.url}</span>
                      {e.wasMatched ? (
                        <span className={`status-plug s${String(e.response?.status ?? 200)[0]}`}>{e.response?.status}</span>
                      ) : (
                        <span className="unmatched">no match</span>
                      )}
                      <span className="muted num small">{timeAgo(new Date(e.request.loggedDate).toISOString())}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>

        <div className="stack">
          {remaining > 0 && (
            <div className="panel">
              <div className="panel-head">
                <h2>Get started</h2>
                <span className="muted small">{steps.length - remaining} of {steps.length} done</span>
              </div>
              <div className="panel-body">
                <ol className="checklist">
                  {steps.map((s) => (
                    <li key={s.title} className={s.done ? 'done' : ''}>
                      <span className="tick" aria-hidden="true"><Check size={12} /></span>
                      <span className="step-title">
                        {s.title}
                        <span className="sr-only">{s.done ? ' (done)' : ' (to do)'}</span>
                      </span>
                      {!s.done && s.action && <button className="btn small" onClick={s.action}>{s.actionLabel}</button>}
                      {!s.done && s.href && <Link className="btn small" to={s.href}>{s.actionLabel}</Link>}
                    </li>
                  ))}
                </ol>
              </div>
            </div>
          )}

          <div className="panel">
            <div className="panel-head">
              <h2>Quick actions</h2>
            </div>
            <div className="panel-body rows">
              {canEdit && (
                <div className="btn-row">
                  <button className="btn primary" onClick={() => newStub('rest')}><Plus size={15} /> REST stub</button>
                  <button className="btn" onClick={() => newStub('soap')}><Plus size={15} /> SOAP stub</button>
                  <Link className="btn" to="import-export"><FileArrowUp size={15} /> Import</Link>
                </div>
              )}
              <div className="btn-row">
                <button className="btn" onClick={() => exportAs('postman')}><DownloadSimple size={15} /> Postman</button>
                <button className="btn" onClick={() => exportAs('bruno')}><DownloadSimple size={15} /> Bruno</button>
                <button className="btn" onClick={() => exportAs('wiremock')}><DownloadSimple size={15} /> WireMock JSON</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function CopyInline({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      className="btn small"
      onClick={async () => {
        await navigator.clipboard.writeText(text).catch(() => undefined);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      }}
    >
      {copied ? 'Copied' : 'Copy'}
    </button>
  );
}
