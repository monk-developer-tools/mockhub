import catalogue from './dynamicValues.json' with { type: 'json' };

export type DynamicScope = 'response' | 'callout';

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

interface CatalogueGroup extends DynamicGroup {
  scope?: DynamicScope;
}

const GROUPS = (catalogue as { groups: CatalogueGroup[] }).groups;
const REQUEST = /\$R\./g;

export function dynamicValues(scope: DynamicScope): DynamicGroup[] {
  const r = scope === 'callout' ? 'originalRequest.' : 'request.';
  return GROUPS.filter((g) => !g.scope || g.scope === scope).map((g) => ({
    id: g.id,
    name: g.name,
    items: g.items.map((i) => ({ ...i, expression: i.expression.replace(REQUEST, r) })),
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
