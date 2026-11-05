import { describe, expect, it } from 'vitest';
import type { Stub } from '../api/types';
import { grpcTarget, grpcurlCommand, parseGrpcPath, starterStub, type GrpcMethod } from './grpc';
import { emptyForm, fromStub, toStub, validate } from './stubForm';

const getBook: GrpcMethod = {
  name: 'GetBook',
  path: '/library.v1.BookService/GetBook',
  kind: 'unary',
  requestType: 'library.v1.GetBookRequest',
  responseType: 'library.v1.Book',
  requestExample: { id: '42' },
  responseExample: { id: '42', title: 'Dune' },
};

describe('gRPC stubs', () => {
  it('writes a POST to the service path with the message as JSON', () => {
    // Given
    const form = { ...emptyForm('grpc'), grpcService: 'library.v1.BookService', grpcMethod: 'GetBook', responseBody: '{"id":"7"}' };

    // When
    const stub = toStub(form);

    // Then
    expect(stub.request).toEqual({ method: 'POST', urlPath: '/library.v1.BookService/GetBook' });
    expect(stub.response).toEqual({ status: 200, jsonBody: { id: '7' } });
    expect((stub.metadata as { mockhub: { type: string } }).mockhub.type).toBe('grpc');
  });

  it('sends an error status as gRPC status headers without a body', () => {
    // Given
    const form = { ...emptyForm('grpc'), grpcService: 'a.B', grpcMethod: 'C', grpcStatus: 'NOT_FOUND', grpcReason: 'No book 404', responseBody: '{"id":"1"}' };

    // When
    const stub = toStub(form);

    // Then
    expect(stub.response).toEqual({ status: 200, headers: { 'grpc-status-name': 'NOT_FOUND', 'grpc-status-reason': 'No book 404' } });
  });

  it('reads the service, method and status back from a stub', () => {
    // Given
    const stub: Stub = {
      request: { method: 'POST', urlPath: '/library.v1.BookService/GetBook', bodyPatterns: [{ equalToJson: '{"id":"404"}', ignoreExtraElements: true }] },
      response: { status: 200, headers: { 'grpc-status-name': 'NOT_FOUND', 'grpc-status-reason': 'Gone', 'x-trace': '1' } },
      metadata: { mockhub: { type: 'grpc' } },
    };

    // When
    const { form, unsupported } = fromStub(stub);

    // Then
    expect(unsupported).toEqual([]);
    expect(form.kind).toBe('grpc');
    expect([form.grpcService, form.grpcMethod, form.grpcStatus, form.grpcReason]).toEqual(['library.v1.BookService', 'GetBook', 'NOT_FOUND', 'Gone']);
    expect(form.responseHeaders).toEqual([{ key: 'x-trace', value: '1' }]);
    expect(form.body[0].value).toBe('{"id":"404"}');
  });

  it('asks for the service and method before saving', () => {
    // Given
    const form = emptyForm('grpc');

    // When
    const errors = validate(form);

    // Then
    expect(errors).toContain('Pick the gRPC service, e.g. library.v1.BookService.');
    expect(errors).toContain('Pick the gRPC method, e.g. GetBook.');
    expect(validate({ ...form, grpcService: 'library.v1.BookService', grpcMethod: 'GetBook' })).toEqual([]);
  });

  it('builds a starter stub from the example response of a method', () => {
    // When
    const stub = starterStub({ name: 'library.v1.BookService', descriptor: 'library.dsc', methods: [getBook] }, getBook);

    // Then
    expect(stub).toEqual({
      name: 'GetBook',
      request: { method: 'POST', urlPath: '/library.v1.BookService/GetBook' },
      response: { status: 200, jsonBody: { id: '42', title: 'Dune' } },
      metadata: { mockhub: { type: 'grpc', folder: 'library.v1.BookService' } },
    });
  });

  it('prints a grpcurl command for the mock host', () => {
    // When
    const local = grpcurlCommand('http://localhost:8080/library', 'library.v1.BookService', 'GetBook', '{\n  "id": "it\'s"\n}');
    const hosted = grpcTarget('https://mocks.example.com/library');

    // Then
    expect(local).toBe(`grpcurl -plaintext -d '{ "id": "it'\\''s" }' localhost:8080 library.v1.BookService/GetBook`);
    expect(hosted).toEqual({ host: 'mocks.example.com:443', plaintext: false });
    expect(parseGrpcPath('/a.B/C')).toEqual({ service: 'a.B', method: 'C' });
    expect(parseGrpcPath('/shop/orders/1')).toEqual({ service: '', method: '' });
  });
});
