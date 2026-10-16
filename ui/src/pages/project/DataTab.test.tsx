import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

let resources = [{ name: 'customers', count: 2, idField: 'id', idType: 'number', crud: true }];
const deleteRecord = vi.fn((..._a: unknown[]) => Promise.resolve());
const addRecord = vi.fn((..._a: unknown[]) => Promise.resolve({}));
vi.mock('../../api/client', () => ({
  api: {
    dataResources: () => Promise.resolve({ resources }),
    dataRecords: () => Promise.resolve([{ id: 1, name: 'Ada', city: 'London' }, { id: 2, name: 'Alan', city: 'Wilmslow' }]),
    deleteRecord: (...a: unknown[]) => deleteRecord(...a),
    addRecord: (...a: unknown[]) => addRecord(...a),
  },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ canEdit: true }) }));
vi.mock('./ProjectPage', () => ({
  useProject: () => ({ project: { id: 'p1', basePath: '/shop', mockUrl: 'http://h/shop' }, reloadProject: vi.fn() }),
}));

const { DataTab } = await import('./DataTab');

describe('DataTab', () => {
  beforeEach(() => {
    resources = [{ name: 'customers', count: 2, idField: 'id', idType: 'number', crud: true }];
    deleteRecord.mockClear();
  });

  it('shows the records of a resource as a table and the endpoints to call', async () => {
    // When
    renderWithShell(<DataTab />);

    // Then
    expect(await screen.findByText('Ada')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'city' })).toBeInTheDocument();
    expect(screen.getAllByText('/shop/customers/{id}', { selector: 'code' })).toHaveLength(4);
  });

  it('searches and deletes records', async () => {
    // Given
    renderWithShell(<DataTab />);
    await screen.findByText('Ada');

    // When
    await userEvent.type(screen.getByLabelText('Search records'), 'wilm');
    await userEvent.click(screen.getByRole('button', { name: 'Delete 2' }));

    // Then
    expect(screen.queryByText('Ada')).not.toBeInTheDocument();
    await waitFor(() => expect(deleteRecord).toHaveBeenCalledWith('p1', 'customers', '2'));
  });

  it('adds a record from JSON', async () => {
    // Given
    renderWithShell(<DataTab />);
    await screen.findByText('Ada');

    // When
    await userEvent.click(screen.getByRole('button', { name: /Add record/ }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Add record' }));

    // Then
    await waitFor(() => expect(addRecord).toHaveBeenCalledWith('p1', 'customers', {}));
  });

  it('invites people to create a resource when there is no data', async () => {
    // Given
    resources = [];

    // When
    renderWithShell(<DataTab />);

    // Then
    expect(await screen.findByRole('heading', { name: 'No data yet' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /New CRUD resource/ })).toBeInTheDocument();
  });
});
