import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const refreshProjects = vi.fn();
const examples = vi.fn();
const installExample = vi.fn();
vi.mock('../state/AppContext', () => ({ useApp: () => ({ canEdit: true, refreshProjects }) }));
vi.mock('../api/client', () => ({ api: { examples: () => examples(), installExample: (id: string) => installExample(id) } }));

const { ExamplesPage } = await import('./ExamplesPage');

describe('ExamplesPage', () => {
  it('lists examples and installs one as a project', async () => {
    // Given
    examples.mockResolvedValue([{ id: 'rest-basics', name: 'Petstore', slug: 'p', description: 'CRUD', tags: ['REST'] }]);
    installExample.mockResolvedValue({ id: 'p1', name: 'Petstore' });
    renderWithShell(<ExamplesPage />);

    // When
    await userEvent.click(await screen.findByRole('button', { name: /Install as project/ }));

    // Then
    expect(installExample).toHaveBeenCalledWith('rest-basics');
    await waitFor(() => expect(refreshProjects).toHaveBeenCalled());
    expect(await screen.findByText('Installed “Petstore”')).toBeInTheDocument();
  });
});
