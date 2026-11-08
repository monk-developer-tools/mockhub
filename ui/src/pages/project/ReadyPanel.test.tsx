import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const stubs = vi.fn();

vi.mock('../../api/client', () => ({
  api: { stubs: (id: string) => stubs(id) },
}));
vi.mock('../../state/AppContext', () => ({
  useApp: () => ({ info: { baseUrl: 'https://mocks.example.com' } }),
}));

const { ReadyPanel } = await import('./ReadyPanel');

const project = { id: 'p1', name: 'Orders', mockUrl: 'https://mocks.example.com/orders' } as never;

describe('The ready view after creating a mock', () => {
  beforeEach(() => {
    stubs.mockReset();
    vi.unstubAllGlobals();
  });

  it('stays hidden when the page was not opened from a create screen', () => {
    // When
    renderWithShell(<ReadyPanel project={project} />, '/projects/p1/overview');

    // Then
    expect(screen.queryByRole('region', { name: 'Your mock is ready' })).not.toBeInTheDocument();
    expect(stubs).not.toHaveBeenCalled();
  });

  it('calls an endpoint from the page and shows its answer', async () => {
    // Given
    stubs.mockResolvedValue([{ request: { method: 'GET', urlPath: '/orders' }, response: { status: 200 } }]);
    const fetchMock = vi.fn().mockResolvedValue(new Response('[{"id": 1}]', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    renderWithShell(<ReadyPanel project={project} />, '/projects/p1/overview?ready=openapi');

    // When
    await userEvent.click(await screen.findByRole('button', { name: 'Try it' }));

    // Then
    expect(fetchMock).toHaveBeenCalledWith('https://mocks.example.com/orders/orders');
    expect(await screen.findByText(/^200 in \d+ ms · \[\{"id": 1\}\]$/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Invite your team' })).toHaveAttribute('href', '/org');
  });
});
