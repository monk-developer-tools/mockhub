import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import type { Project } from '../api/types';
import { renderWithShell } from '../test/render';

vi.mock('../api/client', () => ({
  api: {
    stubs: () => Promise.resolve([{ id: 's1', name: 'Get pet', request: { method: 'GET', urlPathTemplate: '/pets/{id}' }, response: { status: 200 } }]),
  },
}));

const { CommandPalette } = await import('./CommandPalette');

const project: Project = {
  id: 'p1', name: 'Petstore', slug: 'petstore', description: '', color: '#000', createdAt: '', updatedAt: '',
  basePath: '/petstore', mockUrl: 'http://h/petstore', stubCount: 1, soapStubCount: 0, requestCount: 0, unmatchedCount: 0, activity: [], orgId: 'o1', teamId: 't1',
};

function Where() {
  return <div data-testid="where">{useLocation().pathname}</div>;
}

function setup(canEdit = true) {
  const handlers = { onClose: vi.fn(), onNewProject: vi.fn(), onToggleTheme: vi.fn(), onShowShortcuts: vi.fn() };
  renderWithShell(
    <Routes>
      <Route path="*" element={<><CommandPalette projects={[project]} canEdit={canEdit} {...handlers} /><Where /></>} />
    </Routes>,
  );
  return handlers;
}

describe('CommandPalette', () => {
  it('finds a stub and opens it with Enter', async () => {
    // Given
    const { onClose } = setup();

    // When
    await waitFor(() => expect(screen.getByRole('combobox')).toHaveFocus());
    await userEvent.type(screen.getByRole('combobox'), 'pets id');
    await screen.findByText('GET /pets/{id}');
    await userEvent.keyboard('{Enter}');

    // Then
    expect(screen.getByTestId('where')).toHaveTextContent('/projects/p1/stubs/s1');
    expect(onClose).toHaveBeenCalled();
  });

  it('runs actions and hides editing actions from viewers', async () => {
    // Given
    const viewer = setup(false);

    // When / Then
    expect(screen.queryByText('New project')).not.toBeInTheDocument();
    await userEvent.click(screen.getByText('Switch theme'));
    expect(viewer.onToggleTheme).toHaveBeenCalled();
  });
});
