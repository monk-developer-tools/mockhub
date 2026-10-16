import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

let isOwner = true;
const createTeam = vi.fn((_org: string, body: { name: string }) => Promise.resolve({ id: 't3', name: body.name }));
const setMember = vi.fn((_org: string, _user: string, _role: string) => Promise.resolve({}));
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    currentOrg: { id: 'o1', name: 'Payments', role: isOwner ? 'owner' : 'viewer' },
    isOwner,
    isAdmin: false,
    session: { id: 'me' },
    orgs: [{ id: 'o1' }, { id: 'o2' }],
    refreshSession: vi.fn(),
    refreshProjects: vi.fn(),
  }),
}));
vi.mock('../api/client', () => ({
  api: {
    orgs: () => Promise.resolve([{ id: 'o1', name: 'Payments', description: '', projectCount: 2, memberCount: 1, teamCount: 2 }]),
    teams: () => Promise.resolve([
      { id: 't1', orgId: 'o1', name: 'General', slug: 'general', description: '', projectCount: 1, memberCount: 0, role: 'owner' },
      { id: 't2', orgId: 'o1', name: 'Checkout', slug: 'checkout', description: 'Cart pages', projectCount: 1, memberCount: 3, role: 'owner' },
    ]),
    createTeam: (org: string, body: { name: string }) => createTeam(org, body),
    members: () => Promise.resolve([
      { id: 'me', username: 'me', displayName: 'Me', email: '', source: 'local', role: 'owner', groupRole: '', effectiveRole: 'owner', disabled: false, teams: [] },
    ]),
    users: () => Promise.resolve([
      { id: 'me', username: 'me', displayName: 'Me', email: '', source: 'local' },
      { id: 'u2', username: 'qa', displayName: 'Quinn', email: 'quinn@x.com', source: 'azure' },
    ]),
    setMember: (org: string, user: string, role: string) => setMember(org, user, role),
  },
}));

const { OrgPage } = await import('./OrgPage');

describe('OrgPage', () => {
  beforeEach(() => {
    setMember.mockClear();
    isOwner = true;
  });

  it('lets owners add people with a role', async () => {
    // Given
    renderWithShell(<OrgPage />);
    expect(await screen.findByText('2 teams, 2 projects and 1 person')).toBeInTheDocument();

    // When
    await userEvent.selectOptions(screen.getByLabelText('Add a person'), 'u2');
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'viewer');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    // Then
    await waitFor(() => expect(setMember).toHaveBeenCalledWith('o1', 'u2', 'viewer'));
  });

  it('lists teams and lets owners create one', async () => {
    // Given
    renderWithShell(<OrgPage />);
    expect(await screen.findByRole('link', { name: /Checkout/ })).toHaveAttribute('href', '/org/teams/t2');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'New team' }));
    await userEvent.type(within(screen.getByRole('dialog')).getByLabelText('Name'), 'Search');
    await userEvent.click(screen.getByRole('button', { name: 'Create team' }));

    // Then
    await waitFor(() => expect(createTeam).toHaveBeenCalledWith('o1', { name: 'Search', description: '' }));
  });

  it('shows viewers the members without management controls', async () => {
    // Given
    isOwner = false;

    // When
    renderWithShell(<OrgPage />);

    // Then
    expect(await screen.findByText('Me')).toBeInTheDocument();
    expect(screen.queryByLabelText('Add a person')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Delete organisation/ })).not.toBeInTheDocument();
  });
});
