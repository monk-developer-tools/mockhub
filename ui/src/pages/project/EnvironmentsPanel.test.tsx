import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { ProjectEnvironments } from '../../api/types';
import { renderWithShell } from '../../test/render';

let stored: ProjectEnvironments = { active: null, environments: [] };
const saveEnvironments = vi.fn((_id: string, body: ProjectEnvironments) => {
  stored = body;
  return Promise.resolve(body);
});
const switchEnvironment = vi.fn((_id: string, name: string) => {
  stored = { ...stored, active: name };
  return Promise.resolve(stored);
});
let role = 'editor';

vi.mock('../../api/client', () => ({
  api: {
    environments: () => Promise.resolve(stored),
    saveEnvironments: (id: string, body: ProjectEnvironments) => saveEnvironments(id, body),
    switchEnvironment: (id: string, name: string) => switchEnvironment(id, name),
  },
}));
vi.mock('../../state/AppContext', () => ({
  atLeast: (r: string, needed: string) => ['viewer', 'editor', 'manager', 'admin'].indexOf(r) >= ['viewer', 'editor', 'manager', 'admin'].indexOf(needed),
  useApp: () => ({ roleInTeam: () => role }),
}));
const project = { id: 'p1', name: 'Pay', orgId: 'o1', teamId: 't1' };
vi.mock('./ProjectPage', () => ({ useProject: () => ({ project }) }));

const { EnvironmentsPanel } = await import('./EnvironmentsPanel');
const { EnvironmentSwitch } = await import('./EnvironmentSwitch');

describe('Environments', () => {
  beforeEach(() => {
    role = 'editor';
    stored = { active: null, environments: [] };
    saveEnvironments.mockClear();
    switchEnvironment.mockClear();
  });

  it('adds dev and qa with a variable and saves them', async () => {
    // Given
    renderWithShell(<EnvironmentsPanel />);
    await screen.findByText(/No environments/);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Add environment/ }));
    await userEvent.type(screen.getByLabelText('dev variable 1 value'), 'https://dev.example.com');
    await userEvent.click(screen.getByRole('button', { name: /Add environment/ }));
    await userEvent.type(screen.getByLabelText('qa variable 1 value'), 'https://qa.example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Save environments' }));

    // Then
    await waitFor(() => expect(saveEnvironments).toHaveBeenCalledWith('p1', {
      active: 'dev',
      environments: [
        { name: 'dev', variables: { baseUrl: 'https://dev.example.com' } },
        { name: 'qa', variables: { baseUrl: 'https://qa.example.com' } },
      ],
    }));
  });

  it('switches the active environment from the project header', async () => {
    // Given
    stored = { active: 'dev', environments: [{ name: 'dev', variables: {} }, { name: 'qa', variables: {} }] };
    renderWithShell(<EnvironmentSwitch project={project as never} />);

    // When
    await userEvent.selectOptions(await screen.findByLabelText('Active environment'), 'qa');

    // Then
    expect(switchEnvironment).toHaveBeenCalledWith('p1', 'qa');
  });

  it('shows nothing in the header for a project without environments and lets viewers only look', async () => {
    // Given
    role = 'viewer';
    stored = { active: 'dev', environments: [{ name: 'dev', variables: { bank: 'sandbox' } }] };

    // When
    renderWithShell(<EnvironmentsPanel />);

    // Then
    expect(await screen.findByDisplayValue('sandbox')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Save environments' })).not.toBeInTheDocument();
  });
});
