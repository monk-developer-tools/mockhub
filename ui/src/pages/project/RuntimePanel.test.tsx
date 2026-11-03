import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { RuntimeSettings } from '../../api/types';
import { EMPTY_RUNTIME } from '../../model/runtime';
import { renderWithShell } from '../../test/render';

const updateRuntime = vi.fn((_id: string, body: RuntimeSettings) => Promise.resolve(body));
let canEdit = true;
vi.mock('../../api/client', () => ({
  api: { runtime: () => Promise.resolve(EMPTY_RUNTIME), updateRuntime: (id: string, body: RuntimeSettings) => updateRuntime(id, body) },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ canEdit }) }));
vi.mock('./ProjectPage', () => ({ useProject: () => ({ project: { id: 'p1', basePath: '/pay', mockUrl: 'http://h/pay' } }) }));

const { RuntimePanel } = await import('./RuntimePanel');

describe('RuntimePanel', () => {
  beforeEach(() => {
    canEdit = true;
    updateRuntime.mockClear();
  });

  it('saves a rate limit and a fixed delay together', async () => {
    // Given
    renderWithShell(<RuntimePanel />);
    await userEvent.click(await screen.findByRole('switch', { name: 'Rate limit' }));
    const requests = screen.getByLabelText('Requests');
    await userEvent.clear(requests);
    await userEvent.type(requests, '5');
    await userEvent.selectOptions(screen.getByLabelText('Period'), 'second');
    await userEvent.click(screen.getByRole('button', { name: 'Fixed' }));

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    // Then
    expect(updateRuntime).toHaveBeenCalledWith('p1', expect.objectContaining({
      rateLimit: { requests: 5, per: 'second' },
      latency: { mode: 'fixed', fixedMs: 300, load: null },
      defaultResponse: null,
      cors: null,
    }));
    expect(await screen.findByText('Runtime behaviour saved')).toBeInTheDocument();
  });

  it('only offers saving once something changed and can discard it', async () => {
    // Given
    renderWithShell(<RuntimePanel />);
    await screen.findByRole('switch', { name: 'Default response' });
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Fail on purpose' }));
    await userEvent.click(screen.getByRole('button', { name: 'Discard' }));

    // Then
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Server default' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('is read-only for viewers', async () => {
    // Given
    canEdit = false;

    // When
    renderWithShell(<RuntimePanel />);

    // Then
    expect(await screen.findByRole('switch', { name: 'Rate limit' })).toBeDisabled();
    expect(screen.getByText(/only editors can change these/)).toBeInTheDocument();
  });
});
