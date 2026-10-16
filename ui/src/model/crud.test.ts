import type { Stub } from '../api/types';
import { crudEndpoints, crudOf, slugifyResource } from './crud';

describe('crud helpers', () => {
  it('recognises CRUD stubs by their metadata', () => {
    // Given
    const stub = { request: {}, response: {}, metadata: { mockhub: { crud: { resource: 'customers', idField: 'cid', idType: 'number' } } } } as Stub;

    // Then
    expect(crudOf(stub)).toEqual({ resource: 'customers', idField: 'cid', idType: 'number' });
    expect(crudOf({ request: {}, response: {} } as Stub)).toBeNull();
  });

  it('lists the six endpoints with the id field', () => {
    // When
    const endpoints = crudEndpoints('orders', 'orderId');

    // Then
    expect(endpoints.map((e) => `${e.method} ${e.path}`)).toEqual([
      'GET /orders', 'GET /orders/{orderId}', 'POST /orders', 'PUT /orders/{orderId}', 'PATCH /orders/{orderId}', 'DELETE /orders/{orderId}',
    ]);
  });

  it('turns free text into a resource path', () => {
    // Then
    expect(slugifyResource(' Customer Accounts ')).toBe('customer-accounts');
    expect(slugifyResource('/V1//Orders/')).toBe('v1/orders');
  });
});
