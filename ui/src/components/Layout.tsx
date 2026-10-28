import { Archive, BookOpenText, ClockCounterClockwise, Buildings, CircleHalf, Eye, HardDrives, Key, List, MagnifyingGlass, Moon, PaperPlaneTilt, Plus, Sparkle, SquaresFour, Sun, UsersThree } from '@phosphor-icons/react';
import { useMemo } from 'react';
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

const NEXT_THEME: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' };
const THEME_ICON = { system: CircleHalf, light: Sun, dark: Moon };

export function Layout() {
  const { projects, orgProjects, currentOrg, role, info, session, signOut, canEdit, isAdmin, teamsIn } = useApp();
  const [theme, setTheme] = useState<ThemeChoice>(readTheme);
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [palette, setPalette] = useState(false);
  const [shortcuts, setShortcuts] = useState(false);
  const [creatingOrg, setCreatingOrg] = useState(false);
  const [account, setAccount] = useState(false);
  const location = useLocation();
  const ThemeIcon = THEME_ICON[theme];
  const projectGroups = useMemo(() => {
    const teams = teamsIn(currentOrg?.id);
    const groups = teams
      .map((t) => ({ id: t.id, name: t.name, projects: orgProjects.filter((p) => p.teamId === t.id) }))
      .filter((g) => g.projects.length > 0);
    const known = new Set(teams.map((t) => t.id));
    const other = orgProjects.filter((p) => !known.has(p.teamId));
    return other.length ? [...groups, { id: 'other', name: 'Other', projects: other }] : groups;
  }, [orgProjects, teamsIn, currentOrg]);

  const cycleTheme = () => {
    const next = NEXT_THEME[theme];
    applyTheme(next);
    setTheme(next);
  };

  const hotkeys = useMemo(
    () => [
      { key: 'k', mod: true, allowWhileTyping: true, handler: () => setPalette((open) => !open) },
      { key: '?', handler: () => setShortcuts(true) },
    ],
    [],
  );
  useHotkeys(hotkeys);

  return (
    <div className="shell">
      <aside className={`sidebar${open ? ' open' : ''}`} onClick={(e) => (e.target as HTMLElement).closest('a') && setOpen(false)}>
        <NavLink to="/" className="brand">
          <Logo />
          MockHub
        </NavLink>
        <OrgSwitcher onNewOrg={() => setCreatingOrg(true)} />
        <button className="palette-trigger" onClick={() => setPalette(true)}>
          <MagnifyingGlass size={14} />
          <span>Search…</span>
          <kbd>{MOD_LABEL} K</kbd>
        </button>
        <nav className="nav" aria-label="Main">
          <NavLink to="/" end>
            <SquaresFour size={18} weight="duotone" /> Projects
          </NavLink>
          {canEdit && (
            <NavLink to="/playground">
              <PaperPlaneTilt size={18} weight="duotone" /> Playground
            </NavLink>
          )}
          <NavLink to="/examples">
            <BookOpenText size={18} weight="duotone" /> Examples
          </NavLink>
          {currentOrg && (
            <NavLink to="/org">
              <Buildings size={18} weight="duotone" /> Teams & people
            </NavLink>
          )}
          {canSeeAuditLog(session) && (
            <NavLink to="/audit">
              <ClockCounterClockwise size={18} weight="duotone" /> Audit log
            </NavLink>
          )}
          <NavLink to="/server">
            <HardDrives size={18} weight="duotone" /> Server
          </NavLink>
        </nav>
        {isAdmin && (
          <>
            <div className="sidebar-section"><span>Administration</span></div>
            <nav className="nav" aria-label="Administration">
              <NavLink to="/admin/users">
                <UsersThree size={18} weight="duotone" /> Users
              </NavLink>
              <NavLink to="/admin/sso">
                <Key size={18} weight="duotone" /> Single sign-on
              </NavLink>
              <NavLink to="/admin/ai">
                <Sparkle size={18} weight="duotone" /> AI assistant
              </NavLink>
              <NavLink to="/admin/backup">
                <Archive size={18} weight="duotone" /> Backup &amp; restore
              </NavLink>
            </nav>
          </>
        )}
        <div className="sidebar-section">
          <span>Projects in {currentOrg?.name ?? 'this organisation'}</span>
          {canEdit && (
            <button className="btn ghost icon small" onClick={() => setCreating(true)} aria-label="New project" title="New project">
              <Plus size={15} />
            </button>
          )}
        </div>
        <div className="project-list">
          {orgProjects.length === 0 && <p className="muted small" style={{ padding: '4px 10px' }}>No projects yet.</p>}
          {projectGroups.map((group) => (
            <div key={group.id} className="project-group">
              {projectGroups.length > 1 && (
                <div className="project-group-head">
                  <TeamMark name={group.name} seed={group.id} size={16} />
                  <span>{group.name}</span>
                </div>
              )}
              {group.projects.map((p) => (
                <NavLink
                  key={p.id}
                  to={`/projects/${p.id}`}
                  className={() => `project-link${location.pathname.startsWith(`/projects/${p.id}`) ? ' active' : ''}`}
                >
                  <span className="swatch" style={{ background: p.color }} />
                  <span className="project-link-text" title={`${p.name} (${p.basePath})`}>
                    <span className="name">{p.name}</span>
                    <span className="path">{p.basePath}</span>
                  </span>
                </NavLink>
              ))}
            </div>
          ))}
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
          <span className="btn-row">
            <button className="btn ghost icon small" onClick={cycleTheme} aria-label={`Theme: ${theme}`} title={`Theme: ${theme}`}>
              <ThemeIcon size={15} />
            </button>
          </span>
        </div>
      </aside>
      <div className="main">
        <div className="mobile-bar">
          <button className="btn ghost icon" onClick={() => setOpen(!open)} aria-label="Menu">
            <List size={18} />
          </button>
          <Logo size={20} /> <strong>MockHub</strong>
        </div>
        {session && info?.authEnabled && !canEdit && (
          <div className="viewer-banner" role="status">
            <Eye size={15} />
            <span>
              {role === 'none' ? (
                <>You're signed in as <strong>{session.username}</strong>, but you aren't a member of any organisation yet. Ask an admin or an organisation owner to add you.</>
              ) : (
                <>You're signed in as <strong>{session.username}</strong>, who can look at {currentOrg?.name ?? 'this organisation'} but not change anything. Ask an owner for the editor role, or sign in as an editor.</>
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
          canEdit={canEdit}
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
