import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const catalog = {
  descriptors: [{ name: 'library.dsc', size: 945, files: ['google/protobuf/timestamp.proto', 'library.proto'], services: ['library.v1.BookService'] }],
  services: [{
    name: 'library.v1.BookService',
    descriptor: 'library.dsc',
    methods: [
      { name: 'GetBook', path: '/library.v1.BookService/GetBook', kind: 'unary', requestType: 'library.v1.GetBookRequest', responseType: 'library.v1.Book', requestExample: { id: '42' }, responseExample: { id: '42' } },
      { name: 'ListBooks', path: '/library.v1.BookService/ListBooks', kind: 'server-streaming', requestType: 'library.v1.ListBooksRequest', responseType: 'library.v1.Book', requestExample: {}, responseExample: { id: '42' } },
    ],
  }],
};
const empty = { descriptors: [], services: [] };
let current: unknown = catalog;
const createStub = vi.fn((_p: string, stub: unknown) => Promise.resolve(stub));
const uploadGrpcDescriptor = vi.fn((_p: string, _n: string, _f: Blob) => Promise.resolve(catalog));
const deleteGrpcDescriptor = vi.fn((_p: string, _n: string) => Promise.resolve());

vi.mock('../../api/client', () => ({
  api: {
    grpc: () => Promise.resolve(current),
    createStub: (p: string, s: unknown) => createStub(p, s),
    uploadGrpcDescriptor: (p: string, n: string, f: Blob) => uploadGrpcDescriptor(p, n, f),
    deleteGrpcDescriptor: (p: string, n: string) => deleteGrpcDescriptor(p, n),
  },
}));

const { GrpcServices, descriptorName } = await import('./GrpcServices');

describe('GrpcServices', () => {
  beforeEach(() => {
    current = catalog;
    createStub.mockClear();
    uploadGrpcDescriptor.mockClear();
  });

  it('lists the services and methods of each descriptor', async () => {
    // When
    renderWithShell(<GrpcServices projectId="p1" canEdit onStubsCreated={() => undefined} />);

    // Then
    expect(await screen.findByText('library.dsc')).toBeInTheDocument();
    expect(screen.getByText('library.v1.BookService')).toBeInTheDocument();
    expect(screen.getByText('GetBook')).toBeInTheDocument();
    expect(screen.getByText('server-streaming')).toBeInTheDocument();
  });

  it('creates a starter stub for every method of a service', async () => {
    // Given
    const created = vi.fn();
    renderWithShell(<GrpcServices projectId="p1" canEdit onStubsCreated={created} />);

    // When
    await userEvent.click(await screen.findByRole('button', { name: /Create stubs/ }));

    // Then
    await waitFor(() => expect(created).toHaveBeenCalled());
    expect(createStub).toHaveBeenCalledTimes(2);
    expect(createStub.mock.calls[0][1]).toMatchObject({ request: { urlPath: '/library.v1.BookService/GetBook' } });
  });

  it('uploads a dropped descriptor under a safe name', async () => {
    // Given
    current = empty;
    renderWithShell(<GrpcServices projectId="p1" canEdit onStubsCreated={() => undefined} />);
    await screen.findByText('No descriptors yet.');

    // When
    const input = document.querySelector('input[type=file]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File([new Uint8Array([10, 1])], 'my library.dsc')] } });

    // Then
    await waitFor(() => expect(uploadGrpcDescriptor).toHaveBeenCalled());
    expect(uploadGrpcDescriptor.mock.calls[0][1]).toBe('my-library.dsc');
    expect(await screen.findByText('library.dsc')).toBeInTheDocument();
  });

  it('shows the list without upload controls to viewers', async () => {
    // When
    renderWithShell(<GrpcServices projectId="p1" canEdit={false} onStubsCreated={() => undefined} />);

    // Then
    expect(await screen.findByText('library.dsc')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Create stubs/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Drop a .dsc/)).not.toBeInTheDocument();
  });

  it('names descriptors so the server accepts them', () => {
    // Then
    expect(descriptorName('api.pb')).toBe('api.pb');
    expect(descriptorName('services.proto')).toBe('services.proto');
    expect(descriptorName('..evil.dsc')).toBe('evil.dsc');
  });
});
