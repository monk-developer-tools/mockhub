import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { Role } from '../api/types';
import { renderWithShell } from '../test/render';

const signOut = vi.fn();
let role: Role = 'viewer';
let isAdmin = false;
const orgs = [{ id: 'o1', name: 'Payments', slug: 'payments', role: 'viewer' }];
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    projects: [],
    orgProjects: [],
    orgs,
    currentOrg: orgs[0],
    role,
    info: { version: '1', wiremockVersion: '3', authEnabled: true },
    session: { id: 'u1', username: 'demo', displayName: 'Demo', orgs },
    signOut,
    canEdit: role === 'editor' || role === 'owner' || role === 'admin',
    isAdmin,
    roleIn: () => role,
    setCurrentOrg: vi.fn(),
  }),
}));

const { Layout } = await import('./Layout');

describe('Layout', () => {
  beforeEach(() => {
    isAdmin = false;
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

  it('shows no banner and offers new projects to editors', () => {
    // Given
    role = 'editor';

    // When
    renderWithShell(<Layout />);

    // Then
    expect(screen.queryByText(/but not change anything/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New project' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument();
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
