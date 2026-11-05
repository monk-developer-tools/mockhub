import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const refreshProjects = vi.fn();
const examples = vi.fn();
const installExample = vi.fn();
vi.mock('../state/AppContext', () => ({ useApp: () => ({ canManage: true, refreshProjects }) }));
vi.mock('../api/client', () => ({ api: { examples: () => examples(), installExample: (id: string) => installExample(id) } }));

const { ExamplesPage, tagClass } = await import('./ExamplesPage');

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

  it('colours protocol tags', async () => {
    // Given
    examples.mockResolvedValue([{ id: 'grpc-inventory', name: 'Inventory', slug: 'g', description: 'gRPC', tags: ['gRPC', 'Streaming'] }]);

    // When
    renderWithShell(<ExamplesPage />);

    // Then
    expect(await screen.findByText('gRPC', { selector: 'span' })).toHaveClass('tag', 'grpc');
    expect(screen.getByText('Streaming')).toHaveClass('tag');
    expect(screen.getByText('Streaming')).not.toHaveClass('grpc');
  });

  it('maps each protocol to its tag class', () => {
    // When / Then
    expect(tagClass('SOAP')).toBe('tag soap');
    expect(tagClass('GraphQL')).toBe('tag graphql');
    expect(tagClass('gRPC')).toBe('tag grpc');
    expect(tagClass('REST')).toBe('tag');
  });
});
