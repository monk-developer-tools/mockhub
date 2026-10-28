import type { ApiKeyRule } from '../api/types';
import { soapContentType, type StubForm } from './stubForm';

export interface SampleRequest {
  method: string;
  path: string;
  headers: [string, string][];
  body: string;
}

export function samplePath(form: StubForm): string {
  if (form.urlMatch === 'any') return '/';
  let path = form.url || '/';
  if (form.urlMatch === 'urlPathTemplate') path = path.replace(/\{[^}]+}/g, '1');
  if (form.urlMatch === 'urlPattern' || form.urlMatch === 'urlPathPattern') {
    path = path
      .replace(/^\^|\$$/g, '')
      .replace(/\\d\+|\[0-9]\+/g, '1')
      .replace(/\[\^\/]\+|\.\+|\.\*/g, 'sample')
      .replace(/\(([^|()]*)\|[^()]*\)/g, '$1')
      .replace(/\\(.)/g, '$1')
      .replace(/[()?*+^$[\]{}|]/g, '');
  }
  const query = form.query
    .filter((q) => q.key && (q.operator === 'equalTo' || q.operator === 'contains'))
    .map((q) => `${encodeURIComponent(q.key)}=${encodeURIComponent(q.value)}`);
  if (query.length && !path.includes('?')) path += `?${query.join('&')}`;
  return path.startsWith('/') ? path : `/${path}`;
}

export const API_KEY_PLACEHOLDER = '{{apiKey}}';

export function sampleRequest(form: StubForm, apiKey?: ApiKeyRule | null): SampleRequest {
  const headers: [string, string][] = form.headers
    .filter((h) => h.key && (h.operator === 'equalTo' || h.operator === 'contains'))
    .map((h) => [h.key, h.value]);
  if (form.kind === 'soap') {
    headers.push(['Content-Type', soapContentType(form.soapVersion)]);
    if (form.soapAction) headers.push(['SOAPAction', `"${form.soapAction}"`]);
  } else if (!headers.some(([k]) => k.toLowerCase() === 'content-type') && form.body.some((b) => b.operator === 'equalToJson' || b.operator === 'matchesJsonPath')) {
    headers.push(['Content-Type', 'application/json']);
  }
  if (apiKey && !headers.some(([k]) => k.toLowerCase() === apiKey.header.toLowerCase())) headers.push([apiKey.header, apiKey.value ?? API_KEY_PLACEHOLDER]);
  const exact = form.body.find((b) => b.operator === 'equalToJson' || b.operator === 'equalToXml' || b.operator === 'equalTo');
  const method = form.method === 'ANY' ? (form.kind === 'soap' ? 'POST' : 'GET') : form.method;
  return { method, path: samplePath(form), headers, body: form.sampleBody || exact?.value || '' };
}
