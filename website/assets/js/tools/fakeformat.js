function flatten(value, prefix, out) {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    for (const [key, inner] of Object.entries(value)) flatten(inner, prefix ? `${prefix}.${key}` : key, out);
  } else {
    out[prefix] = Array.isArray(value) ? JSON.stringify(value) : value;
  }
  return out;
}

export function rows(records) {
  const flat = records.map((r) => flatten(r, '', {}));
  const columns = [];
  for (const row of flat) for (const key of Object.keys(row)) if (!columns.includes(key)) columns.push(key);
  return { columns, flat };
}

const csvCell = (value) => {
  if (value === null || value === undefined) return '';
  const text = String(value);
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
};

export function toCsv(records) {
  const { columns, flat } = rows(records);
  return [columns.map(csvCell).join(','), ...flat.map((row) => columns.map((c) => csvCell(row[c])).join(','))].join('\n');
}

const identifier = (name) => `"${String(name).replace(/"/g, '""')}"`;

const sqlValue = (value) => {
  if (value === null || value === undefined) return 'NULL';
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
  return `'${String(value).replace(/'/g, "''")}'`;
};

export function toSql(records, table = 'records') {
  const { columns, flat } = rows(records);
  const head = `INSERT INTO ${identifier(table.trim() || 'records')} (${columns.map((c) => identifier(c.replace(/\./g, '_'))).join(', ')}) VALUES`;
  return flat.map((row) => `${head} (${columns.map((c) => sqlValue(row[c])).join(', ')});`).join('\n');
}
