import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import type { Role } from '../api/types';
import { renderWithShell } from '../test/render';

let teamRole: Role = 'owner';
let isOwner = true;
const setTeamMember = vi.fn((..._a: unknown[]) => Promise.resolve({}));
const removeTeamMember = vi.fn((..._a: unknown[]) => Promise.resolve());
const team = { id: 't1', orgId: 'o1', name: 'Checkout', slug: 'checkout', description: 'Cart pages', projectCount: 1, memberCount: 1, role: 'owner' };

vi.mock('../state/AppContext', async (original) => ({
  ...(await original<typeof import('../state/AppContext')>()),
  useApp: () => ({
    currentOrg: { id: 'o1', name: 'Payments', role: 'viewer', teams: [] },
    isOwner,
    isAdmin: false,
    session: { id: 'me' },
    projects: [{ id: 'p1', name: 'Cart', basePath: '/cart', color: '#f00', stubCount: 3, teamId: 't1', orgId: 'o1' }],
    roleInTeam: () => teamRole,
    refreshSession: vi.fn(),
    refreshProjects: vi.fn(),
  }),
}));
vi.mock('../api/client', () => ({
  api: {
    teams: () => Promise.resolve([team]),
    teamMembers: () => Promise.resolve([
      { id: 'u1', username: 'maria', displayName: 'Maria Lopez', email: 'maria@x.com', source: 'local', role: 'editor', groupRole: '', orgRole: '', effectiveRole: 'editor', member: true, disabled: false },
      { id: 'u9', username: 'li', displayName: 'Li Wei', email: '', source: 'local', role: '', groupRole: '', orgRole: 'owner', effectiveRole: 'owner', member: false, disabled: false },
    ]),
    users: () => Promise.resolve([
      { id: 'u1', username: 'maria', displayName: 'Maria Lopez', email: 'maria@x.com', source: 'local' },
      { id: 'u2', username: 'sam', displayName: 'Sam Patel', email: 'sam@x.com', source: 'local' },
    ]),
    setTeamMember: (...a: unknown[]) => setTeamMember(...a),
    removeTeamMember: (...a: unknown[]) => removeTeamMember(...a),
  },
}));

const { TeamPage } = await import('./TeamPage');

function show() {
  return renderWithShell(<Routes><Route path="/org/teams/:teamId" element={<TeamPage />} /></Routes>, '/org/teams/t1');
}

describe('TeamPage', () => {
  beforeEach(() => {
    teamRole = 'owner';
    isOwner = true;
    setTeamMember.mockClear();
  });

  it('shows members, people with organisation access and the team projects', async () => {
    // When
    show();

    // Then
    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(screen.getByText('Maria Lopez')).toBeInTheDocument();
    expect(screen.getByText('Also have access through Payments')).toBeInTheDocument();
    expect(screen.getByText('Li Wei')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Cart/ })).toHaveAttribute('href', '/projects/p1');
    expect(screen.getByRole('button', { name: /Delete team/ })).toBeDisabled();
  });

  it('lets team owners add people with a team role', async () => {
    // Given
    show();
    await screen.findByText('Maria Lopez');

    // When
    await userEvent.click(screen.getAllByRole('button', { name: /Add people/ })[0]);
    const dialog = within(screen.getByRole('dialog'));
    expect(dialog.queryByText('Maria Lopez')).not.toBeInTheDocument();
    await userEvent.click(dialog.getByLabelText('Sam Patel'));
    await userEvent.click(dialog.getByLabelText('Viewer'));
    expect(dialog.getByText('Sam Patel will see the projects of Checkout. Other teams stay hidden.')).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Add person' }));

    // Then
    await waitFor(() => expect(setTeamMember).toHaveBeenCalledWith('o1', 't1', 'u2', 'viewer'));
  });

  it('hides management from team viewers', async () => {
    // Given
    teamRole = 'viewer';
    isOwner = false;

    // When
    show();

    // Then
    expect(await screen.findByText('Maria Lopez')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Add people/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Delete team/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New project' })).not.toBeInTheDocument();
  });
});
