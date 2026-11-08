import type { Stub } from '../api/types';
import { grpcurlCommand } from './grpc';
import { GRAPHQL_TRANSFORMER } from './stubForm';

export interface ReadyCall {
  label: string;
  command: string;
  url?: string;
}

const quote = (text: string) => `'${text.replace(/'/g, `'\\''`)}'`;

type Obj = Record<string, unknown>;
const obj = (value: unknown): Obj => (value && typeof value === 'object' ? (value as Obj) : {});

export function samplePathOf(request: Obj): string | null {
  const exact = request.urlPath ?? request.url;
  if (typeof exact === 'string') return exact.split('?')[0];
  const template = request.urlPathTemplate;
  if (typeof template === 'string') return template.replace(/\{([^}]+)\}/g, (_, name: string) => (/id$/i.test(name) ? '42' : 'sample'));
  const pattern = request.urlPathPattern ?? request.urlPattern;
  if (typeof pattern === 'string') {
    const literal = pattern.replace(/\(\/\.\*\)\?$/, '').replace(/\(\.\*\)\??$/, '').replace(/\\(.)/g, '$1');
    if (!/[*+?()[\]{}|^$]/.test(literal)) return `${literal || ''}/test`;
  }
  return null;
}

export function readyCalls(stubs: Stub[], mockUrl: string, limit = 4): ReadyCall[] {
  const calls: ReadyCall[] = [];
  for (const stub of stubs) {
    if (calls.length >= limit) break;
    const request = obj(stub.request);
    const response = obj(stub.response);
    const meta = obj(obj(stub.metadata).mockhub);
    const path = samplePathOf(request);
    if (meta.type === 'grpc' && typeof request.urlPath === 'string') {
      const [, service, method] = /^\/([^/]+)\/([^/]+)$/.exec(request.urlPath) ?? [];
      if (service && method) calls.push({ label: `${service}/${method}`, command: grpcurlCommand(mockUrl, service, method, '{}') });
      continue;
    }
    if (path === null) {
      if (response.proxyBaseUrl) calls.push({ label: `Any call, forwarded to ${String(response.proxyBaseUrl)}`, command: `curl -i ${mockUrl}/`, url: `${mockUrl}/` });
      continue;
    }
    const transformers = Array.isArray(response.transformers) ? response.transformers : [];
    if (transformers.includes(GRAPHQL_TRANSFORMER)) {
      calls.push({ label: `GraphQL ${path}`, command: `curl -s ${mockUrl}${path} -H 'Content-Type: application/json' -d ${quote('{"query":"{ __typename }"}')}` });
      continue;
    }
    const method = String(request.method ?? 'GET').toUpperCase();
    const verb = method === 'ANY' ? (Array.isArray(stub.serveEventListeners) || /hook/i.test(String(stub.name)) ? 'POST' : 'GET') : method;
    const body = ['POST', 'PUT', 'PATCH'].includes(verb) ? ` -H 'Content-Type: application/json' -d '{}'` : '';
    calls.push({
      label: `${verb} ${path}`,
      command: `curl -i${verb === 'GET' ? '' : ` -X ${verb}`} ${mockUrl}${path}${body}`,
      url: verb === 'GET' ? `${mockUrl}${path}` : undefined,
    });
  }
  return calls;
}
