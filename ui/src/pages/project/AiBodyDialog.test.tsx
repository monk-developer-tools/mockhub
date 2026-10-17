import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const generateBody = vi.fn((..._a: unknown[]) => Promise.resolve({ body: '{\n  "id" : "ord_1"\n}' }));
vi.mock('../../api/client', () => ({ api: { generateBody: (...a: unknown[]) => generateBody(...a) } }));

const { AiBodyDialog } = await import('./AiBodyDialog');

describe('AiBodyDialog', () => {
  beforeEach(() => generateBody.mockClear());

  it('generates a body from an idea and hands it back', async () => {
    // Given
    const onUse = vi.fn();
    renderWithShell(<AiBodyDialog projectId="p1" format="json" method="GET" path="/orders" status={200} current="" onUse={onUse} onClose={vi.fn()} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /A page of 3 orders/ }));
    await userEvent.click(screen.getByRole('button', { name: /Generate/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Use this body' }));

    // Then
    expect(generateBody).toHaveBeenCalledWith('p1', expect.objectContaining({ format: 'json', method: 'GET', path: '/orders', status: 200, current: '' }));
    await waitFor(() => expect(onUse).toHaveBeenCalledWith('{\n  "id" : "ord_1"\n}'));
  });

  it('sends the current body only when asked to build on it', async () => {
    // Given
    renderWithShell(<AiBodyDialog projectId="p1" format="json" method="GET" path="/a" status={200} current='{"a":1}' onUse={vi.fn()} onClose={vi.fn()} />);

    // When
    await userEvent.type(screen.getByLabelText('What should it return?'), 'add a field');
    await userEvent.click(screen.getByLabelText(/Change the current body/));
    await userEvent.click(screen.getByRole('button', { name: /Generate/ }));

    // Then
    await waitFor(() => expect(generateBody).toHaveBeenCalledWith('p1', expect.objectContaining({ description: 'add a field', current: '{"a":1}' })));
  });

  it('shows errors from the provider', async () => {
    // Given
    generateBody.mockRejectedValueOnce(new Error('The AI provider returned no answer'));
    renderWithShell(<AiBodyDialog projectId="p1" format="xml" method="POST" path="/ws" status={200} current="" onUse={vi.fn()} onClose={vi.fn()} />);

    // When
    await userEvent.type(screen.getByLabelText('What should it return?'), 'a balance');
    await userEvent.click(screen.getByRole('button', { name: /Generate/ }));

    // Then
    expect(await screen.findByRole('alert')).toHaveTextContent('The AI provider returned no answer');
  });
});
