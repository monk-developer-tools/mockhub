import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const signOut = vi.fn();
let role: 'viewer' | 'editor' = 'viewer';
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    projects: [],
    info: { version: '1', wiremockVersion: '3', authEnabled: true },
    session: { username: 'demo', role },
    signOut,
    canEdit: role !== 'viewer',
  }),
}));

const { Layout } = await import('./Layout');

describe('Layout', () => {
  it('tells viewers why they cannot edit and lets them switch account', async () => {
    // Given
    role = 'viewer';
    renderWithShell(<Layout />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Switch account' }));

    // Then
    expect(screen.getByText(/can look but not change anything/)).toBeInTheDocument();
    expect(signOut).toHaveBeenCalled();
  });

  it('shows no banner and offers new projects to editors', () => {
    // Given
    role = 'editor';

    // When
    renderWithShell(<Layout />);

    // Then
    expect(screen.queryByText(/can look but not change anything/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New project' })).toBeInTheDocument();
  });
});
