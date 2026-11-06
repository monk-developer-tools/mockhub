import catalogue from './dynamicValues.json' with { type: 'json' };

export type DynamicScope = 'response' | 'callout' | 'bin';

export interface DynamicValue {
  label: string;
  expression: string;
  hint?: string;
  input?: 'xml' | 'soap' | 'form';
}

export interface DynamicGroup {
  id: string;
  name: string;
  items: DynamicValue[];
}

interface CatalogueValue extends DynamicValue {
  bin?: string | false;
  binHint?: string;
}

interface CatalogueGroup {
  id: string;
  name: string;
  items: CatalogueValue[];
  scope?: DynamicScope;
  bin?: false;
}

const GROUPS = (catalogue as { groups: CatalogueGroup[] }).groups;
const REQUEST = /\$R\./g;

function value({ label, expression, hint, input }: CatalogueValue, r: string): DynamicValue {
  return { label, expression: expression.replace(REQUEST, r), ...(hint ? { hint } : {}), ...(input ? { input } : {}) };
}

function inBin(i: CatalogueValue): DynamicValue[] {
  if (i.bin === false) return [];
  const expression = typeof i.bin === 'string' ? i.bin : i.expression;
  return [value({ ...i, expression, hint: i.binHint ?? i.hint }, 'request.')];
}

export function dynamicValues(scope: DynamicScope): DynamicGroup[] {
  if (scope === 'bin') {
    return GROUPS.filter((g) => g.scope !== 'callout' && g.bin !== false)
      .map((g) => ({ id: g.id, name: g.name, items: g.items.flatMap(inBin) }))
      .filter((g) => g.items.length > 0);
  }
  const r = scope === 'callout' ? 'originalRequest.' : 'request.';
  return GROUPS.filter((g) => !g.scope || g.scope === scope).map((g) => ({
    id: g.id,
    name: g.name,
    items: g.items.map((i) => value(i, r)),
  }));
}

export function searchDynamicValues(groups: DynamicGroup[], query: string): DynamicGroup[] {
  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return groups;
  return groups
    .map((g) => ({
      ...g,
      items: g.items.filter((i) => {
        const text = `${g.name} ${i.label} ${i.expression} ${i.hint ?? ''}`.toLowerCase();
        return words.every((w) => text.includes(w));
      }),
    }))
    .filter((g) => g.items.length > 0);
}
