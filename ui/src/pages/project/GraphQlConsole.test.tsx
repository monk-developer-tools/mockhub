import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { Project } from '../../api/types';
import { emptyForm } from '../../model/stubForm';
import { renderWithShell } from '../../test/render';

const operations = [
  { kind: 'query', field: 'book', operationName: 'Book', description: '', query: 'query Book($id: ID!) {\n  book(id: $id) {\n    title\n  }\n}\n', variables: { id: '7' } },
];
const graphqlOperations = vi.fn((..._a: unknown[]) => Promise.resolve({ schema: 'x', operations }));
vi.mock('../../api/client', () => ({ api: { graphqlOperations: (...a: unknown[]) => graphqlOperations(...a) } }));

const { GraphQlConsole } = await import('./GraphQlConsole');

const project = { id: 'p1', basePath: '/library', mockUrl: 'http://h/library' } as unknown as Project;
const form = { ...emptyForm('graphql'), responseMode: 'graphql' as const, graphqlSchema: 'type Query { book(id: ID!): Book } type Book { title: String }', graphqlOperation: '' };

describe('GraphQlConsole', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('runs an example operation from the schema and saves the answer as a stub', async () => {
    // Given
    const fetchMock = vi.fn((_url: string, _init: RequestInit) => Promise.resolve(new Response('{"data":{"book":{"title":"Dune"}}}', { status: 200 })));
    vi.stubGlobal('fetch', fetchMock);
    const onCreate = vi.fn();
    renderWithShell(<GraphQlConsole project={project} form={form} canEdit onCreate={onCreate} />);

    // When
    await userEvent.selectOptions(await screen.findByRole('combobox'), 'Book');
    await userEvent.click(screen.getByRole('button', { name: /POST \/library\/graphql/ }));
    await userEvent.click(await screen.findByRole('button', { name: /Save answer as a stub for Book/ }));

    // Then
    expect(graphqlOperations).toHaveBeenCalledWith('p1', form.graphqlSchema);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/library/graphql');
    expect(JSON.parse(String(init.body))).toMatchObject({ operationName: 'Book', variables: { id: '7' } });
    expect(screen.getByText(/"title": "Dune"/)).toBeInTheDocument();
    await waitFor(() => expect(onCreate).toHaveBeenCalled());
    expect(onCreate.mock.calls[0][0]).toMatchObject({ name: 'Book (id 7)', response: { jsonBody: { data: { book: { title: 'Dune' } } } } });
  });

  it('explains variables that are not JSON before sending', async () => {
    // Given
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderWithShell(<GraphQlConsole project={project} form={{ ...form, graphqlVariables: '{bad' }} canEdit onCreate={vi.fn()} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /POST/ }));

    // Then
    expect(screen.getByRole('alert')).toHaveTextContent("Variables aren't valid JSON");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
