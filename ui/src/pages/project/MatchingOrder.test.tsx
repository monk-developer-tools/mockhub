import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { Project, Stub } from '../../api/types';
import { renderWithShell } from '../../test/render';

const reorderStubs = vi.fn((_id: string, _ids: string[]) => Promise.resolve([]));
vi.mock('../../api/client', () => ({
  api: { matchingOrder: () => Promise.resolve(['a', 'b', 'c']), reorderStubs: (id: string, ids: string[]) => reorderStubs(id, ids) },
}));

const { MatchingOrder } = await import('./MatchingOrder');

const project = { id: 'p1', basePath: '/shop', runtime: { defaultResponse: { status: 418, headers: {}, body: '', templated: false } } } as unknown as Project;
const stubs: Stub[] = [
  { id: 'a', name: 'Any item', request: { method: 'GET', urlPathPattern: '/items/.*' }, response: { status: 200 } },
  { id: 'b', name: 'Special item', request: { method: 'GET', urlPath: '/items/special' }, response: { status: 200 } },
  { id: 'c', name: 'Create item', request: { method: 'POST', urlPath: '/items' }, response: { status: 201 } },
];

describe('MatchingOrder', () => {
  it('lists stubs in the order WireMock tries them and says what answers last', async () => {
    // Given
    renderWithShell(<MatchingOrder project={project} stubs={stubs} canEdit={false} onSelect={vi.fn()} onSaved={vi.fn()} />);

    // When
    const rows = within(await screen.findByRole('list', { name: 'Matching order' })).getAllByRole('listitem');

    // Then
    expect(rows.map((r) => r.textContent)).toEqual([expect.stringContaining('Any item'), expect.stringContaining('Special item'), expect.stringContaining('Create item')]);
    expect(screen.getByText(/default response/).parentElement).toHaveTextContent('answers 418');
    expect(screen.queryByRole('button', { name: /Move/ })).not.toBeInTheDocument();
  });

  it('moves a stub up and saves the new order', async () => {
    // Given
    const onSaved = vi.fn();
    renderWithShell(<MatchingOrder project={project} stubs={stubs} canEdit onSelect={vi.fn()} onSaved={onSaved} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Move Special item up' }));

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Save order' }));

    // Then
    expect(reorderStubs).toHaveBeenCalledWith('p1', ['b', 'a', 'c']);
    expect(onSaved).toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Save order' })).not.toBeInTheDocument();
  });
});
