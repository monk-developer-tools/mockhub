import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const calls: [string, string][] = [];
let outcome: () => Promise<void> = () => Promise.resolve();
const signIn = (username: string, password: string) => {
  calls.push([username, password]);
  return outcome();
};
vi.mock('../state/AppContext', () => ({ useApp: () => ({ signIn }) }));

const { LoginPage } = await import('./LoginPage');

async function submit(username: string, password: string) {
  await userEvent.type(screen.getByLabelText('Username'), username);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  beforeEach(() => {
    calls.length = 0;
  });

  it('signs in with the entered credentials', async () => {
    // Given
    outcome = () => Promise.resolve();
    renderWithShell(<LoginPage />);

    // When
    await submit('qa', 'secret');

    // Then
    expect(calls).toEqual([['qa', 'secret']]);
  });

  it('explains a failed sign in', async () => {
    // Given
    outcome = async () => {
      throw new Error('401');
    };
    renderWithShell(<LoginPage />);

    // When
    await submit('qa', 'nope');

    // Then
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Wrong username or password.'));
  });
});
