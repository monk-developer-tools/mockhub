import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { ProxyView } from '../model/proxy';
import { renderWithShell } from '../test/render';

let view: ProxyView;
const setProxyProject = vi.fn();
vi.mock('../api/client', () => ({
  api: { proxy: () => Promise.resolve(view), setProxyProject: (id: string | null) => setProxyProject(id) },
}));
vi.mock('../state/AppContext', () => ({
  useApp: () => ({ projects: [{ id: 'p1', name: 'Captured', basePath: '/captured' }] }),
}));

const { ProxyPanel } = await import('./ProxyPanel');

describe('ProxyPanel', () => {
  beforeEach(() => setProxyProject.mockReset());

  it('explains how to turn the proxy on', async () => {
    // Given
    view = { enabled: false, allowedNetworks: [], project: null, caReady: false };

    // When
    renderWithShell(<ProxyPanel />);

    // Then
    expect(await screen.findByText('MOCKHUB_PROXY=true')).toBeInTheDocument();
  });

  it('chooses the project that receives proxied traffic', async () => {
    // Given
    view = { enabled: true, allowedNetworks: ['127.0.0.0/8'], project: null, caReady: true };
    setProxyProject.mockResolvedValue({ ...view, project: { id: 'p1', name: 'Captured', basePath: '/captured' } });
    renderWithShell(<ProxyPanel />);
    await userEvent.selectOptions(await screen.findByLabelText('Project for proxied traffic'), 'p1');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    // Then
    expect(setProxyProject).toHaveBeenCalledWith('p1');
    expect(await screen.findByText('Proxied traffic goes to Captured')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Download CA certificate/ })).toHaveAttribute('href', '/__admin/mockhub/api/proxy/ca.pem');
  });
});
