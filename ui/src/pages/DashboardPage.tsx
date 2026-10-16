import { BookOpenText, BracketsCurly, CheckCircle, Plus, Pulse, SquaresFour, Warning } from '@phosphor-icons/react';
import { EmptyArt } from '../components/Illustrations';
import { ActivityBars } from '../components/ActivityBars';
import { StatTile } from '../components/StatTile';
import { timeAgo } from '../model/time';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CopyButton } from '../components/CopyButton';
import { useApp } from '../state/AppContext';
import { ExampleGrid } from './ExamplesPage';
import { ProjectDialog } from './ProjectDialog';

export function DashboardPage() {
  const { orgProjects: projects, currentOrg, canEdit, info, refreshProjects } = useApp();

  useEffect(() => {
    const timer = setInterval(() => refreshProjects().catch(() => undefined), 10000);
    return () => clearInterval(timer);
  }, [refreshProjects]);
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();
  const totals = projects.reduce(
    (t, p) => ({
      stubs: t.stubs + p.stubCount,
      soap: t.soap + p.soapStubCount,
      requests: t.requests + p.requestCount,
      unmatched: t.unmatched + (p.unmatchedCount ?? 0),
    }),
    { stubs: 0, soap: 0, requests: 0, unmatched: 0 },
  );

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{currentOrg ? `${currentOrg.name} projects` : 'Projects'}</h1>
          <p className="muted">
            Each project serves its mocks under its own base path on <span className="mono">{info?.baseUrl}</span>. Point your service
            or test at that URL instead of the real dependency.
          </p>
        </div>
        {canEdit && (
          <button className="btn primary" onClick={() => setCreating(true)}>
            <Plus size={16} /> New project
          </button>
        )}
      </div>

      {projects.length === 0 ? (
        <div className="panel">
          <div className="empty">
            <EmptyArt kind="projects" />
            <h2>Create your first mock project</h2>
            <p>
              Start empty, import an OpenAPI spec, WSDL or WireMock mappings, or install an example below to see how REST, SOAP,
              templating and stateful mocks look.
            </p>
            {canEdit && (
              <button className="btn primary" onClick={() => setCreating(true)}>
                <Plus size={16} /> New project
              </button>
            )}
          </div>
          <ExampleGrid compact />
        </div>
      ) : (
        <div className="stack">
          <div className="stats">
            <StatTile label="Projects" value={projects.length} detail={`in ${currentOrg?.name ?? 'this organisation'}`} icon={<SquaresFour size={18} weight="duotone" />} />
            <StatTile label="Stubs" value={totals.stubs} detail={`${totals.stubs - totals.soap} REST, ${totals.soap} SOAP`} icon={<BracketsCurly size={18} weight="duotone" />} />
            <StatTile label="Requests" value={totals.requests} detail="in the request log" icon={<Pulse size={18} weight="duotone" />} />
            <StatTile
              label="Unmatched"
              value={totals.unmatched}
              tone={totals.unmatched > 0 ? 'warn' : 'ok'}
              icon={totals.unmatched > 0 ? <Warning size={18} weight="duotone" /> : <CheckCircle size={18} weight="duotone" />}
              detail={totals.unmatched > 0 ? 'requests with no stub' : 'every request found a stub'}
            />
          </div>
          <div className="panel">
            <table className="project-table">
              <thead>
                <tr>
                  <th>Project</th>
                  <th>Mock URL</th>
                  <th className="hide-sm">Stubs</th>
                  <th className="hide-sm">Requests</th>
                  <th className="hide-sm" style={{ width: 150 }}>Last hour</th>
                </tr>
              </thead>
              <tbody>
                {projects.map((p) => (
                  <tr key={p.id} className="clickable" onClick={() => navigate(`/projects/${p.id}`)}>
                    <td>
                      <div className="project-title">
                        <span className="project-mark small" style={{ background: p.color }} aria-hidden="true">
                          {p.name.trim().charAt(0).toUpperCase()}
                        </span>
                        <Link to={`/projects/${p.id}`} onClick={(e) => e.stopPropagation()}>
                          {p.name}
                        </Link>
                      </div>
                      {p.description && <p className="muted small clamp-2">{p.description}</p>}
                    </td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <span className="url-cell">
                        <span className="mono small" title={p.mockUrl}>
                          <span className="muted">{p.mockUrl.slice(0, p.mockUrl.length - p.basePath.length)}</span>
                          {p.basePath}
                        </span>
                        <CopyButton text={p.mockUrl} label="Copy mock URL" />
                      </span>
                    </td>
                    <td className="hide-sm num">
                      {p.stubCount}
                      {p.soapStubCount > 0 && <span className="tag soap" style={{ marginLeft: 8 }}>{p.soapStubCount} SOAP</span>}
                    </td>
                    <td className="hide-sm">
                      <span className="num">{p.requestCount}</span>
                      {p.unmatchedCount > 0 && (
                        <span className="tag warn" style={{ marginLeft: 8 }} title="Requests that matched no stub">
                          <Warning size={11} weight="bold" style={{ marginRight: 3 }} /> {p.unmatchedCount} unmatched
                        </span>
                      )}
                      <div className="muted small">{timeAgo(p.lastRequestAt)}</div>
                    </td>
                    <td className="hide-sm" onClick={(e) => e.stopPropagation()}>
                      <ActivityBars counts={p.activity ?? []} height={28} label={`${p.name} requests`} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="muted small">
            <BookOpenText size={13} style={{ verticalAlign: '-2px' }} /> New to mocking with WireMock? <Link to="/examples">Install an example project</Link> and copy what you need.
          </p>
        </div>
      )}
      {creating && <ProjectDialog onClose={() => setCreating(false)} />}
    </div>
  );
}
