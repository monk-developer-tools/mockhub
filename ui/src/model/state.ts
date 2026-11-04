import type { StateContext } from '../api/types';

export type StateEdit = { properties: Record<string, string>; list: Record<string, string>[] };

const MAX_SHOWN = 3;

export function propertySummary(properties: Record<string, string>): string {
  const entries = Object.entries(properties).sort(([a], [b]) => a.localeCompare(b));
  const shown = entries.slice(0, MAX_SHOWN).map(([k, v]) => `${k}: ${v}`).join(', ');
  return entries.length > MAX_SHOWN ? `${shown} +${entries.length - MAX_SHOWN} more` : shown;
}

export function editableText(context: StateContext | null): string {
  return JSON.stringify({ properties: context?.properties ?? {}, list: context?.list ?? [] }, null, 2);
}

function scalars(value: unknown, where: string): Record<string, string> {
  if (value === undefined || value === null) return {};
  if (typeof value !== 'object' || Array.isArray(value)) throw new Error(`${where} must be an object of names and values`);
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(value)) {
    if (v === null) continue;
    if (typeof v === 'object') throw new Error(`${where}.${k} must be text, a number or true/false`);
    out[k] = String(v);
  }
  return out;
}

export function parseStateEdit(text: string): StateEdit | { error: string } {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { error: 'Enter JSON like { "properties": { "status": "open" }, "list": [] }' };
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return { error: 'Enter a JSON object with properties and list' };
  const { properties, list } = parsed as { properties?: unknown; list?: unknown };
  try {
    if (list !== undefined && list !== null && !Array.isArray(list)) throw new Error('list must be an array of objects');
    return { properties: scalars(properties, 'properties'), list: (list ?? []).map((item: unknown, i: number) => scalars(item, `list[${i}]`)) };
  } catch (e) {
    return { error: (e as Error).message };
  }
}

export function validContextName(name: string): string | null {
  const trimmed = name.trim();
  if (!trimmed) return 'Give the context a name, e.g. cart-42';
  if (trimmed.length > 200) return 'Keep the name under 200 characters';
  return null;
}
