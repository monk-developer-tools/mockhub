import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { AiStatus, StagedChange } from '../../api/types';
import { renderWithShell } from '../../test/render';

let canEdit = true;
let aiStatus: AiStatus = { enabled: true, provider: 'openrouter', providerLabel: 'OpenRouter', model: 'openrouter/auto' };
let staged: StagedChange[] = [];
const fromAgent: StagedChange = {
  change: {
    id: 'c-9', action: 'create', stubId: null, before: null, summary: 'List orders',
    stub: { request: { method: 'GET', urlPath: '/orders' }, response: { status: 200 } },
  },
  by: 'Alex Kim via token “Claude Code”',
  at: new Date().toISOString(),
};
const applyStaged = vi.fn((..._a: unknown[]) => Promise.resolve({ applied: 1, results: [{ changeId: 'c-9', ok: true, stubId: 's1', error: null }] }));
const discardStaged = vi.fn((..._a: unknown[]) => Promise.resolve());
const reloadProject = vi.fn(() => Promise.resolve());

vi.mock('../../api/client', async (original) => ({
  ...(await original<typeof import('../../api/client')>()),
  api: {
    stagedChanges: () => Promise.resolve(staged),
    applyStaged: (...a: unknown[]) => applyStaged(...a),
    discardStaged: (...a: unknown[]) => discardStaged(...a),
  },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ aiStatus, canEdit, isAdmin: false, refreshProjects: vi.fn(() => Promise.resolve()) }) }));
vi.mock('./ProjectPage', () => ({ useProject: () => ({ project: { id: 'p1', name: 'Shop', basePath: '/shop' }, reloadProject }) }));

const { AssistantTab } = await import('./AssistantTab');

describe('Changes staged by agents', () => {
  beforeEach(() => {
    sessionStorage.clear();
    applyStaged.mockClear();
    discardStaged.mockClear();
    canEdit = true;
    aiStatus = { enabled: true, provider: 'openrouter', providerLabel: 'OpenRouter', model: 'openrouter/auto' };
    staged = [fromAgent];
  });

  it('shows who staged each change and applies it', async () => {
    // Given
    renderWithShell(<AssistantTab />);
    const panel = (await screen.findByRole('heading', { name: '1 change from agents' })).closest('.panel') as HTMLElement;
    expect(within(panel).getByText(/Alex Kim via token “Claude Code” · just now/)).toBeInTheDocument();

    // When
    staged = [];
    await userEvent.click(within(panel).getByRole('button', { name: 'Apply: List orders' }));

    // Then
    expect(applyStaged).toHaveBeenCalledWith('p1', ['c-9']);
    await waitFor(() => expect(screen.queryByRole('heading', { name: '1 change from agents' })).not.toBeInTheDocument());
    expect(reloadProject).toHaveBeenCalled();
  });

  it('discards a staged change', async () => {
    // Given
    renderWithShell(<AssistantTab />);
    await screen.findByRole('heading', { name: '1 change from agents' });

    // When
    staged = [];
    await userEvent.click(screen.getByRole('button', { name: 'Discard: List orders' }));

    // Then
    expect(discardStaged).toHaveBeenCalledWith('p1', 'c-9');
    await waitFor(() => expect(screen.queryByText('List orders')).not.toBeInTheDocument());
  });

  it('is still reviewable when the AI assistant is off, but only editors can apply', async () => {
    // Given
    aiStatus = { enabled: false, provider: 'openrouter', providerLabel: 'OpenRouter', model: '' };
    canEdit = false;

    // When
    renderWithShell(<AssistantTab />);

    // Then
    expect(await screen.findByRole('heading', { name: '1 change from agents' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'The AI assistant is off' })).toBeInTheDocument();
    expect(screen.getByText(/An editor has to apply them/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply: List orders' })).toBeDisabled();
  });
});
