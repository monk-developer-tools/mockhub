import type { JsonObject, Stub } from '../api/types';
import type { KeyValue, StubForm } from './stubForm';

export type GrpcMethodKind = 'unary' | 'server-streaming' | 'client-streaming' | 'bidi-streaming';

export interface GrpcMethod {
  name: string;
  path: string;
  kind: GrpcMethodKind;
  requestType: string;
  responseType: string;
  requestExample: JsonObject;
  responseExample: JsonObject;
}

export interface GrpcService {
  name: string;
  descriptor: string;
  methods: GrpcMethod[];
}

export interface GrpcDescriptor {
  name: string;
  size: number;
  files: string[];
  services: string[];
}

export interface GrpcCatalog {
  descriptors: GrpcDescriptor[];
  services: GrpcService[];
}

export const GRPC_STATUSES = [
  'OK',
  'CANCELLED',
  'UNKNOWN',
  'INVALID_ARGUMENT',
  'DEADLINE_EXCEEDED',
  'NOT_FOUND',
  'ALREADY_EXISTS',
  'PERMISSION_DENIED',
  'RESOURCE_EXHAUSTED',
  'FAILED_PRECONDITION',
  'ABORTED',
  'OUT_OF_RANGE',
  'UNIMPLEMENTED',
  'INTERNAL',
  'UNAVAILABLE',
  'DATA_LOSS',
  'UNAUTHENTICATED',
] as const;

export const GRPC_STATUS_HEADER = 'grpc-status-name';
export const GRPC_REASON_HEADER = 'grpc-status-reason';

const NAME = /^[A-Za-z_][A-Za-z0-9_]*$/;
const SERVICE = /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$/;

export const grpcPath = (service: string, method: string) => `/${service}/${method}`;

export function parseGrpcPath(path: string): { service: string; method: string } {
  const match = /^\/([^/]+)\/([^/]+)$/.exec(path);
  return match ? { service: match[1], method: match[2] } : { service: '', method: '' };
}

const isStatusHeader = (key: string) => [GRPC_STATUS_HEADER, GRPC_REASON_HEADER].includes(key.trim().toLowerCase());

export function readGrpc(form: StubForm): StubForm {
  const { service, method } = parseGrpcPath(form.url);
  const header = (name: string) => form.responseHeaders.find((h) => h.key.trim().toLowerCase() === name)?.value ?? '';
  const status = header(GRPC_STATUS_HEADER).trim().toUpperCase();
  return {
    ...form,
    grpcService: service,
    grpcMethod: method,
    grpcStatus: (GRPC_STATUSES as readonly string[]).includes(status) ? status : 'OK',
    grpcReason: header(GRPC_REASON_HEADER),
    responseHeaders: form.responseHeaders.filter((h) => !isStatusHeader(h.key)),
  };
}

export function applyGrpc(form: StubForm): StubForm {
  const headers: KeyValue[] = form.responseHeaders.filter((h) => !isStatusHeader(h.key) && h.key.trim().toLowerCase() !== 'content-type');
  if (form.grpcStatus !== 'OK') {
    headers.push({ key: GRPC_STATUS_HEADER, value: form.grpcStatus });
    if (form.grpcReason.trim()) headers.push({ key: GRPC_REASON_HEADER, value: form.grpcReason.trim() });
  }
  const failing = form.grpcStatus !== 'OK';
  return {
    ...form,
    method: 'POST',
    urlMatch: 'urlPath',
    url: grpcPath(form.grpcService.trim(), form.grpcMethod.trim()),
    query: [],
    cookies: [],
    responseMode: 'direct',
    status: '200',
    statusMessage: '',
    responseHeaders: headers,
    bodyType: failing ? 'none' : 'json',
    responseBody: failing ? '' : form.responseBody,
    alternatives: [],
  };
}

export function validateGrpc(form: StubForm): string[] {
  const errors: string[] = [];
  if (!SERVICE.test(form.grpcService.trim())) errors.push('Pick the gRPC service, e.g. library.v1.BookService.');
  if (!NAME.test(form.grpcMethod.trim())) errors.push('Pick the gRPC method, e.g. GetBook.');
  if (!(GRPC_STATUSES as readonly string[]).includes(form.grpcStatus)) errors.push('Pick a gRPC status.');
  return errors;
}

export function starterStub(service: GrpcService, method: GrpcMethod): Stub {
  return {
    name: `${method.name}`,
    request: { method: 'POST', urlPath: method.path },
    response: { status: 200, jsonBody: method.responseExample },
    metadata: { mockhub: { type: 'grpc', folder: service.name } },
  };
}

export function grpcTarget(mockUrl: string): { host: string; plaintext: boolean } {
  try {
    const url = new URL(mockUrl);
    const tls = url.protocol === 'https:';
    return { host: `${url.hostname}:${url.port || (tls ? 443 : 80)}`, plaintext: !tls };
  } catch {
    return { host: 'localhost:8080', plaintext: true };
  }
}

export function grpcurlCommand(mockUrl: string, service: string, method: string, body: string): string {
  const { host, plaintext } = grpcTarget(mockUrl);
  const data = body.trim() ? body.replace(/\s+/g, ' ').trim() : '{}';
  return `grpcurl ${plaintext ? '-plaintext ' : ''}-d '${data.replace(/'/g, `'\\''`)}' ${host} ${service}/${method}`;
}
