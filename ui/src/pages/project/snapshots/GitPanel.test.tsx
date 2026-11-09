import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { GitLinkView, SnapshotDiff } from '../../../api/types';
import { renderWithShell } from '../../../test/render';

let role = 'manager';
let link: GitLinkView = { linked: false, suggestedFolder: 'mockhub/shop' };
const linked: GitLinkView = {
  linked: true, url: 'https://github.com/acme/mocks.git', branch: 'main', folder: 'mockhub/shop', username: '', tokenSet: true,
  lastPush: { at: '2026-11-08T10:00:00Z', by: 'alex', commit: '9f8e7d6c5b4a' },
};
const diff: SnapshotDiff = {
  from: 'Now', to: 'main 9f8e7d6', identical: false,
  counts: { added: 0, removed: 0, changed: 1, unchanged: 0 },
  stubs: [{ id: 'a', status: 'changed', name: 'List items', method: 'GET', url: '/items', before: null, after: null }],
  runtime: { changed: false, before: null, after: null },
  apiKeyHeader: { changed: false, before: null, after: null },
  files: { added: [], removed: [], changed: [] },
};
const saveGitLink = vi.fn((..._a: unknown[]) => Promise.resolve(linked));
const gitPush = vi.fn((..._a: unknown[]) => Promise.resolve({ changed: true, commit: 'abcdef123456', stubs: 2, files: 0 }));
const gitPull = vi.fn((..._a: unknown[]) => Promise.resolve({
  commit: '9f8e7d6c5b4a', stubs: 1, files: 0,
  safety: { id: 's9', name: 'Before pulling from Git', purpose: '', createdAt: '', createdBy: '', safety: true, sourceProjectName: 'Shop', stubCount: 1, fileCount: 0, crudResources: [] },
}));
const onPulled = vi.fn();

vi.mock('../../../api/client', () => ({
  api: {
    gitLink: () => Promise.resolve(link),
    saveGitLink: (...a: unknown[]) => saveGitLink(...a),
    gitPush: (...a: unknown[]) => gitPush(...a),
    gitPreview: () => Promise.resolve(diff),
    gitPull: (...a: unknown[]) => gitPull(...a),
    unlinkGit: () => Promise.resolve(),
  },
}));
vi.mock('../../../state/AppContext', () => ({
  atLeast: (r: string, needed: string) => ['viewer', 'editor', 'manager', 'admin'].indexOf(r) >= ['viewer', 'editor', 'manager', 'admin'].indexOf(needed),
  useApp: () => ({ canEdit: role !== 'viewer', roleInTeam: () => role }),
}));

const { GitPanel } = await import('./GitPanel');
const project = { id: 'p1', name: 'Shop', basePath: '/shop', orgId: 'o1', teamId: 't1' } as never;

describe('Git panel', () => {
  beforeEach(() => {
    role = 'manager';
    link = { linked: false, suggestedFolder: 'mockhub/shop' };
    [saveGitLink, gitPush, gitPull, onPulled].forEach((f) => f.mockClear());
  });

  it('lets a manager link a repository with the suggested folder', async () => {
    // Given
    renderWithShell(<GitPanel project={project} onPulled={onPulled} />);

    // When
    await userEvent.click(await screen.findByRole('button', { name: /Link a repository/ }));
    await userEvent.type(screen.getByLabelText('Repository URL'), 'https://github.com/acme/mocks.git');
    await userEvent.type(screen.getByLabelText('Access token'), 'ghp_x');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    // Then
    expect(saveGitLink).toHaveBeenCalledWith('p1', { url: 'https://github.com/acme/mocks.git', branch: 'main', folder: 'mockhub/shop', username: '', token: 'ghp_x' });
    expect(await screen.findByText(/github.com\/acme\/mocks.git · main · mockhub\/shop\//)).toBeInTheDocument();
  });

  it('tells an editor who can link it', async () => {
    // Given
    role = 'editor';

    // When
    renderWithShell(<GitPanel project={project} onPulled={onPulled} />);

    // Then
    expect(await screen.findByText(/A manager of this project's team can link a repository/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Link a repository/ })).not.toBeInTheDocument();
  });

  it('pushes with a message and pulls after showing what changes', async () => {
    // Given
    role = 'editor';
    link = linked;
    renderWithShell(<GitPanel project={project} onPulled={onPulled} />);
    expect(await screen.findByText(/Pushed .* by alex · 9f8e7d6/)).toBeInTheDocument();

    // When
    await userEvent.type(screen.getByLabelText('Commit message'), 'Add refunds');
    await userEvent.click(screen.getByRole('button', { name: /Push/ }));
    await userEvent.click(screen.getByRole('button', { name: /^Pull$/ }));
    await screen.findByText('List items');
    await userEvent.click(screen.getByRole('button', { name: 'Pull into this project' }));

    // Then
    expect(gitPush).toHaveBeenCalledWith('p1', 'Add refunds');
    await waitFor(() => expect(gitPull).toHaveBeenCalledWith('p1'));
    expect(onPulled).toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Change' })).not.toBeInTheDocument();
  });
});
