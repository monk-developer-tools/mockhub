import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

let isOwner = true;
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
    orgs: () => Promise.resolve([{ id: 'o1', name: 'Payments', description: '', projectCount: 2, memberCount: 1 }]),
    members: () => Promise.resolve([
      { id: 'me', username: 'me', displayName: 'Me', email: '', source: 'local', role: 'owner', groupRole: '', effectiveRole: 'owner', disabled: false },
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
    expect(await screen.findByText('2 projects · 1 member')).toBeInTheDocument();

    // When
    await userEvent.selectOptions(screen.getByLabelText('Add a person'), 'u2');
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'viewer');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    // Then
    await waitFor(() => expect(setMember).toHaveBeenCalledWith('o1', 'u2', 'viewer'));
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
