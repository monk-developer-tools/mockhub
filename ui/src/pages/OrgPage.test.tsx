import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

let isManager = true;
const createTeam = vi.fn((_org: string, body: { name: string }) => Promise.resolve({ id: 't3', name: body.name }));
const setMember = vi.fn((..._a: unknown[]) => Promise.resolve({}));
const removeMember = vi.fn((..._a: unknown[]) => Promise.resolve());
const setTeamMember = vi.fn((..._a: unknown[]) => Promise.resolve({}));
const removeTeamMember = vi.fn((..._a: unknown[]) => Promise.resolve());
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    currentOrg: { id: 'o1', name: 'Payments', role: isManager ? 'manager' : 'viewer' },
    isManager,
    isAdmin: false,
    canEdit: isManager,
    session: { id: 'me' },
    orgs: [{ id: 'o1' }, { id: 'o2' }],
    refreshSession: vi.fn(),
    refreshProjects: vi.fn(),
  }),
}));
vi.mock('../api/client', () => ({
  api: {
    orgs: () => Promise.resolve([{ id: 'o1', name: 'Payments', description: '', projectCount: 2, memberCount: 2, teamCount: 2 }]),
    teams: () => Promise.resolve([
      { id: 't1', orgId: 'o1', name: 'General', slug: 'general', description: '', projectCount: 1, memberCount: 0, role: 'manager' },
      { id: 't2', orgId: 'o1', name: 'Checkout', slug: 'checkout', description: 'Cart pages', projectCount: 1, memberCount: 3, role: 'manager' },
    ]),
    createTeam: (org: string, body: { name: string }) => createTeam(org, body),
    members: () => Promise.resolve([
      { id: 'me', username: 'me', displayName: 'Me', email: '', source: 'local', role: 'manager', groupRole: '', effectiveRole: 'manager', disabled: false, teams: [] },
      { id: 'u3', username: 'kim', displayName: 'Kim Lee', email: 'kim@x.com', source: 'local', role: '', groupRole: '', effectiveRole: 'editor', disabled: false,
        teams: [{ id: 't2', name: 'Checkout', role: 'editor' }] },
    ]),
    users: () => Promise.resolve([
      { id: 'me', username: 'me', displayName: 'Me', email: '', source: 'local' },
      { id: 'u2', username: 'qa', displayName: 'Quinn', email: 'quinn@x.com', source: 'azure' },
      { id: 'u3', username: 'kim', displayName: 'Kim Lee', email: 'kim@x.com', source: 'local' },
    ]),
    setMember: (...a: unknown[]) => setMember(...a),
    removeMember: (...a: unknown[]) => removeMember(...a),
    setTeamMember: (...a: unknown[]) => setTeamMember(...a),
    removeTeamMember: (...a: unknown[]) => removeTeamMember(...a),
  },
}));

const { OrgPage } = await import('./OrgPage');

describe('OrgPage', () => {
  beforeEach(() => {
    [setMember, removeMember, setTeamMember, removeTeamMember].forEach((f) => f.mockClear());
    isManager = true;
  });

  it('explains how organisations, teams and projects fit together', async () => {
    // When
    renderWithShell(<OrgPage />);

    // Then
    const map = await screen.findByRole('region', { name: 'How it fits together' });
    expect(within(map).getByText('2 teams')).toBeInTheDocument();
    expect(within(map).getByText('2 projects')).toBeInTheDocument();
    expect(within(map).getByText('2 people')).toBeInTheDocument();
  });

  it('adds a person to the whole organisation in three choices', async () => {
    // Given
    renderWithShell(<OrgPage />);
    await screen.findByText('2 teams', { selector: 'strong' });

    // When
    await userEvent.click(screen.getAllByRole('button', { name: /Add people/ })[0]);
    const dialog = within(screen.getByRole('dialog'));
    await userEvent.click(dialog.getByLabelText('Quinn'));
    await userEvent.click(dialog.getByLabelText('Viewer'));
    expect(dialog.getByText('Quinn will see every project in Payments, including teams added later.')).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Add person' }));

    // Then
    await waitFor(() => expect(setMember).toHaveBeenCalledWith('o1', 'u2', 'viewer'));
    expect(setTeamMember).not.toHaveBeenCalled();
  });

  it('adds a person to chosen teams only', async () => {
    // Given
    renderWithShell(<OrgPage />);
    await screen.findByText('2 teams', { selector: 'strong' });

    // When
    await userEvent.click(screen.getAllByRole('button', { name: /Add people/ })[0]);
    const dialog = within(screen.getByRole('dialog'));
    await userEvent.click(dialog.getByLabelText('Quinn'));
    await userEvent.click(dialog.getByText('Only some teams'));
    await userEvent.click(dialog.getByLabelText('Checkout'));
    await userEvent.click(dialog.getByRole('button', { name: 'Add person' }));

    // Then
    await waitFor(() => expect(setTeamMember).toHaveBeenCalledWith('o1', 't2', 'u2', 'editor'));
    expect(setMember).not.toHaveBeenCalled();
  });

  it('shows where each person works in plain words and moves them to the whole organisation', async () => {
    // Given
    renderWithShell(<OrgPage />, '/org?tab=people');
    const row = (await screen.findByText('Kim Lee')).closest('tr')!;
    expect(within(row).getByText('Checkout')).toBeInTheDocument();

    // When
    await userEvent.click(within(row).getByRole('button', { name: 'Change access for kim' }));
    const dialog = within(screen.getByRole('dialog'));
    await userEvent.click(dialog.getByText('Whole organisation'));
    await userEvent.click(dialog.getByRole('button', { name: 'Save access' }));

    // Then
    await waitFor(() => expect(setMember).toHaveBeenCalledWith('o1', 'u3', 'editor'));
    expect(removeTeamMember).toHaveBeenCalledWith('o1', 't2', 'u3');
  });

  it('lists teams as cards and lets managers create one', async () => {
    // Given
    renderWithShell(<OrgPage />);
    expect(await screen.findByRole('link', { name: /Checkout/ })).toHaveAttribute('href', '/org/teams/t2');

    // When
    await userEvent.click(screen.getByRole('button', { name: /New team/ }));
    await userEvent.type(within(screen.getByRole('dialog')).getByLabelText('Name'), 'Search');
    await userEvent.click(screen.getByRole('button', { name: 'Create team' }));

    // Then
    await waitFor(() => expect(createTeam).toHaveBeenCalledWith('o1', { name: 'Search', description: '' }));
  });

  it('shows viewers the people without management controls', async () => {
    // Given
    isManager = false;

    // When
    renderWithShell(<OrgPage />, '/org?tab=people');

    // Then
    expect(await screen.findByText('Me')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Add people/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Settings/ })).not.toBeInTheDocument();
  });
});
