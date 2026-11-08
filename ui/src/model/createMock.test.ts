import { describe, expect, it, vi } from 'vitest';
import type { Project, Stub } from '../api/types';
import type { GrpcCatalog } from './grpc';
import { createMock, freeSlug, protoFileName, type MockApi } from './createMock';

function fakeApi(overrides: Partial<MockApi> = {}): MockApi & { stubs: Stub[] } {
  const stubs: Stub[] = [];
  return {
    stubs,
    createProject: vi.fn(async (body: Partial<Project>) => ({ id: 'p1', ...body }) as Project),
    deleteProject: vi.fn(async () => undefined),
    importInto: vi.fn(async () => ({ imported: 3, skipped: ['one left out'] }) as never),
    uploadGrpcDescriptor: vi.fn(async () => ({
      descriptors: [],
      services: [{ name: 'helloworld.Greeter', descriptor: 'service.proto', methods: [
        { name: 'SayHello', path: '/helloworld.Greeter/SayHello', kind: 'unary', requestType: 'a', responseType: 'b', requestExample: {}, responseExample: { message: 'hi' } },
      ] }],
    }) as GrpcCatalog),
    createStub: vi.fn(async (_id: string, stub: Stub) => {
      stubs.push(stub);
      return stub;
    }),
    createCrud: vi.fn(async () => ({ seeded: 2, stub: {} })),
    ...overrides,
  };
}

const NEW = { mode: 'new' as const, name: 'Orders', slug: 'orders', orgId: 'o1', teamId: 't1', description: 'Made from orders.yaml' };

describe('createMock', () => {
  it('creates a project and imports a spec into it in one go', async () => {
    // Given
    const api = fakeApi();

    // When
    const created = await createMock(api, NEW, { kind: 'openapi', text: 'openapi: 3.0.0', options: { stripPrefix: '/api' } });

    // Then
    expect(api.createProject).toHaveBeenCalledWith({ name: 'Orders', slug: 'orders', orgId: 'o1', teamId: 't1', description: 'Made from orders.yaml' });
    expect(api.importInto).toHaveBeenCalledWith('p1', 'openapi', 'openapi: 3.0.0', { stripPrefix: '/api' });
    expect(created).toEqual({ projectId: 'p1', kind: 'openapi', made: 3, notes: ['one left out'] });
  });

  it('adds to an existing project without creating one', async () => {
    // Given
    const api = fakeApi();

    // When
    const created = await createMock(api, { mode: 'existing', projectId: 'p9' }, { kind: 'json', stubs: [{ name: 'a' }, { name: 'b' }] });

    // Then
    expect(api.createProject).not.toHaveBeenCalled();
    expect(api.stubs).toEqual([{ name: 'a' }, { name: 'b' }]);
    expect(created.made).toBe(2);
  });

  it('uploads a proto file and adds a starter stub for every method', async () => {
    // Given
    const api = fakeApi();

    // When
    const created = await createMock(api, NEW, { kind: 'grpc', text: 'syntax = "proto3";', fileName: 'my greeter.proto' });

    // Then
    expect(api.uploadGrpcDescriptor).toHaveBeenCalledWith('p1', 'my-greeter.proto', expect.any(Blob));
    expect(api.stubs[0]).toMatchObject({ request: { urlPath: '/helloworld.Greeter/SayHello' }, metadata: { mockhub: { type: 'grpc' } } });
    expect(created.made).toBe(1);
  });

  it('deletes the new project again when filling it fails, so nothing empty is left behind', async () => {
    // Given
    const api = fakeApi({ importInto: vi.fn(async () => { throw new Error('Line 3: bad spec'); }) });

    // When
    const attempt = createMock(api, NEW, { kind: 'wsdl', text: '<x/>', options: {} });

    // Then
    await expect(attempt).rejects.toThrow('Line 3: bad spec');
    expect(api.deleteProject).toHaveBeenCalledWith('p1');
  });

  it('saves CRUD records and picks free slugs and safe proto names', async () => {
    // Given
    const api = fakeApi();

    // When
    const created = await createMock(api, NEW, { kind: 'crud', request: { resource: 'users', idField: 'id', idType: 'number', seed: [{}, {}] } });

    // Then
    expect(created.notes).toEqual(['2 records saved']);
    expect(freeSlug('orders', ['orders', 'orders-2'])).toBe('orders-3');
    expect(freeSlug('', [])).toBe('mock');
    expect(protoFileName('../x.txt')).toBe('service.proto');
  });
});
