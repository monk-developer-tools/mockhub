import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';
import { DynamicValueMenu } from './DynamicValueMenu';

describe('DynamicValueMenu', () => {
  it('opens a category and inserts the chosen helper', async () => {
    // Given
    const onInsert = vi.fn();
    renderWithShell(<DynamicValueMenu scope="response" onInsert={onInsert} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Insert dynamic value/ }));
    await userEvent.click(screen.getByRole('button', { name: /Faker/ }));
    await userEvent.click(screen.getByRole('button', { name: /E-mail/ }));

    // Then
    expect(onInsert).toHaveBeenCalledWith("{{fake 'Internet.emailAddress'}}");
    expect(screen.queryByRole('dialog', { name: 'Dynamic values' })).not.toBeInTheDocument();
  });

  it('filters by search and shows callout-only response components', async () => {
    // Given
    const onInsert = vi.fn();
    renderWithShell(<DynamicValueMenu scope="callout" onInsert={onInsert} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Insert dynamic value/ }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search dynamic values' }), 'mock response status');

    // Then
    expect(screen.getByRole('list', { name: 'Response components' })).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Faker' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Mock response status/ }));
    expect(onInsert).toHaveBeenCalledWith('{{originalResponse.status}}');
  });

  it('closes with Escape', async () => {
    // Given
    renderWithShell(<DynamicValueMenu scope="response" onInsert={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: /Insert dynamic value/ }));

    // When
    await userEvent.keyboard('{Escape}');

    // Then
    expect(screen.queryByRole('dialog', { name: 'Dynamic values' })).not.toBeInTheDocument();
  });
});
