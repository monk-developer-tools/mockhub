import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { SnapshotDiff, SnapshotSummary } from '../../../api/types';
import { renderWithShell } from '../../../test/render';

let canEdit = true;
const release: SnapshotSummary = {
  id: 's1', name: 'release 1.4', purpose: 'before the demo', createdAt: '2026-11-01T10:00:00Z', createdBy: 'Alex',
  safety: false, sourceProjectName: 'Shop', stubCount: 3, fileCount: 1, crudResources: ['books'],
};
const safety: SnapshotSummary = {
  ...release, id: 's2', name: 'Before restoring “release 1.3”', purpose: '', createdAt: '2026-11-02T10:00:00Z', safety: true, fileCount: 0, crudResources: [],
};
const diff: SnapshotDiff = {
  from: 'Now', to: 'release 1.4', identical: false,
  counts: { added: 1, removed: 0, changed: 1, unchanged: 1 },
  stubs: [
    { id: 'a', status: 'added', name: 'Reviews', method: 'GET', url: '/reviews', before: null, after: null },
    { id: 'b', status: 'changed', name: 'Authors', method: 'GET', url: '/authors', before: null, after: null },
  ],
  runtime: { changed: false, before: null, after: null },
  apiKeyHeader: { changed: false, before: null, after: null },
  files: { added: ['prices.csv'], removed: [], changed: [] },
};
let snapshots: SnapshotSummary[] = [];
const createSnapshot = vi.fn((..._a: unknown[]) => Promise.resolve(release));
const compareSnapshots = vi.fn((..._a: unknown[]) => Promise.resolve(diff));
const restoreSnapshot = vi.fn((..._a: unknown[]) => Promise.resolve({ name: 'release 1.4', safety, stubs: 3, files: 1 }));
const deleteSnapshot = vi.fn((..._a: unknown[]) => Promise.resolve());
const previewPull = vi.fn((..._a: unknown[]) => Promise.resolve(diff));
const pull = vi.fn((..._a: unknown[]) => Promise.resolve({ name: 'Shop dev', safety, stubs: 3, files: 1 }));
const reloadProject = vi.fn(() => Promise.resolve());

vi.mock('../../../api/client', () => ({
  api: {
    snapshots: (projectId: string) => Promise.resolve(projectId === 'p1' ? snapshots : []),
    createSnapshot: (...a: unknown[]) => createSnapshot(...a),
    compareSnapshots: (...a: unknown[]) => compareSnapshots(...a),
    restoreSnapshot: (...a: unknown[]) => restoreSnapshot(...a),
    deleteSnapshot: (...a: unknown[]) => deleteSnapshot(...a),
    previewPull: (...a: unknown[]) => previewPull(...a),
    pull: (...a: unknown[]) => pull(...a),
    downloadSnapshot: () => Promise.resolve(),
  },
}));
vi.mock('../../../state/AppContext', () => ({
  useApp: () => ({
    canEdit,
    projects: [{ id: 'p1', name: 'Shop', basePath: '/shop' }, { id: 'p2', name: 'Shop dev', basePath: '/shop-dev' }],
    refreshProjects: () => Promise.resolve(),
  }),
}));
vi.mock('../ProjectPage', () => ({
  useProject: () => ({ project: { id: 'p1', name: 'Shop', basePath: '/shop' }, reloadProject }),
}));

const { SnapshotsTab } = await import('./SnapshotsTab');

