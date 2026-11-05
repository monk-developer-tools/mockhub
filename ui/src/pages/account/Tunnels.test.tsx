import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { TunnelView } from '../../model/tunnels';
import { renderWithShell } from '../../test/render';

let isAdmin = false;
const laptop: TunnelView = {
  name: 'laptop', owner: 'alex', url: 'tunnel://laptop', online: true, connectedAt: '2026-11-05T10:00:00Z', lastSeenAt: '2026-11-05T10:00:05Z',
  requests: 12, failed: 2, waiting: 0, bytesOut: 2048, bytesIn: 512,
};
let tunnels: TunnelView[] = [laptop];
const closeTunnel = vi.fn((..._a: unknown[]) => Promise.resolve());

vi.mock('../../api/client', async (original) => ({
  ...(await original<typeof import('../../api/client')>()),
  api: {
    tunnels: () => Promise.resolve(tunnels),
    closeTunnel: (...a: unknown[]) => closeTunnel(...a),
  },
}));
vi.mock('../../state/AppContext', () => ({
  useApp: () => ({ session: { isAdmin } }),
}));

const { Tunnels } = await import('./Tunnels');

describe('Tunnels', () => {
  beforeEach(() => {
    isAdmin = false;
    tunnels = [laptop];
    closeTunnel.mockClear();
  });

  it('lists connected tunnels with their traffic', async () => {
    // When
    renderWithShell(<Tunnels />);

    // Then
    const row = within(await screen.findByRole('list', { name: 'Tunnels' })).getByRole('listitem');
    expect(row).toHaveTextContent('tunnel://laptop');
    expect(row).toHaveTextContent('online');
    expect(row).toHaveTextContent('12 requests, 2 failed');
    expect(row).toHaveTextContent('2.0 KB sent');
    expect(row).not.toHaveTextContent('alex');
  });

  it('shows the owner to admins', async () => {
    // Given
    isAdmin = true;

    // When
    renderWithShell(<Tunnels />);

    // Then
    expect(await screen.findByRole('list', { name: 'Tunnels' })).toHaveTextContent('alex');
  });

  it('shows how to start a tunnel when none is connected', async () => {
    // Given
    tunnels = [];

    // When
    renderWithShell(<Tunnels />);

    // Then
    expect(await screen.findByText('No tunnels connected.')).toBeInTheDocument();
    expect(screen.getByText(/java -jar mockhub.jar tunnel --server/)).toBeInTheDocument();
  });

  it('closes a tunnel', async () => {
    // Given
    renderWithShell(<Tunnels />);

    // When
    await userEvent.click(await screen.findByRole('button', { name: 'Close laptop' }));

    // Then
    await waitFor(() => expect(closeTunnel).toHaveBeenCalledWith('laptop'));
  });
});
