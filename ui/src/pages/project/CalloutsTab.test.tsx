import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import type { CalloutEntry } from '../../api/types';
import { renderWithShell } from '../../test/render';

const entries: CalloutEntry[] = [
  {
    id: 'a', at: '2026-11-03T10:00:00Z', projectId: 'p1', stubId: 's1', stubName: 'Take payment', mode: 'async',
    trigger: { method: 'POST', url: '/pay/payments' },
    request: { method: 'POST', url: 'https://partner.test/cb', headers: [{ name: 'Content-Type', value: 'application/json' }], body: '{"id":"pay_1"}' },
    response: { status: 204, headers: [], body: '', truncated: false }, error: null, durationMs: 31, requestId: null,
  },
  {
    id: 'b', at: '2026-11-03T10:00:05Z', projectId: 'p1', stubId: 's2', stubName: 'Check card', mode: 'sync',
    trigger: { method: 'POST', url: '/pay/check' },
    request: { method: 'POST', url: 'http://10.0.0.5/auth', headers: [], body: '' },
    response: null, error: '10.0.0.5 is a private or local address', durationMs: 2, requestId: null,
  },
];
const clearCallouts = vi.fn(() => Promise.resolve());
vi.mock('../../api/client', () => ({
  api: { callouts: () => Promise.resolve(entries), clearCallouts: () => clearCallouts() },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ canEdit: true, aiStatus: { enabled: true } }) }));
vi.mock('./ProjectPage', () => ({ useProject: () => ({ project: { id: 'p1', basePath: '/pay', mockUrl: 'http://h/pay' }, reloadProject: vi.fn() }) }));

const { CalloutsTab } = await import('./CalloutsTab');

function AssistantProbe() {
  const location = useLocation();
  return <p data-testid="prompt">{(location.state as { prompt?: string } | null)?.prompt}</p>;
}

function Harness() {
  return (
    <Routes>
      <Route path="/projects/p1/callouts" element={<CalloutsTab />} />
      <Route path="/projects/p1/assistant" element={<AssistantProbe />} />
    </Routes>
  );
}

describe('CalloutsTab', () => {
  it('lists callouts with their result and filters the failed ones', async () => {
    // Given
    renderWithShell(<Harness />, '/projects/p1/callouts');

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getByText('204')).toBeInTheDocument();
    expect(within(rows[2]).getByText('failed')).toBeInTheDocument();

    // When
    await userEvent.click(screen.getByRole('button', { name: /^Failed/ }));

    // Then
    expect(screen.getAllByRole('row')).toHaveLength(2);
    expect(screen.getByText('Check card')).toBeInTheDocument();
  });

  it('shows what was sent and hands a failed callout to the assistant', async () => {
    // Given
    renderWithShell(<Harness />, '/projects/p1/callouts');
    await userEvent.click(await screen.findByText('Check card'));

    // When
    const drawer = screen.getByRole('complementary', { name: 'Callout details' });
    await userEvent.click(within(drawer).getByRole('button', { name: /Ask the assistant to fix it/ }));

    // Then
    expect(screen.getByTestId('prompt')).toHaveTextContent('wait-for-callout from "Check card" to POST http://10.0.0.5/auth failed with: 10.0.0.5 is a private or local address');
  });

  it('clears the log', async () => {
    // Given
    renderWithShell(<Harness />, '/projects/p1/callouts');
    await screen.findByText('Take payment');

    // When
    await userEvent.click(screen.getByRole('button', { name: /Clear log/ }));

    // Then
    expect(clearCallouts).toHaveBeenCalled();
  });
});
