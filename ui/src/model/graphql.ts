import type { Stub } from '../api/types';
import { emptyForm, toStub } from './stubForm';

export interface GraphqlOperation {
  kind: 'query' | 'mutation';
  field: string;
  operationName: string;
  description: string;
  query: string;
  variables: Record<string, unknown>;
}

export function operationNameOf(query: string): string | null {
  const m = /^\s*(?:#[^\n]*\n\s*)*(?:query|mutation|subscription)\s+([A-Za-z_][A-Za-z0-9_]*)/.exec(query);
  return m ? m[1] : null;
}

export function parseVariables(text: string): { ok: true; value: Record<string, unknown> } | { ok: false; error: string } {
  if (!text.trim()) return { ok: true, value: {} };
  try {
    const value = JSON.parse(text);
    if (value === null || typeof value !== 'object' || Array.isArray(value)) return { ok: false, error: 'Variables must be a JSON object' };
    return { ok: true, value };
  } catch (e) {
    return { ok: false, error: `Variables aren't valid JSON: ${e instanceof Error ? e.message : String(e)}` };
  }
}

export function requestBody(query: string, variables: Record<string, unknown>): string {
  const operationName = operationNameOf(query);
  return JSON.stringify({ query, ...(operationName ? { operationName } : {}), variables });
}

export function operationStub(path: string, operationName: string, variables: Record<string, unknown>, responseBody: string): Stub {
  const form = emptyForm('graphql');
  const keys = Object.keys(variables);
  form.name = keys.length ? `${operationName} (${keys.map((k) => `${k} ${short(variables[k])}`).join(', ')})` : operationName;
  form.url = path;
  form.graphqlOperation = operationName;
  form.graphqlVariables = keys.length ? JSON.stringify(variables) : '';
  form.priority = '1';
  form.folder = 'GraphQL';
  form.responseBody = responseBody;
  return toStub(form);
}

function short(value: unknown): string {
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  return text.length > 20 ? `${text.slice(0, 20)}…` : text;
}
