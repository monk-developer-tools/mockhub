import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const createProject = vi.fn();
const deleteProject = vi.fn();
const importInto = vi.fn();
const createStub = vi.fn();
const refreshProjects = vi.fn();
const team = { id: 't1', name: 'Core' };
let state: Record<string, unknown> = {};

vi.mock('../api/client', () => ({
  api: {
    createProject: (body: unknown) => createProject(body),
    deleteProject: (id: string) => deleteProject(id),
    importInto: (id: string, format: string, body: string, options: unknown) => importInto(id, format, body, options),
    createStub: (id: string, stub: unknown) => createStub(id, stub),
    fetchOpenApi: vi.fn(),
    uploadGrpcDescriptor: vi.fn(),
    createCrud: vi.fn(),
  },
}));
vi.mock('../state/AppContext', () => ({
  atLeast: (role: string, needed: string) => ['viewer', 'editor', 'manager', 'admin'].indexOf(role) >= ['viewer', 'editor', 'manager', 'admin'].indexOf(needed),
  useApp: () => state,
}));

const { NewMockPage } = await import('./NewMockPage');
const { NewMockFlowPage } = await import('./NewMockFlowPage');

function Where() {
  const location = useLocation();
  return <p>at {location.pathname}{location.search}</p>;
}

function flow(path: string) {
  return renderWithShell(
    <Routes>
      <Route path="/new/:kind" element={<NewMockFlowPage />} />
      <Route path="/new" element={<p>start page</p>} />
      <Route path="/projects/:id/*" element={<Where />} />
    </Routes>,
    path,
  );
}

const manager = {
  info: { baseUrl: 'https://mocks.example.com' },
  canEdit: true,
  canManage: true,
  isAdmin: false,
  aiStatus: { enabled: false },
  currentOrg: { id: 'o1', name: 'Acme' },
  projects: [{ id: 'old', slug: 'orders-api' }],
  orgProjects: [{ id: 'old', name: 'Old project', orgId: 'o1', teamId: 't1' }],
  writableTeams: () => [team],
  roleInTeam: () => 'manager',
  refreshProjects,
};

describe('Start a new mock', () => {
  beforeEach(() => {
    [createProject, deleteProject, importInto, createStub].forEach((f) => f.mockReset());
    state = { ...manager };
  });

  it('shows every way to start in the three groups of the website menus', () => {
    // When
    renderWithShell(<NewMockPage />, '/new');

    // Then
    expect(screen.getByRole('heading', { name: 'Mock servers' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Tools' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Service virtualization' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /OpenAPI or Swagger/ })).toHaveAttribute('href', '/new/openapi');
    expect(screen.getByRole('link', { name: /Webhook testing/ })).toHaveAttribute('href', '/new/webhook');
    expect(screen.getByRole('link', { name: /Generate test data/ })).toHaveAttribute('href', 'https://mockhub.devops-monk.com/tools/fake-data/');
    expect(screen.getByText(/Needs AI, which an admin turns on/)).toBeInTheDocument();
  });

  it('tells a viewer why they cannot create mocks', () => {
    // Given
    state = { ...manager, canEdit: false, canManage: false };

    // When
    renderWithShell(<NewMockPage />, '/new');

    // Then
    expect(screen.getByText(/ask a manager of your team for the editor role/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /OpenAPI or Swagger/ })).not.toBeInTheDocument();
  });

  it('creates a project from the sample spec and opens its ready view', async () => {
    // Given
    createProject.mockResolvedValue({ id: 'p7', name: 'Orders API' });
    importInto.mockResolvedValue({ imported: 2 });
    flow('/new/openapi');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Use a sample' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create mock' }));

    // Then
    await waitFor(() => expect(createProject).toHaveBeenCalledWith(expect.objectContaining({ name: 'Orders API', slug: 'orders-api-2', orgId: 'o1', teamId: 't1' })));
    expect(importInto).toHaveBeenCalledWith('p7', 'openapi', expect.stringContaining('title: Orders API'), {});
    expect(await screen.findByText('at /projects/p7/overview?ready=openapi')).toBeInTheDocument();
  });

  it('adds a webhook receiver to a project the user already has', async () => {
    // Given
    createStub.mockResolvedValue({});
    flow('/new/webhook');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'A project I already have' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create mock' }));

    // Then
    await waitFor(() => expect(createStub).toHaveBeenCalledWith('old', expect.objectContaining({ request: { method: 'ANY', urlPathPattern: '/hooks(/.*)?' } })));
    expect(createProject).not.toHaveBeenCalled();
    expect(await screen.findByText('at /projects/old/overview?ready=webhook')).toBeInTheDocument();
  });

  it('explains a mistake before creating anything', async () => {
    // Given
    flow('/new/json');
    await userEvent.type(screen.getByLabelText('The answer to send'), '{{oops');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Create mock' }));

    // Then
    expect(await screen.findByRole('alert')).toHaveTextContent('isn\'t valid JSON');
    expect(createProject).not.toHaveBeenCalled();
  });
});
