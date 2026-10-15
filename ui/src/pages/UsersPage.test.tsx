import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { UserRecord } from '../api/types';
import { renderWithShell } from '../test/render';

let authEnabled = true;
const acceptToken = vi.fn(() => Promise.resolve());
const created: Record<string, unknown>[] = [];
const ana: UserRecord = {
  id: 'u1', username: 'ana@contoso.com', displayName: 'Ana Tester', email: 'ana@contoso.com', source: 'azure',
  serverRole: 'none', groupServerRole: 'none', effectiveServerRole: 'none', disabled: false, hasPassword: false,
  lastLoginAt: new Date().toISOString(), memberships: [], groupMemberships: [{ orgId: 'o1', role: 'editor', orgName: 'QA' }],
};
vi.mock('../state/AppContext', () => ({
  useApp: () => ({ info: { authEnabled }, acceptToken, session: { id: 'me' }, refreshSession: vi.fn(), currentOrg: { id: 'o1' } }),
}));
vi.mock('../api/client', () => ({
  api: {
    users: () => Promise.resolve([ana]),
    orgs: () => Promise.resolve([{ id: 'o1', name: 'QA' }]),
    createUser: (body: Record<string, unknown>) => {
      created.push(body);
      return Promise.resolve({ ...ana, id: 'u2', token: 'tok' });
    },
  },
}));

const { UsersPage } = await import('./UsersPage');

describe('UsersPage', () => {
  beforeEach(() => {
    created.length = 0;
    authEnabled = true;
  });

  it('lists people with their source and organisation roles', async () => {
    // When
    renderWithShell(<UsersPage />);

    // Then
    expect(await screen.findByText('Ana Tester')).toBeInTheDocument();
    expect(screen.getByText('Entra ID')).toBeInTheDocument();
    expect(screen.getByText('QA')).toBeInTheDocument();
    expect(screen.getByText('Editor')).toBeInTheDocument();
  });

  it('creates a local user straight into the current organisation', async () => {
    // Given
    renderWithShell(<UsersPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New user' }));

    // When
    await userEvent.type(screen.getByLabelText('Username'), 'bob');
    await userEvent.type(screen.getByLabelText(/Temporary password/), 'longenough');
    await userEvent.click(screen.getByRole('button', { name: 'Create user' }));

    // Then
    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toMatchObject({ username: 'bob', password: 'longenough', serverRole: 'none', memberships: [{ orgId: 'o1', role: 'editor' }] });
  });

  it('turns sign-in on by creating the first admin', async () => {
    // Given
    authEnabled = false;
    renderWithShell(<UsersPage />);

    // When
    await userEvent.type(screen.getByLabelText('Username'), 'owner');
    await userEvent.type(screen.getByLabelText(/Password/), 'longenough');
    await userEvent.click(screen.getByRole('button', { name: 'Create admin and turn on sign-in' }));

    // Then
    await waitFor(() => expect(acceptToken).toHaveBeenCalledWith('tok'));
    expect(created[0]).toMatchObject({ username: 'owner', serverRole: 'admin' });
  });
});
