import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { AccessToken } from '../../api/types';
import { renderWithShell } from '../../test/render';

let isAdmin = false;
const laptop: AccessToken = {
  id: 't1', name: 'Claude Code on laptop', prefix: 'mh_AbC12x', role: 'editor', mode: 'direct', projects: [],
  createdAt: '2026-01-01T00:00:00Z', expiresAt: null, lastUsedAt: null, expired: false,
};
let tokens: AccessToken[] = [laptop];
const createAccessToken = vi.fn((..._a: unknown[]) => Promise.resolve({
  ...laptop, id: 't2', name: 'CI', role: 'viewer' as const, mode: 'review' as const, projects: [{ id: 'p2', name: 'Search' }],
  secret: 'mh_secretvalue123',
}));
const revokeAccessToken = vi.fn((..._a: unknown[]) => Promise.resolve());

vi.mock('../../api/client', async (original) => ({
  ...(await original<typeof import('../../api/client')>()),
  api: {
    accessTokens: () => Promise.resolve(tokens),
    createAccessToken: (...a: unknown[]) => createAccessToken(...a),
    revokeAccessToken: (...a: unknown[]) => revokeAccessToken(...a),
  },
}));
vi.mock('../../state/AppContext', () => ({
  useApp: () => ({
    session: { isAdmin },
    projects: [{ id: 'p1', name: 'Shop' }, { id: 'p2', name: 'Search' }],
  }),
}));

const { AccessTokens } = await import('./AccessTokens');

describe('AccessTokens', () => {
  beforeEach(() => {
    isAdmin = false;
    tokens = [laptop];
    createAccessToken.mockClear();
    revokeAccessToken.mockClear();
  });

  it('lists tokens with their access, scope and use', async () => {
    // When
    renderWithShell(<AccessTokens />);

    // Then
    const row = within(await screen.findByRole('list', { name: 'Access tokens' })).getByRole('listitem');
    expect(row).toHaveTextContent('Claude Code on laptop');
    expect(row).toHaveTextContent('mh_AbC12x…');
    expect(row).toHaveTextContent('editor');
    expect(row).toHaveTextContent('applies directly');
    expect(row).toHaveTextContent('All projects');
    expect(row).toHaveTextContent(/never used/i);
  });

  it('creates a scoped review token and shows the secret once with setup for agents', async () => {
    // Given
    renderWithShell(<AccessTokens />);
    await userEvent.click(await screen.findByRole('button', { name: 'New token' }));
    expect(screen.queryByRole('option', { name: /Admin/ })).not.toBeInTheDocument();

    // When
    await userEvent.type(screen.getByRole('textbox', { name: /Name/ }), 'CI');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Access' }), 'viewer');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Expires after' }), '30');
    await userEvent.click(screen.getByRole('button', { name: 'Stage for review' }));
    await userEvent.click(screen.getByRole('button', { name: 'Only some' }));
    expect(screen.getByRole('button', { name: 'Create token' })).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Search' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create token' }));

    // Then
    expect(createAccessToken).toHaveBeenCalledWith({ name: 'CI', role: 'viewer', mode: 'review', expiresInDays: 30, projectIds: ['p2'] });
    expect(await screen.findByText('mh_secretvalue123')).toBeInTheDocument();
    expect(screen.getByText(/can't show it again/)).toBeInTheDocument();
    expect(screen.getByText(/claude mcp add/)).toHaveTextContent('mh_secretvalue123');
    await userEvent.click(screen.getByRole('button', { name: 'Cursor' }));
    expect(screen.getByText(/mcpServers/)).toHaveTextContent('/__admin/mockhub/mcp');
  });

  it('revokes a token after confirmation', async () => {
    // Given
    renderWithShell(<AccessTokens />);
    await userEvent.click(await screen.findByRole('button', { name: 'Revoke Claude Code on laptop' }));

    // When
    tokens = [];
    await userEvent.click(screen.getByRole('button', { name: 'Revoke' }));

    // Then
    expect(revokeAccessToken).toHaveBeenCalledWith('t1');
    await waitFor(() => expect(screen.getByText('No tokens yet.')).toBeInTheDocument());
  });
});
