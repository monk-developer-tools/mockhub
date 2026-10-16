import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';
import { FakeDataMenu } from './FakeDataMenu';

describe('FakeDataMenu', () => {
  it('copies the chosen helper and turns templating on', async () => {
    // Given
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const onPick = vi.fn();
    renderWithShell(<FakeDataMenu onPick={onPick} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Fake data/ }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'E-mail' }));

    // Then
    expect(onPick).toHaveBeenCalled();
    await waitFor(() => expect(writeText).toHaveBeenCalledWith("{{fake 'Internet.emailAddress'}}"));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
