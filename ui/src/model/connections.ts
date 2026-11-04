import type { Connection, ConnectionInput, ConnectionTest, ConnectionType } from '../api/types';

export interface ConnectionForm {
  key: string;
  name: string;
  type: ConnectionType;
  tokenUrl: string;
  clientId: string;
  scope: string;
  audience: string;
  clientAuth: 'basic' | 'body';
  headerName: string;
  headerPrefix: string;
  secret: string;
}

const KEY = /^[a-z0-9][a-z0-9_-]{0,49}$/;

export function connectionForm(c?: Connection): ConnectionForm {
  return {
    key: c?.key ?? '',
    name: c?.name ?? '',
    type: c?.type ?? 'oauth2',
    tokenUrl: c?.tokenUrl ?? '',
    clientId: c?.clientId ?? '',
    scope: c?.scope ?? '',
    audience: c?.audience ?? '',
    clientAuth: c?.clientAuth ?? 'basic',
    headerName: c?.headerName ?? 'Authorization',
    headerPrefix: c?.headerPrefix ?? 'Bearer ',
    secret: '',
  };
}

export function withType(form: ConnectionForm, type: ConnectionType): ConnectionForm {
  if (type === form.type) return form;
  const defaults = type === 'header' ? { headerName: 'X-Api-Key', headerPrefix: '' } : { headerName: 'Authorization', headerPrefix: 'Bearer ' };
  const untouched = form.type === 'oauth2' ? form.headerName === 'Authorization' && form.headerPrefix === 'Bearer ' : form.headerName === 'X-Api-Key' && form.headerPrefix === '';
  return { ...form, type, ...(untouched ? defaults : {}) };
}

export function slugKey(name: string): string {
  return name.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 50);
}

export function connectionProblem(form: ConnectionForm, editing: boolean): string | null {
  if (!editing && !KEY.test(form.key)) return 'The key must be lowercase letters, digits, dashes or underscores, e.g. payments-api';
  if (!form.headerName.trim() || /[\s:]/.test(form.headerName.trim())) return 'Enter a header name without spaces, e.g. Authorization';
  if (form.type === 'oauth2') {
    if (!/^(https?:\/\/|\/)/.test(form.tokenUrl.trim())) return 'The token URL must start with https:// or http://, or be a path on this server';
    if (!form.clientId.trim()) return 'Enter the client id';
  }
  if (!editing && !form.secret) return form.type === 'oauth2' ? 'Enter the client secret' : 'Enter the header value';
  return null;
}

export function connectionInput(form: ConnectionForm, editing: boolean): ConnectionInput {
  const out: ConnectionInput = {
    name: form.name.trim() || form.key,
    type: form.type,
    headerName: form.headerName.trim(),
    headerPrefix: form.headerPrefix,
  };
  if (!editing) out.key = form.key;
  if (form.type === 'oauth2') {
    out.tokenUrl = form.tokenUrl.trim();
    out.clientId = form.clientId.trim();
    out.scope = form.scope.trim();
    out.audience = form.audience.trim();
    out.clientAuth = form.clientAuth;
  }
  if (form.secret) out.secret = form.secret;
  return out;
}

export function calloutExample(key: string): string {
  return JSON.stringify({ name: 'mockhub-callout', parameters: { url: 'https://partner.example.com/hooks', connection: key, retry: { count: 3 } } });
}

export function proxyExample(key: string): string {
  return JSON.stringify({ proxyBaseUrl: 'https://api.example.com', transformers: ['mockhub-connection'], transformerParameters: { connection: key } });
}

export function describeTest(result: ConnectionTest): string {
  const parts = [`${result.headerName}: ${result.preview}`];
  if (result.expiresIn) parts.push(`expires in ${result.expiresIn >= 120 ? `${Math.round(result.expiresIn / 60)} minutes` : `${result.expiresIn} seconds`}`);
  if (result.scope) parts.push(`scope ${result.scope}`);
  return parts.join(' · ');
}
