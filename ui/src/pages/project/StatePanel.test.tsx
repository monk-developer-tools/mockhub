import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const saveStateContext = vi.fn((..._a: unknown[]) => Promise.resolve({}));
const deleteStateContext = vi.fn((..._a: unknown[]) => Promise.resolve());
const clearState = vi.fn((..._a: unknown[]) => Promise.resolve());
vi.mock('../../api/client', () => ({
  api: {
    saveStateContext: (...a: unknown[]) => saveStateContext(...a),
    deleteStateContext: (...a: unknown[]) => deleteStateContext(...a),
    clearState: (...a: unknown[]) => clearState(...a),
  },
}));

const { StatePanel } = await import('./StatePanel');

const contexts = [
  { name: 'cart-1', properties: { status: 'open' }, list: [{ sku: 'a' }, { sku: 'b' }], updateCount: 3, updatedAt: '2026-11-04T10:00:00Z' },
  { name: 'cart-2', properties: { status: 'paid' }, list: [], updateCount: 1, updatedAt: '2026-11-04T10:00:00Z' },
];

describe('StatePanel', () => {
  beforeEach(() => {
    saveStateContext.mockClear();
    deleteStateContext.mockClear();
    clearState.mockClear();
  });

  it('lists contexts with their properties, list size and update count', () => {
    // When
    renderWithShell(<StatePanel projectId="p1" contexts={contexts} canEdit onChanged={vi.fn()} />);

    // Then
    const row = screen.getByRole('row', { name: /cart-1/ });
    expect(within(row).getByText('status: open')).toBeInTheDocument();
    expect(within(row).getByText('2')).toBeInTheDocument();
    expect(within(row).getByText('3')).toBeInTheDocument();
  });

  it('searches and deletes a context', async () => {
    // Given
    const onChanged = vi.fn();
    renderWithShell(<StatePanel projectId="p1" contexts={contexts} canEdit onChanged={onChanged} />);

    // When
    await userEvent.type(screen.getByLabelText('Search state'), 'paid');
    await userEvent.click(screen.getByRole('button', { name: 'Delete cart-2' }));

    // Then
    expect(screen.queryByText('cart-1')).not.toBeInTheDocument();
    await waitFor(() => expect(deleteStateContext).toHaveBeenCalledWith('p1', 'cart-2'));
    expect(onChanged).toHaveBeenCalled();
  });

  it('saves an edited context', async () => {
    // Given
    renderWithShell(<StatePanel projectId="p1" contexts={contexts} canEdit onChanged={vi.fn()} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Edit cart-1' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Save state' }));

    // Then
    await waitFor(() => expect(saveStateContext).toHaveBeenCalledWith('p1', 'cart-1', { properties: { status: 'open' }, list: [{ sku: 'a' }, { sku: 'b' }] }));
  });

  it('asks for a name before adding a context', async () => {
    // Given
    renderWithShell(<StatePanel projectId="p1" contexts={contexts} canEdit onChanged={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: /Add context/ }));
    const dialog = screen.getByRole('dialog');

    // When
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add state' }));

    // Then
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Give the context a name');
    expect(saveStateContext).not.toHaveBeenCalled();

    // When
    await userEvent.type(within(dialog).getByLabelText('Context name'), 'cart-9');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add state' }));

    // Then
    await waitFor(() => expect(saveStateContext).toHaveBeenCalledWith('p1', 'cart-9', { properties: {}, list: [] }));
  });

  it('clears all state after confirming', async () => {
    // Given
    renderWithShell(<StatePanel projectId="p1" contexts={contexts} canEdit onChanged={vi.fn()} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete all' }));

    // Then
    await waitFor(() => expect(clearState).toHaveBeenCalledWith('p1'));
  });

  it('hides changes from viewers and explains where state comes from', () => {
    // When
    renderWithShell(<StatePanel projectId="p1" contexts={[]} canEdit={false} onChanged={vi.fn()} />);

    // Then
    expect(screen.getByText(/Stubs that record state/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Add context/ })).not.toBeInTheDocument();
  });
});
