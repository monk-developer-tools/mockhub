import { BookOpen, Eye, LayoutGrid, LogOut, Menu, Moon, Plus, Search, Server, Sun, SunMoon } from 'lucide-react';
import { useMemo } from 'react';
import { MOD_LABEL, useHotkeys } from '../state/hotkeys';
import { CommandPalette } from './CommandPalette';
import { ShortcutsDialog } from './ShortcutsDialog';
import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useApp } from '../state/AppContext';
import { applyTheme, readTheme, type ThemeChoice } from '../state/theme';
import { Logo } from './Logo';
import { ProjectDialog } from '../pages/ProjectDialog';

const NEXT_THEME: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' };
const THEME_ICON = { system: SunMoon, light: Sun, dark: Moon };

export function Layout() {
  const { projects, info, session, signOut, canEdit } = useApp();
  const [theme, setTheme] = useState<ThemeChoice>(readTheme);
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [palette, setPalette] = useState(false);
  const [shortcuts, setShortcuts] = useState(false);
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
          <NavLink to="/server">
            <Server size={16} /> Server
          </NavLink>
        </nav>
        <div className="sidebar-section">
          <span>Your projects</span>
          {canEdit && (
            <button className="btn ghost icon small" onClick={() => setCreating(true)} aria-label="New project" title="New project">
              <Plus size={15} />
            </button>
          )}
        </div>
        <div className="project-list">
          {projects.length === 0 && <p className="muted small" style={{ padding: '4px 10px' }}>No projects yet.</p>}
          {projects.map((p) => (
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
          <span title={info ? `WireMock ${info.wiremockVersion}` : undefined}>
            {session && info?.authEnabled ? `${session.username} · ${session.role}` : `v${info?.version ?? ''}`}
          </span>
          <span className="btn-row">
            <button className="btn ghost icon small" onClick={cycleTheme} aria-label={`Theme: ${theme}`} title={`Theme: ${theme}`}>
              <ThemeIcon size={15} />
            </button>
            {info?.authEnabled && (
              <button className="btn ghost icon small" onClick={signOut} aria-label="Sign out" title="Sign out">
                <LogOut size={15} />
              </button>
            )}
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
        {session?.role === 'viewer' && (
          <div className="viewer-banner" role="status">
            <Eye size={15} />
            <span>
              You're signed in as <strong>{session.username}</strong>, who can look but not change anything. Sign in as an editor or
              admin to create projects and stubs.
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
    </div>
  );
}
