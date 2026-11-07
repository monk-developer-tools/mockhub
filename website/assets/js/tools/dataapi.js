import { MAX_RECORDS, parseRecords } from './instant.js';

export const MAX_ROUTES = 20;
const ROUTE = /^[a-z0-9][a-z0-9-]{0,59}$/;

export function parseJsonDoc(text) {
  const raw = String(text ?? '').trim();
  if (!raw) throw new Error('Paste some JSON first.');
  try {
    return JSON.parse(raw);
  } catch (e) {
    throw new Error(`That isn't valid JSON: ${e.message}`);
  }
}

const isRecords = (value) => Array.isArray(value) && value.every((item) => item !== null && typeof item === 'object' && !Array.isArray(item));

export function planRoutes(data, rootName = 'items') {
  if (Array.isArray(data)) {
    if (!isRecords(data)) throw new Error('A top-level list must hold JSON objects, one per record.');
    if (data.length > MAX_RECORDS) throw new Error(`Start with up to ${MAX_RECORDS} records in all.`);
    return { routes: [{ name: rootName, kind: 'crud', count: data.length }], skipped: [] };
  }
  if (data === null || typeof data !== 'object' || !Object.keys(data).length) {
    throw new Error('Paste a JSON object whose keys become routes, like {"posts": [{"id": 1}]}, or a list of records.');
  }
  const keys = Object.keys(data);
  if (keys.length > MAX_ROUTES) throw new Error(`Keep it to ${MAX_ROUTES} top-level keys; each becomes a route.`);
  const routes = [];
  const skipped = [];
  let total = 0;
  for (const name of keys) {
    if (!ROUTE.test(name)) {
      skipped.push(`${name}: route names are lowercase letters, digits and dashes, like posts or order-items`);
      continue;
    }
    if (isRecords(data[name])) {
      total += data[name].length;
      routes.push({ name, kind: 'crud', count: data[name].length });
    } else {
      routes.push({ name, kind: 'get' });
    }
  }
  if (total > MAX_RECORDS) throw new Error(`Start with up to ${MAX_RECORDS} records in all.`);
  return { routes, skipped };
}

export function routeLines({ name, kind }) {
  if (kind === 'get') return [['GET', `/${name}`]];
  return [['GET', `/${name}`], ['POST', `/${name}`], ['GET', `/${name}/{id}`], ['PUT', `/${name}/{id}`], ['PATCH', `/${name}/{id}`], ['DELETE', `/${name}/{id}`]];
}

export function routesFromStubs(stubs) {
  return (stubs ?? []).map((stub) => {
    const resource = (stub.response?.transformers ?? []).includes('mockhub-crud') ? stub.response?.transformerParameters?.resource : null;
    if (resource) return { name: resource, kind: 'crud' };
    const path = stub.request?.urlPath ?? stub.request?.urlPathTemplate ?? stub.request?.url;
    return path && (stub.request?.method ?? 'GET') === 'GET' ? { name: path.replace(/^\//, ''), kind: 'get' } : null;
  }).filter(Boolean);
}

export function orderRoutes(routes, order) {
  const rank = (r) => {
    const at = order.indexOf(r.name);
    return at < 0 ? order.length : at;
  };
  return routes.map((r, i) => ({ r, i })).sort((a, b) => rank(a.r) - rank(b.r) || a.i - b.i).map(({ r }) => r);
}

export function csvPreview(text, limit = 20) {
  const records = parseRecords(text);
  const columns = [...new Set(records.flatMap((r) => Object.keys(r)))];
  return { columns, rows: records.slice(0, limit), total: records.length, records };
}

export function columnsOf(records, max = 8) {
  const columns = [...new Set(records.flatMap((r) => Object.keys(r ?? {})))];
  const ordered = columns.includes('id') ? ['id', ...columns.filter((c) => c !== 'id')] : columns;
  return ordered.slice(0, max);
}

export function cell(value) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export function recordBody(text) {
  const value = parseJsonDoc(text);
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('A record is one JSON object, like {"title": "Hello"}.');
  return value;
}
