export type Row = Record<string, unknown>;

export interface TableBody {
  path: string | null;
  rows: Row[];
  columns: string[];
}

const isRow = (v: unknown): v is Row => typeof v === 'object' && v !== null && !Array.isArray(v);

export function tableOf(text: string): TableBody | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return null;
  }
  if (Array.isArray(parsed)) return parsed.length > 0 && parsed.every(isRow) ? build(null, parsed) : null;
  if (!isRow(parsed)) return null;
  const lists = Object.entries(parsed).filter(([, v]) => Array.isArray(v) && v.length > 0 && v.every(isRow));
  return lists.length === 1 ? build(lists[0][0], lists[0][1] as Row[]) : null;
}

function build(path: string | null, rows: Row[]): TableBody {
  const columns: string[] = [];
  for (const row of rows) for (const key of Object.keys(row)) if (!columns.includes(key)) columns.push(key);
  return { path, rows, columns };
}

export function cellText(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function sample(rows: Row[], column: string): unknown {
  return rows.map((r) => r[column]).find((v) => v !== null && v !== undefined);
}

export function cellValue(text: string, original: unknown, columnSample: unknown): unknown {
  const like = original === null || original === undefined ? columnSample : original;
  const trimmed = text.trim();
  if (trimmed === '' && (like === null || like === undefined || typeof like !== 'string')) return null;
  if (typeof like === 'number' && trimmed !== '' && !Number.isNaN(Number(trimmed))) return Number(trimmed);
  if (typeof like === 'boolean' && (trimmed === 'true' || trimmed === 'false')) return trimmed === 'true';
  if (typeof like === 'object' && like !== null) {
    try {
      return JSON.parse(trimmed);
    } catch {
      return text;
    }
  }
  return text;
}

export function editCell(rows: Row[], index: number, column: string, text: string): Row[] {
  const columnSample = sample(rows, column);
  return rows.map((row, i) => {
    if (i !== index) return row;
    const value = cellValue(text, row[column], columnSample);
    const next = { ...row };
    if (value === null && !(column in row)) return row;
    next[column] = value;
    return next;
  });
}

export function emptyRow(columns: string[]): Row {
  return Object.fromEntries(columns.map((c) => [c, null]));
}

export function withRows(text: string, path: string | null, rows: Row[]): string {
  if (path === null) return JSON.stringify(rows, null, 2);
  const parsed = JSON.parse(text) as Row;
  return JSON.stringify({ ...parsed, [path]: rows }, null, 2);
}

const PLACEHOLDER = '"__mockhub_dataset__"';

export function pathParam(urlMatch: string, url: string): string | null {
  if (urlMatch !== 'urlPathTemplate') return null;
  const match = /\{([A-Za-z_][A-Za-z0-9_]*)\}\/?$/.exec(url);
  return match ? match[1] : null;
}

export function datasetBody(text: string, path: string | null, key: string, param: string | null, field: string): string {
  const helper = param ? `{{{dataset-one '${key}' ${field}=request.path.${param}}}}` : `{{{dataset '${key}' limit=1000}}}`;
  if (path === null) return helper;
  const parsed = JSON.parse(text) as Row;
  return JSON.stringify({ ...parsed, [path]: '__mockhub_dataset__' }, null, 2).replace(PLACEHOLDER, helper);
}

export function keyFrom(url: string): string {
  const parts = url.split('/').filter((p) => p && !p.startsWith('{') && !p.includes('*') && !p.includes('('));
  const last = parts[parts.length - 1] ?? 'records';
  return last.toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^[-_]+|[-_]+$/g, '').slice(0, 64) || 'records';
}
