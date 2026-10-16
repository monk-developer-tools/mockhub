import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../../api/client';
import type { AiStatus, AssistantJob, ProposedChange } from '../../api/types';
import { renderWithShell } from '../../test/render';

let canEdit = true;
let aiStatus: AiStatus = { enabled: true, provider: 'openrouter', providerLabel: 'OpenRouter', model: 'openrouter/auto' };
const change: ProposedChange = {
  id: 'c-1', action: 'create', stubId: null, before: null, summary: 'Get one customer',
  stub: { request: { method: 'GET', urlPathTemplate: '/customers/{id}' }, response: { status: 200 } },
};
const steps = [{ tool: 'list_stubs', detail: '' }, { tool: 'create_stub', detail: 'Get one customer' }];
const doneJob: AssistantJob = {
  id: 'j1', status: 'done', steps, error: null,
  reply: { reply: 'Staged **one** stub.', changes: [change], steps, promptTokens: 1, completionTokens: 1 },
};
const startAssistant = vi.fn((..._args: unknown[]) => Promise.resolve({ id: 'j1', status: 'running', steps: [], reply: null, error: null }));
let assistantJob = vi.fn((..._args: unknown[]): Promise<AssistantJob> => Promise.resolve(doneJob));
const applyChanges = vi.fn((..._args: unknown[]) => Promise.resolve({ applied: 1, results: [{ changeId: 'c-1', ok: true, stubId: 's1', error: null }] }));
const reloadProject = vi.fn(() => Promise.resolve());

vi.mock('../../api/client', async (original) => ({
  ...(await original<typeof import('../../api/client')>()),
  api: {
    startAssistant: (...a: unknown[]) => startAssistant(...a),
    assistantJob: (...a: unknown[]) => assistantJob(...a),
    applyChanges: (...a: unknown[]) => applyChanges(...a),
  },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ aiStatus, canEdit, isAdmin: true, refreshProjects: vi.fn(() => Promise.resolve()) }) }));
vi.mock('./ProjectPage', () => ({ useProject: () => ({ project: { id: 'p1', name: 'Customers', basePath: '/customers-api' }, reloadProject }) }));

const { AssistantTab, stepsLabel } = await import('./AssistantTab');

describe('AssistantTab', () => {
  beforeEach(() => {
    sessionStorage.clear();
    startAssistant.mockClear();
    assistantJob = vi.fn((..._args: unknown[]): Promise<AssistantJob> => Promise.resolve(doneJob));
    applyChanges.mockClear();
    canEdit = true;
    aiStatus = { enabled: true, provider: 'openrouter', providerLabel: 'OpenRouter', model: 'openrouter/auto' };
  });

  it('sends the message and shows the reply with the staged changes', async () => {
    // Given
    renderWithShell(<AssistantTab />);

    // When
    await userEvent.type(screen.getByLabelText('Message the assistant'), 'Add GET /customers{enter}');

    // Then
    expect(await screen.findByText('one', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(startAssistant).toHaveBeenCalledWith('p1', [{ role: 'user', content: 'Add GET /customers' }], []);
    expect(assistantJob).toHaveBeenCalledWith('p1', 'j1');
    expect(screen.getByText('read the stubs · staged 1 change')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Review 1 change' })).toBeInTheDocument();
    expect(screen.getByText('Get one customer')).toBeInTheDocument();
  });

  it('applies changes and clears them from the review list', async () => {
    // Given
    renderWithShell(<AssistantTab />);
    await userEvent.click(screen.getByRole('button', { name: /Create CRUD endpoints/ }));
    await screen.findByRole('heading', { name: 'Review 1 change' }, { timeout: 3000 });

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Apply all' }));

    // Then
    await waitFor(() => expect(applyChanges).toHaveBeenCalledWith('p1', [change]));
    expect((await screen.findAllByText('Applied 1 change.')).length).toBeGreaterThan(0);
    expect(screen.queryByRole('heading', { name: /Review/ })).not.toBeInTheDocument();
    expect(reloadProject).toHaveBeenCalled();
  });

  it('lets viewers draft but not apply', async () => {
    // Given
    canEdit = false;
    renderWithShell(<AssistantTab />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Create CRUD endpoints/ }));

    // Then
    expect(await screen.findByRole('button', { name: 'Apply all' }, { timeout: 3000 })).toBeDisabled();
    expect(screen.getByText(/an editor has to apply them/)).toBeInTheDocument();
  });

  it('discards a change and shows a diff on request', async () => {
    // Given
    renderWithShell(<AssistantTab />);
    await userEvent.click(screen.getByRole('button', { name: /Create CRUD endpoints/ }));
    await screen.findByRole('heading', { name: 'Review 1 change' }, { timeout: 3000 });

    // When
    await userEvent.click(screen.getByRole('button', { name: 'JSON' }));

    // Then
    expect(screen.getByLabelText('Changes')).toHaveTextContent('+ {');
    await userEvent.click(screen.getByRole('button', { name: 'Discard: Get one customer' }));
    expect(screen.queryByRole('heading', { name: /Review/ })).not.toBeInTheDocument();
  });

  it('shows live progress and picks the reply up after leaving the tab', async () => {
    // Given
    let finished = false;
    assistantJob = vi.fn(() => Promise.resolve(finished ? doneJob : { ...doneJob, status: 'running' as const, reply: null }));
    const first = renderWithShell(<AssistantTab />);
    await userEvent.type(screen.getByLabelText('Message the assistant'), 'Add GET /customers{enter}');
    expect(await screen.findByText('read the stubs · staged 1 change', {}, { timeout: 3000 })).toBeInTheDocument();

    // When
    first.unmount();
    finished = true;
    renderWithShell(<AssistantTab />);

    // Then
    expect(await screen.findByText('one', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText('Add GET /customers')).toBeInTheDocument();
    expect(screen.queryByText(/Working on it/)).not.toBeInTheDocument();
  });

  it('explains when the server lost the request', async () => {
    // Given
    assistantJob = vi.fn(() => Promise.reject(new ApiError(404, 'This assistant request is no longer available.')));
    renderWithShell(<AssistantTab />);

    // When
    await userEvent.type(screen.getByLabelText('Message the assistant'), 'Hi{enter}');

    // Then
    expect(await screen.findByRole('alert', {}, { timeout: 3000 })).toHaveTextContent('no longer available');
    expect(screen.getByLabelText('Send')).toBeDisabled();
    expect(screen.queryByText(/Working on it/)).not.toBeInTheDocument();
  });

  it('explains how to turn the assistant on', () => {
    // Given
    aiStatus = { ...aiStatus, enabled: false };

    // When
    renderWithShell(<AssistantTab />);

    // Then
    expect(screen.getByRole('link', { name: 'Set up the assistant' })).toHaveAttribute('href', '/admin/ai');
  });

  it('summarises the tool steps', () => {
    // Then
    expect(stepsLabel([{ tool: 'list_requests', detail: '' }, { tool: 'create_stub', detail: 'Error: bad' }, { tool: 'create_stub', detail: 'ok' }]))
      .toBe('checked the request log · staged 2 changes · fixed 1 mistake');
  });
});
