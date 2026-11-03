import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { Role } from '../api/types';
import { renderWithShell } from '../test/render';

const signOut = vi.fn();
let role: Role = 'viewer';
let isAdmin = false;
const teams = [
  { id: 't1', name: 'Checkout', slug: 'checkout', role: 'viewer', member: true },
  { id: 't2', name: 'Search', slug: 'search', role: 'viewer', member: false },
];
const orgs = [{ id: 'o1', name: 'Payments', slug: 'payments', role: 'viewer', teams }];
let orgProjects: { id: string; name: string; basePath: string; color: string; teamId: string; orgId: string }[] = [];
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    projects: [],
    orgProjects,
    orgs,
    currentOrg: orgs[0],
    role,
    info: { version: '1', wiremockVersion: '3', authEnabled: true },
    session: { id: 'u1', username: 'demo', displayName: 'Demo', orgs },
    signOut,
    canEdit: role === 'editor' || role === 'manager' || role === 'admin',
    isAdmin,
    roleIn: () => role,
    teamsIn: () => teams,
    writableTeams: () => (role === 'manager' || role === 'admin' ? teams : []),
    setCurrentOrg: vi.fn(),
  }),
}));

const { Layout } = await import('./Layout');

describe('Layout', () => {
  beforeEach(() => {
    isAdmin = false;
    orgProjects = [];
  });

  it('groups the sidebar projects by team', () => {
    // Given
    role = 'viewer';
    orgProjects = [
      { id: 'p1', name: 'Cart', basePath: '/cart', color: '#f00', teamId: 't1', orgId: 'o1' },
      { id: 'p2', name: 'Index', basePath: '/index', color: '#0f0', teamId: 't2', orgId: 'o1' },
    ];

    // When
    renderWithShell(<Layout />);

    // Then
    const groups = document.querySelectorAll('.project-group');
    expect(groups).toHaveLength(2);
    expect(groups[0]).toHaveTextContent('Checkout');
    expect(groups[0]).toHaveTextContent('Cart');
    expect(groups[1]).toHaveTextContent('Search');
    expect(groups[1]).toHaveTextContent('Index');
  });

  it('tells viewers why they cannot edit and lets them switch account', async () => {
    // Given
    role = 'viewer';
    renderWithShell(<Layout />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Switch account' }));

    // Then
    expect(screen.getByText(/can look at Payments but not change anything/)).toBeInTheDocument();
    expect(signOut).toHaveBeenCalled();
  });

  it('tells people without an organisation who to ask', () => {
    // Given
    role = 'none';

    // When
    renderWithShell(<Layout />);

    // Then
    expect(screen.getByText(/aren't a member of any organisation yet/)).toBeInTheDocument();
  });

  it('shows editors no banner and no new project button', () => {
    // Given
    role = 'editor';

    // When
    renderWithShell(<Layout />);

    // Then
    expect(screen.queryByText(/but not change anything/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New project' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument();
  });

  it('offers new projects to team managers', () => {
    // Given
    role = 'manager';

    // When
    renderWithShell(<Layout />);

    // Then
    expect(screen.getByRole('button', { name: 'New project' })).toBeInTheDocument();
  });

  it('shows the administration links to admins', () => {
    // Given
    role = 'admin';
    isAdmin = true;

    // When
    renderWithShell(<Layout />);

    // Then
    expect(screen.getByRole('link', { name: 'Users' })).toHaveAttribute('href', '/admin/users');
    expect(screen.getByRole('link', { name: 'Single sign-on' })).toHaveAttribute('href', '/admin/sso');
  });

  it('collapses the sidebar to a rail and remembers it', async () => {
    // Given
    role = 'editor';
    localStorage.removeItem('mockhub.sidebar');
    const { container } = renderWithShell(<Layout />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Collapse sidebar' }));

    // Then
    expect(container.querySelector('.shell')).toHaveClass('collapsed');
    expect(localStorage.getItem('mockhub.sidebar')).toBe('collapsed');
    expect(screen.getByRole('link', { name: 'Playground' })).toHaveAttribute('title', 'Playground');
    await userEvent.click(screen.getByRole('button', { name: 'Expand sidebar' }));
    expect(container.querySelector('.shell')).not.toHaveClass('collapsed');
  });

  it('shows breadcrumbs and links help to the docs for the page', async () => {
    // Given
    role = 'editor';
    renderWithShell(<Layout />, '/admin/sso');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Help' }));

    // Then
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toHaveTextContent('Single sign-on');
    expect(screen.getByRole('menuitem', { name: 'Docs for this page' })).toHaveAttribute('href', 'https://mockhub.devops-monk.com/docs/#sso');
  });

  it('switches organisation from the sidebar', async () => {
    // Given
    role = 'editor';
    renderWithShell(<Layout />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Organisation Payments/ }));

    // Then
    expect(screen.getByRole('menuitemradio', { name: /Payments/ })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('menuitem', { name: 'Organisation settings' })).toBeInTheDocument();
  });
});
