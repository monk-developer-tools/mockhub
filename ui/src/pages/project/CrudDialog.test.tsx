import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const createCrud = vi.fn((..._a: unknown[]) => Promise.resolve({ seeded: 2, stub: {} }));
vi.mock('../../api/client', () => ({ api: { createCrud: (...a: unknown[]) => createCrud(...a) } }));

const { CrudDialog } = await import('./CrudDialog');

describe('CrudDialog', () => {
  beforeEach(() => createCrud.mockClear());

  it('previews the endpoints and creates the resource with starting records', async () => {
    // Given
    const onCreated = vi.fn();
    renderWithShell(<CrudDialog projectId="p1" onClose={vi.fn()} onCreated={onCreated} />);

    // When
    await userEvent.type(screen.getByLabelText(/^Resource/), 'Customers');
    await userEvent.click(screen.getByRole('button', { name: 'Use example records' }));
    await userEvent.click(screen.getByRole('button', { name: 'UUID' }));
    await userEvent.click(screen.getByRole('button', { name: /Create resource/ }));

    // Then
    expect(screen.getAllByText('/customers/{id}', { selector: 'code' })).toHaveLength(4);
    await waitFor(() => expect(onCreated).toHaveBeenCalledWith('customers'));
    expect(createCrud).toHaveBeenCalledWith('p1', expect.objectContaining({ resource: 'customers', idField: 'id', idType: 'uuid' }));
    expect((createCrud.mock.calls[0][1] as { seed: unknown[] }).seed).toHaveLength(2);
  });
});
