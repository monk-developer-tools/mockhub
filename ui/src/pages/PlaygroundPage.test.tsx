import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { PlaygroundEntry } from '../api/types';
import { renderWithShell } from '../test/render';

const sent: PlaygroundEntry = {
  id: 'e1',
  at: new Date().toISOString(),
  request: { method: 'GET', url: 'https://api.example.com/users', headers: [{ name: 'Accept', value: 'application/json' }], body: '' },
  response: { status: 201, headers: [{ name: 'Content-Type', value: 'application/json' }], body: '{"id":1}', size: 8, truncated: false, durationMs: 42 },
  error: null,
  local: false,
};
const old: PlaygroundEntry = { ...sent, id: 'e0', request: { ...sent.request, url: 'https://api.example.com/old' }, response: { ...sent.response!, status: 404 } };

const sendPlayground = vi.fn((..._a: unknown[]) => Promise.resolve(sent));
const playgroundHistory = vi.fn(() => Promise.resolve([old]));
const deletePlaygroundEntry = vi.fn((..._a: unknown[]) => Promise.resolve());
vi.mock('../api/client', () => ({
  api: {
    sendPlayground: (...a: unknown[]) => sendPlayground(...a),
    playgroundHistory: () => playgroundHistory(),
    deletePlaygroundEntry: (...a: unknown[]) => deletePlaygroundEntry(...a),
    clearPlaygroundHistory: vi.fn(),
  },
}));
vi.mock('../state/AppContext', () => ({ useApp: () => ({ orgProjects: [], roleInTeam: () => 'editor', refreshProjects: vi.fn() }) }));

const { PlaygroundPage } = await import('./PlaygroundPage');

describe('PlaygroundPage', () => {
  beforeEach(() => {
    sendPlayground.mockClear();
    deletePlaygroundEntry.mockClear();
  });

  it('sends a request and shows the response with status, time and size', async () => {
    // Given
    renderWithShell(<PlaygroundPage />);
    const url = screen.getByLabelText('URL');

    // When
    await userEvent.clear(url);
    await userEvent.type(url, 'https://api.example.com/users');
    await userEvent.click(screen.getByRole('button', { name: /Send/ }));

    // Then
    expect(sendPlayground).toHaveBeenCalledWith({ method: 'GET', url: 'https://api.example.com/users', headers: [{ name: 'Accept', value: 'application/json' }], body: '' });
    expect(await screen.findByText('42 ms')).toBeInTheDocument();
    expect(screen.getByText('8 B')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Save as stub/ })).toBeInTheDocument();
  });

  it('lists history, reopens an entry and deletes it', async () => {
    // Given
    renderWithShell(<PlaygroundPage />);

    // When
    await userEvent.click(await screen.findByText('/old'));

    // Then
    expect(screen.getByLabelText('URL')).toHaveValue('https://api.example.com/old');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Delete GET https://api.example.com/old from history' }));

    // Then
    await waitFor(() => expect(deletePlaygroundEntry).toHaveBeenCalledWith('e0'));
    expect(screen.queryByText('/old')).not.toBeInTheDocument();
  });

  it('edits query parameters as rows', async () => {
    // Given
    renderWithShell(<PlaygroundPage />);
    const url = screen.getByLabelText('URL');
    await userEvent.clear(url);
    await userEvent.type(url, 'https://a.example/x?page=2');

    // When
    await userEvent.clear(screen.getByLabelText('Parameter 1 value'));
    await userEvent.type(screen.getByLabelText('Parameter 1 value'), '3');

    // Then
    expect(url).toHaveValue('https://a.example/x?page=3');
  });
});
