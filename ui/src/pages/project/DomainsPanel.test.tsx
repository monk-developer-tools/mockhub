import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const updateProject = vi.fn((_id: string, _body: unknown) => Promise.resolve({}));
const reloadProject = vi.fn();
let domains: string[] = [];
vi.mock('../../api/client', () => ({ api: { updateProject: (id: string, body: unknown) => updateProject(id, body) } }));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ canManage: true, refreshProjects: vi.fn() }) }));
vi.mock('./ProjectPage', () => ({
  useProject: () => ({ project: { id: 'p1', slug: 'pay', basePath: '/pay', mockUrl: 'https://mocks.example.com/pay', domains }, reloadProject }),
}));

const { DomainsPanel } = await import('./ProjectSettingsTab');

describe('DomainsPanel', () => {
  beforeEach(() => {
    domains = [];
    updateProject.mockClear();
  });

  it('saves the host names one per line', async () => {
    // Given
    renderWithShell(<DomainsPanel />);
    await userEvent.type(screen.getByLabelText('Custom domains'), 'Payments.Example.com{enter}pay.test');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    // Then
    expect(updateProject).toHaveBeenCalledWith('p1', { domains: ['payments.example.com', 'pay.test'] });
    expect(reloadProject).toHaveBeenCalled();
  });

  it('explains a wrong entry and does not save it', async () => {
    // Given
    renderWithShell(<DomainsPanel />);

    // When
    await userEvent.type(screen.getByLabelText('Custom domains'), 'https://pay.example.com');

    // Then
    expect(screen.getByRole('alert')).toHaveTextContent('enter only the host name');
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('lists the URL of each saved domain', () => {
    // Given
    domains = ['payments.example.com'];

    // When
    renderWithShell(<DomainsPanel />);

    // Then
    expect(screen.getByText('https://payments.example.com/')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});
