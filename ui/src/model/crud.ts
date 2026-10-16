import type { Stub } from '../api/types';

export interface CrudInfo {
  resource: string;
  idField: string;
  idType: 'uuid' | 'number';
}

export function crudOf(stub: Stub): CrudInfo | null {
  const meta = (stub.metadata as Record<string, unknown> | undefined)?.mockhub as Record<string, unknown> | undefined;
  const crud = meta?.crud as Record<string, unknown> | undefined;
  if (!crud || typeof crud.resource !== 'string') return null;
  return {
    resource: crud.resource,
    idField: typeof crud.idField === 'string' ? crud.idField : 'id',
    idType: crud.idType === 'number' ? 'number' : 'uuid',
  };
}

export function crudEndpoints(resource: string, idField = 'id') {
  const item = `/${resource}/{${idField}}`;
  return [
    { method: 'GET', path: `/${resource}`, does: 'List records. Filter with ?field=value, sort with _sort and _order, page with _limit and _offset.' },
    { method: 'GET', path: item, does: 'Read one record, or 404.' },
    { method: 'POST', path: `/${resource}`, does: `Create a record. The ${idField} is generated unless you send one.` },
    { method: 'PUT', path: item, does: 'Replace a record.' },
    { method: 'PATCH', path: item, does: 'Change some fields. Send null to remove a field.' },
    { method: 'DELETE', path: item, does: 'Delete a record.' },
  ];
}

export function slugifyResource(text: string) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9/]+/g, '-')
    .replace(/\/+/g, '/')
    .replace(/(^[-/]+)|([-/]+$)/g, '');
}
