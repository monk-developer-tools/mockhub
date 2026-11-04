import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const customers = {
  orgId: 'o1', key: 'customers', name: 'Customers', description: 'People who buy', identity: ['id'], indexes: ['city'],
  columns: [{ name: 'id', type: 'number' }, { name: 'name', type: 'string' }, { name: 'city', type: 'string' }],
  recordCount: 2, bytes: 120, createdAt: '2026-11-04T10:00:00Z', updatedAt: '2026-11-04T10:00:00Z',
};
const limits = { maxUploadBytes: 20 * 1024 * 1024, maxRecords: 100000, maxRecordBytes: 65536, maxDatasets: 100, maxIdentity: 3, maxIndexes: 5 };
let role = 'editor';
let datasets: unknown[] = [customers];
const createDataset = vi.fn((_o: string, body: { key: string }) => Promise.resolve({ ...customers, key: body.key, recordCount: 0 }));
const uploadDataset = vi.fn((..._a: unknown[]) => Promise.resolve({ added: 2, updated: 0, skipped: 0, dataset: customers }));
const datasetRecords = vi.fn((_o: string, _k: string, params: { filters?: Record<string, string> }) =>
  Promise.resolve(params.filters?.city === 'Paris'
    ? { total: 0, records: [] }
    : { total: 2, records: [{ id: 1, name: 'Ada', city: 'London' }, { id: 2, name: 'Alan', city: 'Wilmslow' }] }));
const deleteDataset = vi.fn((..._a: unknown[]) => Promise.resolve());

vi.mock('../../state/AppContext', () => ({
  atLeast: (r: string, needed: string) => ['none', 'viewer', 'editor', 'manager', 'admin'].indexOf(r) >= ['none', 'viewer', 'editor', 'manager', 'admin'].indexOf(needed),
  useApp: () => ({ currentOrg: { id: 'o1', name: 'Retail' }, roleIn: () => role }),
}));
vi.mock('../../api/client', () => ({
  api: {
    datasets: () => Promise.resolve({ datasets, limits }),
    createDataset: (o: string, body: { key: string }) => createDataset(o, body),
    uploadDataset: (...a: unknown[]) => uploadDataset(...a),
    datasetRecords: (o: string, k: string, p: { filters?: Record<string, string> }) => datasetRecords(o, k, p),
    deleteDataset: (...a: unknown[]) => deleteDataset(...a),
    updateDataset: vi.fn(),
  },
}));

const { DatasetsPage } = await import('./DatasetsPage');

describe('DatasetsPage', () => {
  beforeEach(() => {
    role = 'editor';
    datasets = [customers];
    createDataset.mockClear();
    uploadDataset.mockClear();
    datasetRecords.mockClear();
  });

  it('shows a dataset with its columns, records and helpers to copy', async () => {
    // When
    renderWithShell(<DatasetsPage />);

    // Then
    expect(await screen.findByText('Ada')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Customers' })).toBeInTheDocument();
    expect(screen.getByText(/identified by/)).toBeInTheDocument();
    expect(screen.getByText("{{{dataset-one 'customers' id=request.path.id}}}")).toBeInTheDocument();
    expect(screen.getByText('1–2 of 2')).toBeInTheDocument();
  });

  it('previews what a lookup returns', async () => {
    // Given
    renderWithShell(<DatasetsPage />);
    await screen.findByText('Ada');

    // When
    await userEvent.selectOptions(screen.getByLabelText('Field 1'), 'city');
    await userEvent.type(screen.getByLabelText('Value 1'), 'Paris');
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }));

    // Then
    expect(screen.getByText("{{{dataset-one 'customers' city='Paris'}}}")).toBeInTheDocument();
    expect(await screen.findByLabelText('Lookup result')).toHaveTextContent('Nothing found');
    expect(datasetRecords).toHaveBeenLastCalledWith('o1', 'customers', { filters: { city: 'Paris' }, limit: 1 });
  });

  it('uploads a file in the chosen mode', async () => {
    // Given
    renderWithShell(<DatasetsPage />);
    await screen.findByText('Ada');
    await userEvent.click(screen.getByRole('button', { name: /Upload/ }));
    const dialog = screen.getByRole('dialog');

    // When
    await userEvent.upload(within(dialog).getByLabelText(/^File/), new File(['id,name\n3,Grace\n'], 'more.csv', { type: 'text/csv' }));
    await userEvent.click(within(dialog).getByText('Add only'));
    await userEvent.click(within(dialog).getByRole('button', { name: /Upload/ }));

    // Then
    await waitFor(() => expect(uploadDataset).toHaveBeenCalledWith('o1', 'customers', 'csv', 'add', expect.any(File)));
  });

  it('creates the first dataset from the empty page', async () => {
    // Given
    datasets = [];
    renderWithShell(<DatasetsPage />);
    await userEvent.click(await screen.findByRole('button', { name: /New dataset/ }));

    // When
    await userEvent.type(screen.getByLabelText(/^Name/), 'Product Catalogue');
    await userEvent.click(screen.getByRole('button', { name: 'Create dataset' }));

    // Then
    await waitFor(() => expect(createDataset).toHaveBeenCalledWith('o1', expect.objectContaining({ key: 'product-catalogue', name: 'Product Catalogue', identity: ['id'] })));
  });

  it('lets viewers browse but not change datasets', async () => {
    // Given
    role = 'viewer';

    // When
    renderWithShell(<DatasetsPage />);

    // Then
    expect(await screen.findByText('Ada')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Upload/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete customers' })).not.toBeInTheDocument();
  });
});
