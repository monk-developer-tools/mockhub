import { Archive, BookOpenText, CaretRight, ClockCounterClockwise, Buildings, CircleHalf, Eye, GearSix, HardDrives, Key, MagnifyingGlass, Moon, PaperPlaneTilt, Plug, Plus, SidebarSimple, Sparkle, SquaresFour, Sun, Table, UsersThree } from '@phosphor-icons/react';
import { useCallback, useMemo } from 'react';
import { MOD_LABEL, useHotkeys } from '../state/hotkeys';
import { CommandPalette } from './CommandPalette';
import { ShortcutsDialog } from './ShortcutsDialog';
import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useApp } from '../state/AppContext';
import { applyTheme, readTheme, type ThemeChoice } from '../state/theme';
import { Avatar, TeamMark } from './Avatar';
import { Logo } from './Logo';
import { AccountDialog } from '../pages/AccountDialog';
import { OrgDialog } from '../pages/OrgDialog';
import { ProjectDialog } from '../pages/ProjectDialog';
import { OrgSwitcher } from './OrgSwitcher';
import { roleLabel } from './RoleBadge';
import { canSeeAuditLog } from '../model/audit';
import { readCollapsed, readSettingsOpen, writeCollapsed, writeSettingsOpen } from '../state/sidebar';
import { shortProjectName, sidebarProjects } from '../model/sidebarProjects';
import { TopBar } from './TopBar';

const NEXT_THEME: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' };
const THEME_ICON = { system: CircleHalf, light: Sun, dark: Moon };

