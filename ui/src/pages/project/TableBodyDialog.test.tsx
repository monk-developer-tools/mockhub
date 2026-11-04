import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';
import { tableOf } from '../../model/tableBody';

const createDataset = vi.fn((..._a: unknown[]) => Promise.resolve({}));
const uploadDataset = vi.fn((..._a: unknown[]) => Promise.resolve({}));
vi.mock('../../api/client', () => ({
  api: {
    createDataset: (...a: unknown[]) => createDataset(...a),
    uploadDataset: (...a: unknown[]) => uploadDataset(...a),
  },
}));

const { TableBodyDialog } = await import('./TableBodyDialog');

const body = '{"data":[{"id":1,"name":"Rex"},{"id":2,"name":"Tom"}],"total":2}';

describe('TableBodyDialog', () => {
  beforeEach(() => {
    createDataset.mockClear();
    uploadDataset.mockClear();
  });

  it('edits cells, adds and removes rows and writes the body back', async () => {
    // Given
    const onUse = vi.fn();
    renderWithShell(<TableBodyDialog orgId="o1" text={body} table={tableOf(body)!} url="/pets" param={null} onClose={vi.fn()} onUse={onUse} onBind={vi.fn()} />);

    // When
    const name = screen.getByLabelText('Row 1 name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Rexy');
    await userEvent.click(screen.getByRole('button', { name: 'Remove row 2' }));
    await userEvent.click(screen.getByRole('button', { name: /Row$/ }));
    await userEvent.type(screen.getByLabelText('Row 2 id'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Use as response body' }));

    // Then
    expect(JSON.parse(onUse.mock.calls[0][0])).toEqual({ data: [{ id: 1, name: 'Rexy' }, { id: 3, name: null }], total: 2 });
  });

  it('moves the rows to a dataset and reads them back with a helper', async () => {
    // Given
    const onBind = vi.fn();
    renderWithShell(<TableBodyDialog orgId="o1" text={body} table={tableOf(body)!} url="/pets/{petId}" param="petId" onClose={vi.fn()} onUse={vi.fn()} onBind={onBind} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Move the rows to a dataset/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Create dataset and use it' }));

    // Then
    await waitFor(() => expect(onBind).toHaveBeenCalled());
    expect(createDataset).toHaveBeenCalledWith('o1', { key: 'pets', name: 'pets', identity: ['id'] });
    expect(uploadDataset).toHaveBeenCalledWith('o1', 'pets', 'json', 'replace', expect.any(Blob));
    expect(onBind.mock.calls[0][0]).toContain(`"data": {{{dataset-one 'pets' id=request.path.petId}}}`);
  });
});
