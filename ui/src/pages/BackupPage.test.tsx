import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { RestorePreview } from '../api/types';
import { renderWithShell } from '../test/render';

const preview: RestorePreview = {
  createdAt: '2026-10-16T10:00:00Z', mockhubVersion: '1.0.0', scope: 'full', unassignedStubs: 0,
  organizations: [{
    id: 'o1', name: 'Payments Team', slug: 'payments', exists: true,
    projects: [
      { id: 'p1', name: 'Pay By Bank', slug: 'pay-by-bank', stubCount: 4, status: 'replace' },
      { id: 'p2', name: 'Cards', slug: 'cards', stubCount: 2, status: 'slug-taken', newSlug: 'cards-2' },
    ],
  }],
};
const backup = vi.fn((..._a: unknown[]) => Promise.resolve());
const previewRestore = vi.fn((..._a: unknown[]) => Promise.resolve(preview));
const restore = vi.fn((..._a: unknown[]) => Promise.resolve({ organizations: 0, projects: 1, stubs: 2, renamed: [{ project: 'Cards', from: '/cards', to: '/cards-2' }] }));
const refreshProjects = vi.fn(() => Promise.resolve());

vi.mock('../api/client', () => ({
  api: {
    backup: (...a: unknown[]) => backup(...a),
    previewRestore: (...a: unknown[]) => previewRestore(...a),
    restore: (...a: unknown[]) => restore(...a),
  },
}));
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    orgs: [{ id: 'o1', name: 'Payments Team', slug: 'payments', role: 'admin' }, { id: 'o2', name: 'Retail', slug: 'retail', role: 'admin' }],
    projects: [
      { id: 'p1', name: 'Pay By Bank', basePath: '/pay-by-bank', stubCount: 4, orgId: 'o1', color: '#55f' },
      { id: 'p2', name: 'Cards', basePath: '/cards', stubCount: 2, orgId: 'o1', color: '#f55' },
      { id: 'p3', name: 'Basket', basePath: '/basket', stubCount: 1, orgId: 'o2', color: '#5f5' },
    ],
    refreshProjects,
    refreshSession: () => Promise.resolve(),
    teamsIn: (id: string) => (id === 'o2' ? [{ id: 't9', name: 'Storefront', slug: 'storefront', role: 'admin', member: false }] : []),
  }),
}));

const { BackupPage } = await import('./BackupPage');

describe('BackupPage', () => {
  beforeEach(() => {
    backup.mockClear();
    restore.mockClear();
  });

  it('backs up one project from an organisation', async () => {
    // Given
    renderWithShell(<BackupPage />);
    const download = screen.getByRole('button', { name: /Download backup/ });
    expect(download).toBeDisabled();

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Show projects in Payments Team' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Pay By Bank' }));
    await userEvent.click(download);

    // Then
    expect(screen.getByText('1 organisation, 1 project, 4 stubs')).toBeInTheDocument();
    expect(backup).toHaveBeenCalledWith({ organizations: [], projects: ['p1'] });
  });

  it('backs up whole organisations or everything', async () => {
    // Given
    renderWithShell(<BackupPage />);

    // When
    await userEvent.click(screen.getByRole('checkbox', { name: 'Retail' }));
    await userEvent.click(screen.getByRole('button', { name: /Download backup/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Select all' }));
    await userEvent.click(screen.getByRole('button', { name: /Download backup/ }));

    // Then
    expect(backup).toHaveBeenNthCalledWith(1, { organizations: ['o2'], projects: [] });
    expect(backup).toHaveBeenNthCalledWith(2, { everything: true, organizations: [], projects: [] });
  });

  it('previews a file and restores the chosen project as a copy in another organisation', async () => {
    // Given
    renderWithShell(<BackupPage />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['{"projects":[]}'], 'payments.json', { type: 'application/json' });
    Object.defineProperty(file, 'text', { value: () => Promise.resolve('{"projects":[]}') });

    // When
    await userEvent.upload(input, file);
    expect(await screen.findByText('Replaces existing')).toBeInTheDocument();
    expect(screen.getByText('Moves to /cards-2')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Pay By Bank' }));
    await userEvent.selectOptions(screen.getByLabelText('Restore into'), 'o2');
    await userEvent.selectOptions(screen.getByLabelText('Team'), 't9');
    await userEvent.click(screen.getByRole('button', { name: 'Add a copy' }));
    await userEvent.click(screen.getByRole('button', { name: 'Restore 1 project' }));

    // Then
    await waitFor(() => expect(restore).toHaveBeenCalledWith('{"projects":[]}', { organizations: [], projects: ['p2'], targetOrgId: 'o2', targetTeamId: 't9', mode: 'copy' }));
    expect(await screen.findByText(/now answers at/)).toHaveTextContent('Cards now answers at /cards-2 because /cards was taken.');
    expect(refreshProjects).toHaveBeenCalled();
  });
});
