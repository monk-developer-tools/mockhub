import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { AuditPage as AuditData } from '../api/types';
import { renderWithShell } from '../test/render';

const page: AuditData = {
  retentionDays: 90,
  entries: [
    { id: 'e2', at: new Date().toISOString(), actorId: 'u1', actor: 'Sam Patel', action: 'deleted', target: 'stub', targetName: 'GET /orders',
      details: '', orgId: 'o1', teamId: 't1', projectId: 'p1', projectName: 'Shop' },
    { id: 'e1', at: new Date().toISOString(), actorId: 'u2', actor: 'Maria Lopez', action: 'gave team access to', target: 'person', targetName: 'Li Wei',
      details: 'as editor', orgId: 'o1', teamId: 't1', projectId: null, projectName: null },
  ],
};
const audit = vi.fn((..._a: unknown[]) => Promise.resolve(page));

vi.mock('../api/client', () => ({ api: { audit: (...a: unknown[]) => audit(...a) } }));
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    isAdmin: true,
    orgs: [{ id: 'o1', name: 'Acme', slug: 'acme', role: 'admin', teams: [] }],
    projects: [{ id: 'p1', name: 'Shop', orgId: 'o1' }, { id: 'p2', name: 'Search', orgId: 'o1' }],
  }),
}));

const { AuditPage } = await import('./AuditPage');

describe('AuditPage', () => {
  it('shows who changed what, newest first', async () => {
    // When
    renderWithShell(<AuditPage />);

    // Then
    const rows = await screen.findAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('Sam Patel deleted stub “GET /orders”');
    expect(rows[0]).toHaveTextContent('Shop');
    expect(rows[1]).toHaveTextContent('Maria Lopez gave team access to person “Li Wei”');
    expect(rows[1]).toHaveTextContent('as editor');
    expect(screen.getByText(/kept for 90 days/)).toBeInTheDocument();
  });

  it('filters by project and by search text', async () => {
    // Given
    renderWithShell(<AuditPage />);
    await screen.findAllByRole('listitem');

    // When
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Project' }), 'p1');
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search activity' }), 'maria');

    // Then
    await waitFor(() => expect(audit).toHaveBeenLastCalledWith({ orgId: '', projectId: 'p1' }));
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(1));
    expect(screen.getByRole('listitem')).toHaveTextContent('Maria Lopez');
  });
});
