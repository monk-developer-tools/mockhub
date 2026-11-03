import { BookOpenText, BracketsCurly, CheckCircle, ClockCounterClockwise, MagnifyingGlass, PaperPlaneTilt, Plus, Pulse, ShareNetwork, SquaresFour, Warning } from '@phosphor-icons/react';
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
import { PageHeader } from '../components/ui/PageHeader';

export function DashboardPage() {
  const { orgProjects: allProjects, currentOrg, canManage, info, refreshProjects, teamsIn } = useApp();
  const teams = teamsIn(currentOrg?.id);
  const [teamFilter, setTeamFilter] = useState('all');
  const projects = teamFilter === 'all' ? allProjects : allProjects.filter((p) => p.teamId === teamFilter);
  const teamName = (id: string) => teams.find((t) => t.id === id)?.name ?? '';
  const [query, setQuery] = useState('');
  const needle = query.trim().toLowerCase();
  const shown = needle
    ? projects.filter((p) => `${p.name} ${p.description} ${p.basePath} ${teamName(p.teamId)}`.toLowerCase().includes(needle))
    : projects;

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
      <PageHeader
        title={currentOrg ? `${currentOrg.name} projects` : 'Projects'}
        description={<>Each project answers under its own base path on <span className="mono">{info?.baseUrl}</span>. Point your service or tests there instead of the real dependency.</>}
        actions={
          canManage && (
            <button className="btn primary" onClick={() => setCreating(true)}>
              <Plus size={16} /> New project
            </button>
          )
        }
      />

      {allProjects.length === 0 ? (
        <div className="panel">
          <div className="empty">
            <EmptyArt kind="projects" />
            <h2>Create your first mock project</h2>
            <p>
              Start empty, import an OpenAPI spec, WSDL or WireMock mappings, or install an example below to see how REST, SOAP,
              templating and stateful mocks look.
            </p>
            {canManage && (
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
            <div className="table-toolbar">
              <label className="table-search">
                <MagnifyingGlass size={14} aria-hidden="true" />
                <input type="search" placeholder="Filter projects" aria-label="Filter projects" value={query} onChange={(e) => setQuery(e.target.value)} />
              </label>
              {teams.length > 1 && (
                <select className="table-select" aria-label="Filter by team" value={teamFilter} onChange={(e) => setTeamFilter(e.target.value)}>
                  <option value="all">All teams</option>
                  {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              )}
              <span className="muted small table-count">{shown.length === projects.length ? `${projects.length} projects` : `${shown.length} of ${projects.length}`}</span>
            </div>
            <table className="project-table">
              <thead>
                <tr>
                  <th>Project</th>
                  <th className="hide-sm">Base path</th>
                  <th className="hide-sm">Team</th>
                  <th className="hide-sm num-col">Stubs</th>
                  <th className="hide-sm num-col">Requests</th>
                  <th className="hide-sm">Last request</th>
                  <th className="hide-sm" style={{ width: 110 }}>Last hour</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((p) => (
                  <tr key={p.id} className="clickable" onClick={() => navigate(`/projects/${p.id}`)}>
                    <td className="project-cell">
                      <div className="project-title">
                        <span className="project-mark small" style={{ background: p.color }} aria-hidden="true">
                          {p.name.trim().charAt(0).toUpperCase()}
                        </span>
                        <span className="project-text">
                          <Link to={`/projects/${p.id}`} onClick={(e) => e.stopPropagation()} title={p.name}>{p.name}</Link>
                          {p.description && <span className="project-desc" title={p.description}>{p.description}</span>}
                        </span>
                      </div>
                    </td>
                    <td className="hide-sm" onClick={(e) => e.stopPropagation()}>
                      <span className="url-cell">
                        <span className="mono small" title={p.mockUrl}>{p.root ? '/' : p.basePath}</span>
                        <CopyButton text={p.mockUrl} label="Copy mock URL" />
                      </span>
                    </td>
                    <td className="hide-sm">
                      <span className="team-cell">{teamName(p.teamId) || '—'}</span>
                    </td>
                    <td className="hide-sm num-col">
                      {p.stubCount}
                      {p.soapStubCount > 0 && <span className="tag soap" style={{ marginLeft: 6 }}>{p.soapStubCount} SOAP</span>}
                    </td>
                    <td className="hide-sm num-col">
                      {p.requestCount}
                      {p.unmatchedCount > 0 && (
                        <span className="tag warn" style={{ marginLeft: 6 }} title="Requests that matched no stub">
                          {p.unmatchedCount} unmatched
                        </span>
                      )}
                    </td>
                    <td className="hide-sm muted small">{p.lastRequestAt ? timeAgo(p.lastRequestAt) : '—'}</td>
                    <td className="hide-sm" onClick={(e) => e.stopPropagation()}>
                      {(p.activity ?? []).some((n) => n > 0)
                        ? <ActivityBars counts={p.activity ?? []} height={22} label={`${p.name} requests`} />
                        : <span className="muted small">—</span>}
                    </td>
                  </tr>
                ))}
                {shown.length === 0 && (
                  <tr><td colSpan={7} className="muted small table-empty">No projects match “{query}”.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <section className="workflow" aria-labelledby="workflow-title">
            <h2 id="workflow-title">How it works</h2>
            <ol>
              <li>
                <span className="workflow-icon create"><Plus size={18} weight="bold" /></span>
                <strong>Create a mock</strong>
                <span>Add stubs by hand, import OpenAPI or WSDL, or ask the AI assistant.</span>
              </li>
              <li>
                <span className="workflow-icon test"><PaperPlaneTilt size={18} weight="fill" /></span>
                <strong>Test it</strong>
                <span>Call it from the <Link to="/playground">Playground</Link>, Try it, or your own app.</span>
              </li>
              <li>
                <span className="workflow-icon track"><ClockCounterClockwise size={18} weight="bold" /></span>
                <strong>Track every call</strong>
                <span>The request log shows each call and points out requests no stub matched.</span>
              </li>
              <li>
                <span className="workflow-icon share"><ShareNetwork size={18} weight="fill" /></span>
                <strong>Share it</strong>
                <span>Give your team the mock URL, or export to Postman and Bruno.</span>
              </li>
            </ol>
          </section>
          <p className="muted small">
            <BookOpenText size={13} style={{ verticalAlign: '-2px' }} /> New to mocking with WireMock? <Link to="/examples">Install an example project</Link> and copy what you need.
          </p>
        </div>
      )}
      {creating && <ProjectDialog onClose={() => setCreating(false)} />}
    </div>
  );
}
