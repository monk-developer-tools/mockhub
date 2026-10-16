import { Archive, BookOpen, Building2, Eye, KeyRound, Sparkles, LayoutGrid, Menu, Moon, Plus, Search, Server, Sun, SunMoon, UserRound, Users } from 'lucide-react';
import { useMemo } from 'react';
import { MOD_LABEL, useHotkeys } from '../state/hotkeys';
import { CommandPalette } from './CommandPalette';
import { ShortcutsDialog } from './ShortcutsDialog';
import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useApp } from '../state/AppContext';
import { applyTheme, readTheme, type ThemeChoice } from '../state/theme';
import { Logo } from './Logo';
import { AccountDialog } from '../pages/AccountDialog';
import { OrgDialog } from '../pages/OrgDialog';
import { ProjectDialog } from '../pages/ProjectDialog';
import { OrgSwitcher } from './OrgSwitcher';
import { roleLabel } from './RoleBadge';

const NEXT_THEME: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' };
const THEME_ICON = { system: SunMoon, light: Sun, dark: Moon };

export function Layout() {
  const { projects, orgProjects, currentOrg, role, info, session, signOut, canEdit, isAdmin } = useApp();
  const [theme, setTheme] = useState<ThemeChoice>(readTheme);
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [palette, setPalette] = useState(false);
  const [shortcuts, setShortcuts] = useState(false);
  const [creatingOrg, setCreatingOrg] = useState(false);
  const [account, setAccount] = useState(false);
  const location = useLocation();
  const ThemeIcon = THEME_ICON[theme];

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
          <Search size={14} />
          <span>Search…</span>
          <kbd>{MOD_LABEL} K</kbd>
        </button>
        <nav className="nav" aria-label="Main">
          <NavLink to="/" end>
            <LayoutGrid size={16} /> Projects
          </NavLink>
          <NavLink to="/examples">
            <BookOpen size={16} /> Examples
          </NavLink>
          {currentOrg && (
            <NavLink to="/org">
              <Building2 size={16} /> Members & settings
            </NavLink>
          )}
          <NavLink to="/server">
            <Server size={16} /> Server
          </NavLink>
        </nav>
        {isAdmin && (
          <>
            <div className="sidebar-section"><span>Administration</span></div>
            <nav className="nav" aria-label="Administration">
              <NavLink to="/admin/users">
                <Users size={16} /> Users
              </NavLink>
              <NavLink to="/admin/sso">
                <KeyRound size={16} /> Single sign-on
              </NavLink>
              <NavLink to="/admin/ai">
                <Sparkles size={16} /> AI assistant
              </NavLink>
              <NavLink to="/admin/backup">
                <Archive size={16} /> Backup &amp; restore
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
          {orgProjects.map((p) => (
            <NavLink
              key={p.id}
              to={`/projects/${p.id}`}
              className={() => `project-link${location.pathname.startsWith(`/projects/${p.id}`) ? ' active' : ''}`}
            >
              <span className="swatch" style={{ background: p.color }} />
              <span>
                {p.name}
                <span className="path">{p.basePath}</span>
              </span>
            </NavLink>
          ))}
        </div>
        <div className="sidebar-footer">
          {session && info?.authEnabled ? (
            <button className="account-trigger" onClick={() => setAccount(true)} aria-label="Your account">
              <UserRound size={15} />
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
            <Menu size={18} />
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
