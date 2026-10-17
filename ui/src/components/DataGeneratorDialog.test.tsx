import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const generateData = vi.fn((..._a: unknown[]) => Promise.resolve({ records: [{ id: 1, name: 'Ada' }] }));
vi.mock('../api/client', () => ({ api: { generateData: (...a: unknown[]) => generateData(...a) } }));

const { DataGeneratorDialog } = await import('./DataGeneratorDialog');

describe('DataGeneratorDialog', () => {
  it('generates records with the chosen fields and wraps them', async () => {
    // Given
    const onUse = vi.fn();
    renderWithShell(<DataGeneratorDialog onUse={onUse} onClose={vi.fn()} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Remove field 4' }));
    await userEvent.clear(screen.getByLabelText('Records'));
    await userEvent.type(screen.getByLabelText('Records'), '3');
    await userEvent.type(screen.getByLabelText(/Wrap in/), 'items');
    await userEvent.click(screen.getByRole('button', { name: /^Generate$/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Use this data' }));

    // Then
    expect(generateData).toHaveBeenCalledWith({
      fields: [{ name: 'id', expression: 'sequence' }, { name: 'name', expression: 'Name.fullName' }, { name: 'email', expression: 'Internet.emailAddress' }],
      count: 3,
      locale: undefined,
    });
    await waitFor(() => expect(JSON.parse(onUse.mock.calls[0][0])).toEqual({ items: [{ id: 1, name: 'Ada' }], total: 1 }));
  });

  it('accepts a custom Datafaker expression', async () => {
    // Given
    generateData.mockClear();
    renderWithShell(<DataGeneratorDialog onUse={vi.fn()} onClose={vi.fn()} />);

    // When
    await userEvent.selectOptions(screen.getByLabelText('Field 2 type'), 'Custom Datafaker expression…');
    await userEvent.type(screen.getByLabelText('Field 2 expression'), 'Animal.name');
    await userEvent.click(screen.getByRole('button', { name: /^Generate$/ }));

    // Then
    await waitFor(() => expect((generateData.mock.calls[0][0] as { fields: unknown[] }).fields[1]).toEqual({ name: 'name', expression: 'Animal.name' }));
  });
});
