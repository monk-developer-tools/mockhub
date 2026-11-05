import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const fetchOpenApi = vi.fn();
const createProject = vi.fn();
const importInto = vi.fn();
const refreshProjects = vi.fn();
const team = { id: 't1', name: 'Core', role: 'editor' };
let teams = [team];
vi.mock('../api/client', () => ({
  api: {
    fetchOpenApi: (url: string) => fetchOpenApi(url),
    createProject: (body: unknown) => createProject(body),
    importInto: (id: string, format: string, body: string) => importInto(id, format, body),
  },
}));
vi.mock('../state/AppContext', () => ({
  useApp: () => ({
    orgs: [{ id: 'o1', name: 'Acme', teams }],
    currentOrg: { id: 'o1', name: 'Acme', teams },
    writableTeams: () => teams,
    refreshProjects,
    setCurrentOrg: vi.fn(),
  }),
}));

const { MockTheseApisPage } = await import('./MockTheseApisPage');

const SPEC_URL = 'https://api.example.com/openapi.yaml';

describe('MockTheseApisPage', () => {
  beforeEach(() => {
    teams = [team];
    fetchOpenApi.mockReset();
    createProject.mockReset();
    importInto.mockReset();
  });

  it('loads the file from the link and creates a project with its stubs', async () => {
    // Given
    fetchOpenApi.mockResolvedValue({ url: SPEC_URL, title: 'Pet Store', version: '1.0', operations: 3, content: 'openapi: 3.0.0' });
    createProject.mockResolvedValue({ id: 'p9', name: 'Pet Store' });
    importInto.mockResolvedValue({ imported: 3 });
    renderWithShell(<MockTheseApisPage />, `/mock-these-apis?openapi=${encodeURIComponent(SPEC_URL)}`);
    expect(await screen.findByText('Pet Store')).toBeInTheDocument();

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Create mock project' }));

    // Then
    expect(fetchOpenApi).toHaveBeenCalledWith(SPEC_URL);
    expect(createProject).toHaveBeenCalledWith(expect.objectContaining({ name: 'Pet Store', slug: 'pet-store', orgId: 'o1', teamId: 't1' }));
    await waitFor(() => expect(importInto).toHaveBeenCalledWith('p9', 'openapi', 'openapi: 3.0.0'));
    expect(await screen.findByText('Created “Pet Store” with 3 stubs')).toBeInTheDocument();
  });

  it('shows why a file could not be loaded', async () => {
    // Given
    fetchOpenApi.mockRejectedValue(new Error('api.example.com answered 404 for the API description'));
    renderWithShell(<MockTheseApisPage />, '/mock-these-apis');
    await userEvent.type(screen.getByLabelText('OpenAPI URL'), SPEC_URL);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Load/ }));

    // Then
    expect(await screen.findByRole('alert')).toHaveTextContent('answered 404');
    expect(screen.queryByRole('button', { name: 'Create mock project' })).not.toBeInTheDocument();
  });

  it('offers the badge snippets for the file', async () => {
    // Given
    renderWithShell(<MockTheseApisPage />, '/mock-these-apis');

    // When
    await userEvent.type(screen.getByLabelText('OpenAPI URL'), SPEC_URL);

    // Then
    expect(screen.getByText(/^\[!\[Mock these APIs\]/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Mock these APIs' })).toBeInTheDocument();
  });

  it('explains that creating needs the editor role', async () => {
    // Given
    teams = [];
    fetchOpenApi.mockResolvedValue({ url: SPEC_URL, title: 'Pet Store', version: '', operations: 1, content: 'x' });

    // When
    renderWithShell(<MockTheseApisPage />, `/mock-these-apis?openapi=${encodeURIComponent(SPEC_URL)}`);

    // Then
    expect(await screen.findByText(/need the editor role/)).toBeInTheDocument();
  });
});
