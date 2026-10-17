import type { HeaderPair, LoggedRequest, PlaygroundEntry, PlaygroundRequest, Project, Stub } from '../api/types';

export const PLAYGROUND_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];

export function statusTone(status: number | undefined): 'ok' | 'redirect' | 'client' | 'server' | 'none' {
  if (!status) return 'none';
  if (status < 300) return 'ok';
  if (status < 400) return 'redirect';
  if (status < 500) return 'client';
  return 'server';
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function headerValue(headers: HeaderPair[], name: string): string {
  return headers.find((h) => h.name.toLowerCase() === name.toLowerCase())?.value ?? '';
}

export function prettyBody(body: string, contentType: string): { text: string; language: 'json' | 'xml' | 'text' } {
  const trimmed = body.trim();
  if (contentType.includes('json') || trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      return { text: JSON.stringify(JSON.parse(trimmed), null, 2), language: 'json' };
    } catch {
      return { text: body, language: 'text' };
    }
  }
  if (contentType.includes('xml') || trimmed.startsWith('<')) return { text: body, language: 'xml' };
  return { text: body, language: 'text' };
}

export function absoluteUrl(url: string, origin: string): string {
  return url.startsWith('/') ? origin + url : url;
}

export function splitUrl(url: string): { base: string; params: HeaderPair[] } {
  const q = url.indexOf('?');
  if (q < 0) return { base: url, params: [] };
  const params = url
    .slice(q + 1)
    .split('&')
    .filter(Boolean)
    .map((pair) => {
      const eq = pair.indexOf('=');
      const decode = (s: string) => {
        try {
          return decodeURIComponent(s.replace(/\+/g, ' '));
        } catch {
          return s;
        }
      };
      return eq < 0 ? { name: decode(pair), value: '' } : { name: decode(pair.slice(0, eq)), value: decode(pair.slice(eq + 1)) };
    });
  return { base: url.slice(0, q), params };
}

export function joinUrl(base: string, params: HeaderPair[]): string {
  const query = params
    .filter((p) => p.name.trim())
    .map((p) => `${encodeURIComponent(p.name.trim())}=${encodeURIComponent(p.value)}`)
    .join('&');
  return query ? `${base}?${query}` : base;
}

export function curlOf(request: PlaygroundRequest, origin: string): string {
  const quote = (s: string) => `'${s.replace(/'/g, `'\\''`)}'`;
  const parts = [`curl -X ${request.method} ${quote(absoluteUrl(request.url, origin))}`];
  for (const h of request.headers) if (h.name.trim()) parts.push(`  -H ${quote(`${h.name}: ${h.value}`)}`);
  if (request.body && request.method !== 'GET' && request.method !== 'HEAD') parts.push(`  --data-raw ${quote(request.body)}`);
  return parts.join(' \\\n');
}

export function pathOf(url: string): string {
  if (url.startsWith('/')) return splitUrl(url).base;
  try {
    return new URL(url.includes('://') ? url : `https://${url}`).pathname;
  } catch {
    return '/';
  }
}

export function projectFor(url: string, origin: string, projects: Project[]): Project | undefined {
  const absolute = absoluteUrl(url, origin);
  return projects.find((p) => absolute.startsWith(p.mockUrl + '/') || absolute === p.mockUrl);
}

export function relativePath(url: string, project: Project | undefined): string {
  const path = pathOf(url) || '/';
  if (project && (path === project.basePath || path.startsWith(project.basePath + '/'))) {
    return path.slice(project.basePath.length) || '/';
  }
  return path;
}

export function stubFromEntry(entry: PlaygroundEntry, path: string, name: string): Stub {
  const response = entry.response;
  const contentType = response ? headerValue(response.headers, 'Content-Type') : '';
  const body = response?.body ?? '';
  const pretty = prettyBody(body, contentType);
  const responseDef: Record<string, unknown> = { status: response?.status ?? 200 };
  if (contentType) responseDef.headers = { 'Content-Type': contentType };
  if (pretty.language === 'json') responseDef.jsonBody = JSON.parse(pretty.text);
  else if (body) responseDef.body = body;
  return {
    name,
    request: { method: entry.request.method, urlPath: path },
    response: responseDef,
  } as Stub;
}

export function entryLabel(entry: PlaygroundEntry): string {
  const path = pathOf(entry.request.url);
  return path === '/' && !entry.request.url.startsWith('/') ? entry.request.url : path;
}

const NOT_REPLAYED = ['host', 'content-length', 'connection', 'accept-encoding', 'user-agent', 'x-real-ip', 'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host', 'x-forwarded-port'];

export function replayOf(request: LoggedRequest): PlaygroundRequest {
  const headers: HeaderPair[] = [];
  for (const [name, value] of Object.entries(request.headers ?? {})) {
    if (NOT_REPLAYED.includes(name.toLowerCase())) continue;
    for (const v of Array.isArray(value) ? value : [value]) headers.push({ name, value: v });
  }
  return { method: request.method, url: request.url, headers, body: request.body ?? '' };
}
