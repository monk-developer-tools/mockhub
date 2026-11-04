import { fromStub, toStub, validate, emptyForm } from './stubForm';
import { operationNameOf, operationStub, parseVariables, requestBody } from './graphql';
import { sampleRequest } from './sampleRequest';

describe('graphql', () => {
  it('finds the operation name of a query', () => {
    // When / Then
    expect(operationNameOf('query GetUser($id: ID!) { user(id: $id) { id } }')).toBe('GetUser');
    expect(operationNameOf('# comment\nmutation AddBook { addBook { id } }')).toBe('AddBook');
    expect(operationNameOf('{ books { id } }')).toBeNull();
  });

  it('writes the request body and reads variables', () => {
    // When
    const body = JSON.parse(requestBody('query Books { books { id } }', { first: 2 }));

    // Then
    expect(body).toEqual({ query: 'query Books { books { id } }', operationName: 'Books', variables: { first: 2 } });
    expect(parseVariables('[1]')).toEqual({ ok: false, error: 'Variables must be a JSON object' });
    expect(parseVariables('')).toEqual({ ok: true, value: {} });
  });

  it('saves an answer as a stub for its operation name and variables', () => {
    // When
    const stub = operationStub('/graphql', 'GetUser', { id: '42' }, '{"data":{"user":null}}');

    // Then
    expect(stub.name).toBe('GetUser (id 42)');
    expect(stub.priority).toBe(1);
    expect(stub.request).toEqual({
      method: 'POST',
      urlPath: '/graphql',
      bodyPatterns: [
        { matchesJsonPath: "$[?(@.operationName == 'GetUser')]" },
        { matchesJsonPath: { expression: '$.variables', equalToJson: '{"id":"42"}', ignoreExtraElements: true, ignoreArrayOrder: true } },
      ],
    });
    expect(stub.response).toMatchObject({ status: 200, jsonBody: { data: { user: null } } });
    expect((stub.metadata as { mockhub: { type: string } }).mockhub.type).toBe('graphql');
  });

  it('round-trips a schema endpoint and an operation stub through the form', () => {
    // Given
    const endpoint = {
      name: 'GraphQL: Library', priority: 10,
      request: { method: 'ANY', urlPath: '/graphql' },
      response: { status: 200, headers: { 'Content-Type': 'application/json' }, transformers: ['mockhub-graphql'], transformerParameters: { schema: 'type Query { a: Int }' } },
      metadata: { mockhub: { type: 'graphql', folder: 'GraphQL' } },
    };
    const operation = operationStub('/graphql', 'GetUser', { id: '42' }, '{"data":{}}');

    // When
    const a = fromStub(endpoint);
    const b = fromStub(operation);

    // Then
    expect(a.unsupported).toEqual([]);
    expect(a.form).toMatchObject({ kind: 'graphql', responseMode: 'graphql', graphqlSchema: 'type Query { a: Int }', graphqlOperation: '' });
    expect(toStub(a.form, endpoint).response).toEqual(endpoint.response);
    expect(b.unsupported).toEqual([]);
    expect(b.form).toMatchObject({ graphqlOperation: 'GetUser', graphqlVariables: '{"id":"42"}', body: [] });
    expect(toStub(b.form, operation).request).toEqual(operation.request);
  });

  it('checks the operation name, variables and schema', () => {
    // Given
    const form = { ...emptyForm('graphql'), graphqlOperation: 'Get User', graphqlVariables: '[]', responseMode: 'graphql' as const };

    // When
    const errors = validate(form);

    // Then
    expect(errors).toEqual(expect.arrayContaining([
      'Paste the GraphQL schema the answers come from.',
      'The operation name can only hold letters, digits and underscores, e.g. GetUser.',
      'Variables must be a JSON object, e.g. {"id": "42"}.',
    ]));
  });

  it('sends a sample GraphQL request for the operation', () => {
    // Given
    const form = { ...emptyForm('graphql'), graphqlOperation: 'GetUser', graphqlVariables: '{"id":"1"}' };

    // When
    const sample = sampleRequest(form);

    // Then
    expect(sample.method).toBe('POST');
    expect(sample.headers).toContainEqual(['Content-Type', 'application/json']);
    expect(JSON.parse(sample.body)).toMatchObject({ operationName: 'GetUser', variables: { id: '1' } });
  });
});
