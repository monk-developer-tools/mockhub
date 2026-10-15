import { useCallback, useEffect, useState } from 'react';
import { NavLink, Outlet, useOutletContext, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { Project } from '../../api/types';
import { CopyButton } from '../../components/CopyButton';
import { Skeleton } from '../../components/Skeleton';
import { useToast } from '../../components/Toasts';
import { useApp } from '../../state/AppContext';

export interface ProjectContext {
  project: Project;
  reloadProject: () => Promise<void>;
}

export function useProject() {
  return useOutletContext<ProjectContext>();
}

export function ProjectPage() {
  const { projectId = '' } = useParams();
  const toast = useToast();
  const [project, setProject] = useState<Project | null>(null);
  const [missing, setMissing] = useState(false);
  const { currentOrg, setCurrentOrg, orgs } = useApp();

  const reloadProject = useCallback(async () => {
    try {
      setProject(await api.project(projectId));
      setMissing(false);
    } catch (e) {
      setMissing(true);
      toast.fail(e);
    }
  }, [projectId, toast.fail]);

  useEffect(() => {
    if (project?.orgId && project.orgId !== currentOrg?.id && orgs.some((o) => o.id === project.orgId)) setCurrentOrg(project.orgId);
  }, [project?.orgId, currentOrg?.id, orgs, setCurrentOrg]);

  useEffect(() => {
    setProject(null);
    reloadProject();
  }, [reloadProject]);

  if (missing) {
    return (
      <div className="page">
        <div className="panel empty">
          <h2>Project not found</h2>
          <p>It may have been deleted. Pick another project from the sidebar.</p>
        </div>
      </div>
    );
  }
  if (!project) return <div className="page"><Skeleton rows={4} label="Loading project" /></div>;

  const tabs = [
    { to: 'overview', label: 'Overview' },
    { to: 'stubs', label: 'Stubs', count: project.stubCount },
    { to: 'requests', label: 'Requests', count: project.requestCount, alert: project.unmatchedCount },
    { to: 'scenarios', label: 'Scenarios' },
    { to: 'import-export', label: 'Import & export' },
    { to: 'settings', label: 'Settings' },
  ];

  return (
    <div className="page">
      <div className="page-header" style={{ marginBottom: 14 }}>
        <div style={{ minWidth: 0 }}>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="swatch" style={{ background: project.color, width: 12, height: 12 }} />
            {project.name}
          </h1>
          <div className="mock-url">
            <span>
              {project.mockUrl.slice(0, project.mockUrl.length - project.basePath.length)}
              <strong>{project.basePath}</strong>
            </span>
            <CopyButton text={project.mockUrl} label="Copy mock URL" />
          </div>
          {project.description && <p className="muted" style={{ marginTop: 4 }}>{project.description}</p>}
        </div>
      </div>
      <nav className="tabs" aria-label="Project sections">
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} className={({ isActive }) => (isActive ? 'active' : '')}>
            {t.label}
            {t.count !== undefined && <span className="count">{t.count}</span>}
            {Boolean(t.alert) && (
              <span className="count alert" title={`${t.alert} unmatched`}>
                {t.alert} unmatched
              </span>
            )}
          </NavLink>
        ))}
      </nav>
      <Outlet context={{ project, reloadProject } satisfies ProjectContext} />
    </div>
  );
}
