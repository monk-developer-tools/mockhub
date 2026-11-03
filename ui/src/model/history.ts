import type { HeaderPair, HistoryEntry, LoggedRequest, Stub } from '../api/types';
import type { SampleRequest } from './sampleRequest';

export type MatchFilter = '' | 'matched' | 'unmatched';
export type TimeRange = '' | '15m' | '1h' | '24h' | '7d';

export interface HistoryFilters {
  text: string;
  method: string;
  status: string;
  matched: MatchFilter;
  stub: string;
  header: string;
  range: TimeRange;
  from: string;
  to: string;
}

export const NO_FILTERS: HistoryFilters = { text: '', method: '', status: '', matched: '', stub: '', header: '', range: '', from: '', to: '' };

export const PAGE_SIZE = 100;

const RANGE_MS: Record<Exclude<TimeRange, ''>, number> = { '15m': 15 * 60_000, '1h': 3_600_000, '24h': 86_400_000, '7d': 7 * 86_400_000 };

export function historyQuery(filters: HistoryFilters, now: number, offset = 0, limit = PAGE_SIZE): string {
  const params = new URLSearchParams();
  if (filters.text.trim()) params.set('q', filters.text.trim());
  if (filters.method) params.set('method', filters.method);
  if (filters.status.trim()) params.set('status', filters.status.trim());
  if (filters.matched) params.set('matched', String(filters.matched === 'matched'));
  if (filters.stub) params.set('stub', filters.stub);
  if (filters.header.trim()) params.set('header', filters.header.trim());
  if (filters.from) params.set('from', filters.from);
  else if (filters.range) params.set('from', String(now - RANGE_MS[filters.range]));
  if (filters.to) params.set('to', filters.to);
  params.set('limit', String(limit));
  if (offset > 0) params.set('offset', String(offset));
  return params.toString();
}

export function activeFilters(filters: HistoryFilters): number {
  return Object.values(filters).filter((v) => v !== '').length;
}

export function isValidStatus(status: string): boolean {
  const s = status.trim();
  return s === '' || /^[1-5]xx$/i.test(s) || /^\d{3}$/.test(s) || /^\d{3}-\d{3}$/.test(s);
}

export function isValidHeader(header: string): boolean {
  const h = header.trim();
  return h === '' || /^[^:\s]+(:.*)?$/.test(h);
}

export function headerMap(headers: HeaderPair[]): Record<string, string | string[]> {
  const out: Record<string, string | string[]> = {};
  for (const { name, value } of headers) {
    const existing = out[name];
    out[name] = existing === undefined ? value : Array.isArray(existing) ? [...existing, value] : [existing, value];
  }
  return out;
}

export function loggedRequestOf(entry: HistoryEntry): LoggedRequest {
  return {
    url: entry.url,
    absoluteUrl: entry.absoluteUrl,
    method: entry.method,
    headers: headerMap(entry.requestHeaders),
    body: entry.requestBody,
    loggedDate: entry.at,
    loggedDateString: new Date(entry.at).toISOString(),
    clientIp: entry.clientIp,
  };
}

function relativePath(url: string, basePath: string): string {
  return url === basePath ? '/' : url.startsWith(basePath + '/') || url.startsWith(basePath + '?') ? url.slice(basePath.length) : url;
}

export function stubFromRequest(request: { method: string; url: string; body: string }, basePath: string): Stub {
  const [rawPath, rawQuery] = request.url.split('?');
  const path = relativePath(rawPath, basePath);
  const stub: Stub = { name: `${request.method} ${path}`, request: { method: request.method, urlPath: path }, response: { status: 200 } };
  const req = stub.request as Record<string, unknown>;
  if (rawQuery) {
    const params: Record<string, { equalTo: string }> = {};
    new URLSearchParams(rawQuery).forEach((v, k) => (params[k] = { equalTo: v }));
    req.queryParameters = params;
  }
  const body = request.body?.trim();
  if (body) {
    try {
      JSON.parse(body);
      req.bodyPatterns = [{ equalToJson: body, ignoreExtraElements: true }];
      (stub.response as Record<string, unknown>).headers = { 'Content-Type': 'application/json' };
      (stub.response as Record<string, unknown>).jsonBody = {};
    } catch {
      if (body.startsWith('<')) {
        const op = /<(?:\w+:)?Body[^>]*>\s*<(?:\w+:)?([\w-]+)/.exec(body)?.[1];
        if (op) {
          req.bodyPatterns = [{ matchesXPath: `//*[local-name()='Body']/*[local-name()='${op}']` }];
          stub.metadata = { mockhub: { type: 'soap', operation: op, sampleRequest: { body } } };
        }
      }
    }
  }
  return stub;
}

const SKIPPED_HEADERS = new Set(['host', 'content-length', 'connection', 'accept-encoding', 'user-agent', 'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host', 'x-real-ip']);

export function replayRequest(request: { method: string; url: string; headers: HeaderPair[]; body: string }, basePath: string): SampleRequest {
  const path = relativePath(request.url, basePath);
  const merged = new Map<string, string[]>();
  for (const { name, value } of request.headers) {
    if (SKIPPED_HEADERS.has(name.toLowerCase())) continue;
    merged.set(name, [...(merged.get(name) ?? []), value]);
  }
  const headers = [...merged].map(([k, v]): [string, string] => [k, v.join(', ')]);
  return { method: request.method, path: path.startsWith('/') || path.startsWith('?') ? path : `/${path}`, headers, body: request.body ?? '' };
}

export function statusTone(status: number): string {
  if (status <= 0 || status >= 500) return 'bad';
  if (status >= 400) return 'warn';
  if (status >= 300) return 'info';
  return 'ok';
}