export function Layout() {
  const { projects, orgProjects, currentOrg, role, info, session, signOut, canEdit, isAdmin, teamsIn, writableTeams, orgs } = useApp();
  const canCreateProjects = orgs.some((o) => writableTeams(o.id).length > 0);
  const [theme, setTheme] = useState<ThemeChoice>(readTheme);
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [palette, setPalette] = useState(false);
  const [shortcuts, setShortcuts] = useState(false);
  const [creatingOrg, setCreatingOrg] = useState(false);
  const [account, setAccount] = useState(false);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const location = useLocation();
  const ThemeIcon = THEME_ICON[theme];
  const activeProjectId = location.pathname.match(/^\/projects\/([^/]+)/)?.[1];
  const projectGroups = useMemo(() => {
    const teams = teamsIn(currentOrg?.id);
    const known = new Set(teams.map((t) => t.id));
    const ordered = [...teams.flatMap((t) => orgProjects.filter((p) => p.teamId === t.id)), ...orgProjects.filter((p) => !known.has(p.teamId))];
    const shown = new Set(sidebarProjects(ordered, activeProjectId));
    const groups = teams
      .map((t) => ({ id: t.id, name: t.name, projects: orgProjects.filter((p) => p.teamId === t.id && shown.has(p)) }))
      .filter((g) => g.projects.length > 0);
    const other = orgProjects.filter((p) => !known.has(p.teamId) && shown.has(p));
    return other.length ? [...groups, { id: 'other', name: 'Other', projects: other }] : groups;
  }, [orgProjects, teamsIn, currentOrg, activeProjectId]);
  const hiddenProjects = orgProjects.length - projectGroups.reduce((n, g) => n + g.projects.length, 0);
  const inSettings = /^\/(server|admin)(\/|$)/.test(location.pathname);
  const [settingsOpen, setSettingsOpen] = useState(readSettingsOpen);
  const showSettings = settingsOpen || inSettings || collapsed;
  const toggleSettings = () => {
    writeSettingsOpen(!settingsOpen);
    setSettingsOpen(!settingsOpen);
  };

  const cycleTheme = () => {
    const next = NEXT_THEME[theme];
    applyTheme(next);
    setTheme(next);
  };

  const toggleCollapsed = useCallback(() => {
    setCollapsed((was) => {
      writeCollapsed(!was);
      return !was;
    });
  }, []);

  const hotkeys = useMemo(
    () => [
      { key: 'k', mod: true, allowWhileTyping: true, handler: () => setPalette((open) => !open) },
      { key: '?', handler: () => setShortcuts(true) },
      { key: '[', handler: toggleCollapsed },
    ],
    [toggleCollapsed],
  );
  useHotkeys(hotkeys);

  return (
    <div className={`shell${collapsed ? ' collapsed' : ''}`}>
      <aside className={`sidebar${open ? ' open' : ''}`} onClick={(e) => (e.target as HTMLElement).closest('a') && setOpen(false)}>
        <div className="brand-row">
          <NavLink to="/" className="brand" aria-label="MockHub home">
            <Logo />
            <span className="brand-name">MockHub</span>
          </NavLink>
          <button className="btn ghost icon small collapse-toggle" onClick={toggleCollapsed} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} title={`${collapsed ? 'Expand' : 'Collapse'} sidebar ( [ )`} aria-pressed={collapsed}>
            <SidebarSimple size={16} />
          </button>
        </div>
        <div className="sidebar-top">
          <OrgSwitcher onNewOrg={() => setCreatingOrg(true)} />
          {canEdit && (
            <NavLink to="/new" className="btn primary new-mock" title={collapsed ? 'New mock' : undefined}>
              <Plus size={16} /> <span className="nav-label">New mock</span>
            </NavLink>
          )}
          <button className="palette-trigger" onClick={() => setPalette(true)} title={collapsed ? 'Search' : undefined}>
            <MagnifyingGlass size={14} />
            <span className="palette-label">Search…</span>
            <kbd>{MOD_LABEL} K</kbd>
          </button>
        </div>
        <div className="sidebar-scroll">
          <nav className="nav" aria-label="Main">
            <NavLink to="/" end title={collapsed ? 'Projects' : undefined}>
              <SquaresFour size={17} /> <span className="nav-label">Projects</span>
            </NavLink>
            {canEdit && (
              <NavLink to="/playground" title={collapsed ? 'Playground' : undefined}>
                <PaperPlaneTilt size={17} /> <span className="nav-label">Playground</span>
              </NavLink>
            )}
            <NavLink to="/examples" title={collapsed ? 'Examples' : undefined}>
              <BookOpenText size={17} /> <span className="nav-label">Examples</span>
            </NavLink>
            {currentOrg && (
              <NavLink to="/org" title={collapsed ? 'Teams & people' : undefined}
                className={({ isActive }) => (isActive && !location.pathname.startsWith('/org/datasets') && !location.pathname.startsWith('/org/connections') ? 'active' : undefined)}>
                <Buildings size={17} /> <span className="nav-label">Teams & people</span>
              </NavLink>
            )}
            {currentOrg && (
              <NavLink to="/org/datasets" title={collapsed ? 'Datasets' : undefined}>
                <Table size={17} /> <span className="nav-label">Datasets</span>
              </NavLink>
            )}
            {currentOrg && (
              <NavLink to="/org/connections" title={collapsed ? 'Connections' : undefined}>
                <Plug size={17} /> <span className="nav-label">Connections</span>
              </NavLink>
            )}
            {canSeeAuditLog(session) && (
              <NavLink to="/audit" title={collapsed ? 'Audit log' : undefined}>
                <ClockCounterClockwise size={17} /> <span className="nav-label">Audit log</span>
              </NavLink>
            )}
          </nav>
          <div className="sidebar-section">
            <span>Projects</span>
            {canCreateProjects && (
              <button className="btn ghost icon small" onClick={() => setCreating(true)} aria-label="New project" title="New project">
                <Plus size={14} />
              </button>
            )}
          </div>
          <div className="project-list">
            {orgProjects.length === 0 && <p className="project-empty">No projects yet.</p>}
            {projectGroups.map((group) => (
              <div key={group.id} className="project-group">
                {projectGroups.length > 1 && (
                  <div className="project-group-head" title={collapsed ? group.name : undefined}>
                    <TeamMark name={group.name} seed={group.id} size={14} />
                    <span>{group.name}</span>
                  </div>
                )}
                {group.projects.map((p) => (
                  <NavLink
                    key={p.id}
                    to={`/projects/${p.id}`}
                    className={() => `project-link${p.id === activeProjectId ? ' active' : ''}`}
                    title={`${p.name} · ${p.basePath || '/'}`}
                  >
                    <span className="swatch" style={{ background: p.color }} />
                    <span className="project-link-text">{shortProjectName(p.name)}</span>
                  </NavLink>
                ))}
              </div>
            ))}
            {hiddenProjects > 0 && (
              <NavLink to="/" end className="project-more">
                View all {orgProjects.length} projects
              </NavLink>
            )}
          </div>
        </div>
        <div className="sidebar-settings">
          <button className="settings-toggle" onClick={toggleSettings} aria-expanded={showSettings} aria-controls="sidebar-settings" title={collapsed ? 'Settings' : undefined}>
            <GearSix size={17} />
            <span className="nav-label">Settings</span>
            <CaretRight size={12} className="settings-caret" />
          </button>
          {showSettings && (
            <nav className="nav" id="sidebar-settings" aria-label="Settings">
              <NavLink to="/server" title={collapsed ? 'Server' : undefined}>
                <HardDrives size={17} /> <span className="nav-label">Server</span>
              </NavLink>
              {isAdmin && (
                <>
                  <NavLink to="/admin/users" title={collapsed ? 'Users' : undefined}>
                    <UsersThree size={17} /> <span className="nav-label">Users</span>
                  </NavLink>
                  <NavLink to="/admin/sso" title={collapsed ? 'Single sign-on' : undefined}>
                    <Key size={17} /> <span className="nav-label">Single sign-on</span>
                  </NavLink>
                  <NavLink to="/admin/ai" title={collapsed ? 'AI assistant' : undefined}>
                    <Sparkle size={17} /> <span className="nav-label">AI assistant</span>
                  </NavLink>
                  <NavLink to="/admin/backup" title={collapsed ? 'Backup & restore' : undefined}>
                    <Archive size={17} /> <span className="nav-label">Backup &amp; restore</span>
                  </NavLink>
                </>
              )}
            </nav>
          )}
        </div>
        <div className="sidebar-footer">
          {session && info?.authEnabled ? (
            <button className="account-trigger" onClick={() => setAccount(true)} aria-label="Your account">
              <Avatar name={session.displayName || session.username} seed={session.id} size={26} />
              <span>
                <strong>{session.displayName || session.username}</strong>
                <span className="muted">{role === 'none' ? 'No organisation access' : roleLabel(role)}</span>
              </span>
            </button>
          ) : (
            <span title={info ? `WireMock ${info.wiremockVersion}` : undefined}>v{info?.version ?? ''}</span>
          )}
        </div>
      </aside>
      <div className="main">
        <TopBar
          onMenu={() => setOpen(!open)}
          onShortcuts={() => setShortcuts(true)}
          onSearch={() => setPalette(true)}
          themeToggle={
            <button className="btn ghost icon" onClick={cycleTheme} aria-label={`Theme: ${theme}`} title={`Theme: ${theme}`}>
              <ThemeIcon size={18} />
            </button>
          }
        />
        {session && info?.authEnabled && !canEdit && (
          <div className="viewer-banner" role="status">
            <Eye size={15} />
            <span>
              {role === 'none' ? (
                <>You're signed in as <strong>{session.username}</strong>, but you aren't a member of any organisation yet. Ask an admin or an organisation manager to add you.</>
              ) : (
                <>You're signed in as <strong>{session.username}</strong>, who can look at {currentOrg?.name ?? 'this organisation'} but not change anything. Ask a manager for the editor role, or sign in as an editor.</>
              )}
            </span>
            <button className="btn small" onClick={signOut}>Switch account</button>
          </div>
        )}
        <Outlet />
      </div>
      {creating && <ProjectDialog onClose={() => setCreating(false)} />}
      {palette && (
        <CommandPalette
          projects={projects}
          canEdit={canCreateProjects}
          onClose={() => setPalette(false)}
          onNewProject={() => setCreating(true)}
          onToggleTheme={cycleTheme}
          onShowShortcuts={() => setShortcuts(true)}
        />
      )}
      {shortcuts && <ShortcutsDialog onClose={() => setShortcuts(false)} />}
      {creatingOrg && <OrgDialog onClose={() => setCreatingOrg(false)} />}
      {account && <AccountDialog onClose={() => setAccount(false)} />}
    </div>
  );
}
