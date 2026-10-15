import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { AuthInfo } from '../api/types';
import { renderWithShell } from '../test/render';

const calls: [string, string][] = [];
let outcome: () => Promise<void> = () => Promise.resolve();
let authInfo: AuthInfo = { authEnabled: true, loginNote: 'Sign in as demo / demo', passwordLogin: true, sso: { enabled: false } };
let loginError = '';
const signIn = (username: string, password: string) => {
  calls.push([username, password]);
  return outcome();
};
vi.mock('../state/AppContext', () => ({ useApp: () => ({ signIn, authInfo, loginError }) }));
vi.mock('../api/client', () => ({ ApiError: class extends Error { status = 0; } }));

const { LoginPage } = await import('./LoginPage');

async function submit(username: string, password: string) {
  await userEvent.type(screen.getByLabelText('Username'), username);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  beforeEach(() => {
    calls.length = 0;
    loginError = '';
    authInfo = { authEnabled: true, loginNote: 'Sign in as demo / demo', passwordLogin: true, sso: { enabled: false } };
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

  it("shows the server's sign-in note", () => {
    // When
    renderWithShell(<LoginPage />);

    // Then
    expect(screen.getByText('Sign in as demo / demo')).toBeInTheDocument();
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

  it('offers Microsoft sign-in next to passwords', () => {
    // Given
    authInfo = { authEnabled: true, passwordLogin: true, sso: { enabled: true, label: 'Sign in with Contoso', startUrl: '/start' } };

    // When
    renderWithShell(<LoginPage />);

    // Then
    expect(screen.getByRole('link', { name: 'Sign in with Contoso' })).toHaveAttribute('href', '/start');
    expect(screen.getByLabelText('Username')).toBeInTheDocument();
  });

  it('hides the password form when only Microsoft sign-in is allowed', async () => {
    // Given
    authInfo = { authEnabled: true, passwordLogin: false, sso: { enabled: true, label: 'Sign in with Microsoft', startUrl: '/start' } };
    renderWithShell(<LoginPage />);
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument();

    // When
    await userEvent.click(screen.getByRole('button', { name: /Administrator\? Sign in with a password/ }));

    // Then
    expect(screen.getByLabelText('Username')).toBeInTheDocument();
  });

  it('shows why a Microsoft sign-in failed', () => {
    // Given
    loginError = 'Accounts from this e-mail domain are not allowed';

    // When
    renderWithShell(<LoginPage />);

    // Then
    expect(screen.getByRole('alert')).toHaveTextContent('e-mail domain');
  });
});
