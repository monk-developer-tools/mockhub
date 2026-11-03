import { BookOpenText, Bug, GithubLogo, Keyboard, List, MagnifyingGlass, Question } from '@phosphor-icons/react';
import { useLocation } from 'react-router-dom';
import { breadcrumbsFor, docsFor, GITHUB_URL } from '../model/navigation';
import { useApp } from '../state/AppContext';
import { MOD_LABEL } from '../state/hotkeys';
import { Breadcrumbs } from './ui/Breadcrumbs';
import { Menu } from './ui/Menu';
import type { ReactNode } from 'react';

interface Props {
  onMenu: () => void;
  onShortcuts: () => void;
  onSearch: () => void;
  themeToggle: ReactNode;
}

export function TopBar({ onMenu, onShortcuts, onSearch, themeToggle }: Props) {
  const { currentOrg, activeProject, teamsIn } = useApp();
  const { pathname } = useLocation();
  const teamId = pathname.match(/^\/org\/teams\/([^/]+)/)?.[1];
  const crumbs = breadcrumbsFor(pathname, {
    orgName: currentOrg?.name,
    projectName: activeProject?.name,
    teamName: teamId ? teamsIn(currentOrg?.id).find((t) => t.id === teamId)?.name : undefined,
  });

  return (
    <header className="topbar">
      <button className="btn ghost icon topbar-menu" onClick={onMenu} aria-label="Menu">
        <List size={18} />
      </button>
      <Breadcrumbs crumbs={crumbs} />
      <div className="topbar-actions">
        <Menu
          label="Help"
          trigger={<Question size={18} />}
          items={[
            { label: 'Docs for this page', icon: <BookOpenText size={16} />, href: docsFor(pathname) },
            { label: 'Keyboard shortcuts', icon: <Keyboard size={16} />, hint: <kbd>?</kbd>, onSelect: onShortcuts },
            { label: 'Search and commands', icon: <MagnifyingGlass size={16} />, hint: <kbd>{MOD_LABEL} K</kbd>, onSelect: onSearch },
            { label: 'Report an issue', icon: <Bug size={16} />, href: `${GITHUB_URL}/issues/new` },
            { label: 'MockHub on GitHub', icon: <GithubLogo size={16} />, href: GITHUB_URL },
          ]}
        />
        {themeToggle}
      </div>
    </header>
  );
}