describe('SnapshotsTab', () => {
  beforeEach(() => {
    canEdit = true;
    snapshots = [safety, release];
    [createSnapshot, compareSnapshots, restoreSnapshot, deleteSnapshot, previewPull, pull, reloadProject].forEach((f) => f.mockClear());
  });

  it('lists snapshots with their author, contents and safety tag', async () => {
    // When
    renderWithShell(<SnapshotsTab />);

    // Then
    const rows = within(await screen.findByRole('list', { name: 'Snapshots' })).getAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('safety');
    expect(rows[1]).toHaveTextContent('release 1.4');
    expect(rows[1]).toHaveTextContent('before the demo');
    expect(rows[1]).toHaveTextContent('Alex');
    expect(rows[1]).toHaveTextContent('3 stubs · 1 file · CRUD: books');
    expect(screen.getByText(/don't hold stored records/)).toBeInTheDocument();
  });

  it('explains snapshots when there are none', async () => {
    // Given
    snapshots = [];

    // When
    renderWithShell(<SnapshotsTab />);

    // Then
    expect(await screen.findByText('No snapshots yet')).toBeInTheDocument();
  });

  it('takes a snapshot with a name and purpose', async () => {
    // Given
    renderWithShell(<SnapshotsTab />);
    await screen.findByRole('list', { name: 'Snapshots' });

    // When
    await userEvent.click(screen.getByRole('button', { name: /Take snapshot/ }));
    const form = screen.getByRole('form', { name: 'Take a snapshot' });
    await userEvent.type(within(form).getByLabelText('Name'), 'release 1.5');
    await userEvent.type(within(form).getByLabelText(/Purpose/), 'before retries');
    await userEvent.click(within(form).getByRole('button', { name: 'Take snapshot' }));

    // Then
    expect(createSnapshot).toHaveBeenCalledWith('p1', 'release 1.5', 'before retries');
    await waitFor(() => expect(screen.queryByRole('form', { name: 'Take a snapshot' })).not.toBeInTheDocument());
  });

  it('compares a snapshot with now and shows what changed', async () => {
    // Given
    renderWithShell(<SnapshotsTab />);
    const rows = within(await screen.findByRole('list', { name: 'Snapshots' })).getAllByRole('listitem');

    // When
    await userEvent.click(within(rows[1]).getByRole('button', { name: /Compare with now/ }));

    // Then
    expect(compareSnapshots).toHaveBeenCalledWith('p1', 's1', 'current');
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('status')).toHaveTextContent('1 stub added · 1 stub changed · 1 file different');
    expect(within(dialog).getByText('prices.csv')).toBeInTheDocument();
  });

  it('compares two selected snapshots from older to newer', async () => {
    // Given
    renderWithShell(<SnapshotsTab />);
    await screen.findByRole('list', { name: 'Snapshots' });

    // When
    await userEvent.click(screen.getByLabelText(/Select Before restoring/));
    await userEvent.click(screen.getByLabelText('Select release 1.4 to compare'));
    await userEvent.click(screen.getByRole('button', { name: /Compare selected/ }));

    // Then
    expect(compareSnapshots).toHaveBeenCalledWith('p1', 's1', 's2');
  });

  it('previews a restore and restores after confirmation', async () => {
    // Given
    renderWithShell(<SnapshotsTab />);
    await screen.findByRole('list', { name: 'Snapshots' });

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Restore release 1.4' }));
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByText(/1 stub added/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Restore' }));

    // Then
    expect(compareSnapshots).toHaveBeenCalledWith('p1', 'current', 's1');
    expect(restoreSnapshot).toHaveBeenCalledWith('p1', 's1');
    await waitFor(() => expect(reloadProject).toHaveBeenCalled());
    expect(await screen.findByText(/previous state is saved as/)).toBeInTheDocument();
  });

  it('pulls from another project after previewing the change', async () => {
    // Given
    renderWithShell(<SnapshotsTab />);
    await screen.findByRole('list', { name: 'Snapshots' });

    // When
    await userEvent.click(screen.getByRole('button', { name: /Pull from a project/ }));
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByText(/1 stub added/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Pull into this project' }));

    // Then
    expect(within(dialog).getByLabelText('From project')).toHaveValue('p2');
    expect(previewPull).toHaveBeenCalledWith('p1', 'p2', 'current');
    expect(pull).toHaveBeenCalledWith('p1', 'p2', 'current');
  });

  it('deletes a snapshot after confirmation', async () => {
    // Given
    renderWithShell(<SnapshotsTab />);
    await screen.findByRole('list', { name: 'Snapshots' });

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Delete release 1.4' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }));

    // Then
    expect(deleteSnapshot).toHaveBeenCalledWith('p1', 's1');
  });

  it('lets viewers compare and download but not change anything', async () => {
    // Given
    canEdit = false;

    // When
    renderWithShell(<SnapshotsTab />);
    await screen.findByRole('list', { name: 'Snapshots' });

    // Then
    expect(screen.queryByRole('button', { name: /Take snapshot/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Pull from a project/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Restore release 1.4' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete release 1.4' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download release 1.4' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Compare with now/ })).toHaveLength(2);
  });
});
