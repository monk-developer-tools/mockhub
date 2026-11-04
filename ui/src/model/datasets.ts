import type { Dataset, DatasetFormat } from '../api/types';

export function formatOf(fileName: string, text: string): DatasetFormat {
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.csv')) return 'csv';
  if (lower.endsWith('.ndjson') || lower.endsWith('.jsonl')) return 'ndjson';
  if (lower.endsWith('.json')) return 'json';
  const start = text.trimStart();
  if (start.startsWith('[')) return 'json';
  if (start.startsWith('{')) return 'ndjson';
  return 'csv';
}

export function sizeLabel(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} bytes`;
}

export function slugifyKey(text: string): string {
  return text.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^[-_]+|[-_]+$/g, '').slice(0, 64);
}

export function fieldList(text: string): string[] {
  return [...new Set(text.split(',').map((f) => f.trim()).filter(Boolean))];
}

function lookupField(dataset: Dataset): string {
  return dataset.identity[0] ?? dataset.indexes[0] ?? dataset.columns[0]?.name ?? 'id';
}

function readField(dataset: Dataset, field: string): string {
  return dataset.columns.find((c) => c.name !== field)?.name ?? field;
}

export function helperExamples(dataset: Dataset): { label: string; expression: string }[] {
  const field = lookupField(dataset);
  const read = readField(dataset, field);
  const filter = dataset.indexes.find((f) => f !== field) ?? dataset.columns.find((c) => c.name !== field)?.name ?? field;
  return [
    { label: 'One record as JSON', expression: `{{{dataset-one '${dataset.key}' ${field}=request.path.${field}}}}` },
    { label: "One record's field", expression: `{{#with (dataset-one '${dataset.key}' ${field}=request.path.${field})}}{{${read}}}{{else}}Not found{{/with}}` },
    { label: 'Matching records as JSON', expression: `{{{dataset '${dataset.key}' ${filter}=request.query.${filter} limit=20}}}` },
  ];
}

export function filtersFrom(rows: { field: string; value: string }[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const row of rows) if (row.field.trim()) out[row.field.trim()] = row.value;
  return out;
}

export function lookupExpression(key: string, filters: Record<string, string>, one: boolean): string {
  const quote = (v: string) => (v.includes("'") ? `"${v}"` : `'${v}'`);
  const args = Object.entries(filters).map(([k, v]) => ` ${k}=${quote(v)}`).join('');
  return one ? `{{{dataset-one '${key}'${args}}}}` : `{{{dataset '${key}'${args}}}}`;
}
