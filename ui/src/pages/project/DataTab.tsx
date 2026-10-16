import { ArrowClockwise, Database, MagnifyingGlass, PencilSimple, Plus, Trash } from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { DataResource } from '../../api/types';
import { CodeEditor } from '../../components/CodeEditor';
import { EmptyArt } from '../../components/Illustrations';
import { Modal } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { crudEndpoints } from '../../model/crud';
import { useApp } from '../../state/AppContext';
import { CrudDialog } from './CrudDialog';
import { useProject } from './ProjectPage';

type Row = Record<string, unknown>;

const MAX_COLUMNS = 6;

function cell(value: unknown) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export function DataTab() {
  const { project, reloadProject } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [resources, setResources] = useState<DataResource[] | null>(null);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<{ row: Row | null } | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);

  const selected = params.get('resource') ?? resources?.[0]?.name ?? '';
  const current = resources?.find((r) => r.name === selected);

  const loadResources = useCallback(async () => {
    try {
      setResources((await api.dataResources(project.id)).resources);
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, toast.fail]);

  const loadRows = useCallback(async () => {
    if (!selected) {
      setRows([]);
      return;
    }
    try {
      setRows(await api.dataRecords(project.id, selected));
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, selected, toast.fail]);

  useEffect(() => {
    loadResources();
  }, [loadResources]);

  useEffect(() => {
    loadRows();
  }, [loadRows]);

  const refresh = async () => {
    await Promise.all([loadResources(), loadRows()]);
  };

  const columns = useMemo(() => {
    const keys: string[] = [];
    for (const r of rows ?? []) for (const k of Object.keys(r)) if (!keys.includes(k)) keys.push(k);
    const id = current?.idField ?? 'id';
    return [id, ...keys.filter((k) => k !== id)].slice(0, MAX_COLUMNS);
  }, [rows, current]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (rows ?? []).filter((r) => !q || JSON.stringify(r).toLowerCase().includes(q));
  }, [rows, query]);

  const idOf = (row: Row) => cell(row[current?.idField ?? 'id']);

  const remove = async (row: Row) => {
    try {
      await api.deleteRecord(project.id, selected, idOf(row));
      toast.notify('Record deleted');
      refresh();
    } catch (e) {
      toast.fail(e);
    }
  };

  const clear = async () => {
    try {
      await api.clearResource(project.id, selected);
      setConfirmClear(false);
      toast.notify(`All ${selected} records deleted`);
      refresh();
    } catch (e) {
      toast.fail(e);
    }
  };

  if (resources !== null && resources.length === 0) {
    return (
      <div className="panel empty">
        <EmptyArt kind="projects" />
        <h2>No data yet</h2>
        <p>
          A CRUD resource gives you a REST API that really stores data: POST a customer, then GET it back, change it or delete it.
          Records show up here, and you can edit them by hand.
        </p>
        {canEdit && (
          <button className="btn primary" onClick={() => setCreating(true)}><Database size={15} /> New CRUD resource</button>
        )}
        {creating && (
          <CrudDialog projectId={project.id} onClose={() => setCreating(false)}
            onCreated={async (name) => { setCreating(false); await reloadProject(); setParams({ resource: name }); loadResources(); }} />
        )}
      </div>
    );
  }

  return (
    <div className="data-layout">
      <div className="panel data-resources">
        <div className="panel-head">
          <h2><Database size={16} weight="duotone" /> Resources</h2>
          {canEdit && (
            <button className="btn ghost icon small" aria-label="New CRUD resource" title="New CRUD resource" onClick={() => setCreating(true)}>
              <Plus size={15} />
            </button>
          )}
        </div>
        <ul>
          {(resources ?? []).map((r) => (
            <li key={r.name}>
              <button className={`data-resource${r.name === selected ? ' active' : ''}`} onClick={() => setParams({ resource: r.name })}>
                <span className="mono">/{r.name}</span>
                <span className="count">{r.count}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div className="stack" style={{ minWidth: 0 }}>
        <div className="panel">
          <div className="panel-head">
            <div className="search-field">
              <MagnifyingGlass size={14} />
              <input type="search" aria-label="Search records" placeholder={`Search ${selected}`} value={query} onChange={(e) => setQuery(e.target.value)} />
            </div>
            <span className="btn-row">
              <button className="btn ghost icon small" aria-label="Refresh" title="Refresh" onClick={refresh}><ArrowClockwise size={14} /></button>
              {canEdit && (
                <>
                  <button className="btn small" disabled={!rows?.length} onClick={() => setConfirmClear(true)}>Clear</button>
                  <button className="btn small primary" onClick={() => setEditing({ row: null })}><Plus size={13} /> Add record</button>
                </>
              )}
            </span>
          </div>
          {rows === null ? (
            <div className="panel-body muted small">Loading…</div>
          ) : filtered.length === 0 ? (
            <div className="panel-body muted small">
              {rows.length === 0 ? `No ${selected} yet. POST one to ${project.mockUrl}/${selected}, or add it here.` : 'Nothing matches your search.'}
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table records">
                <thead>
                  <tr>
                    {columns.map((c) => <th key={c}>{c}</th>)}
                    {canEdit && <th><span className="sr-only">Actions</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((r) => (
                    <tr key={idOf(r)}>
                      {columns.map((c) => <td key={c} className={c === (current?.idField ?? 'id') ? 'mono' : ''} title={cell(r[c])}>{cell(r[c])}</td>)}
                      {canEdit && (
                        <td className="actions">
                          <button className="btn ghost icon small" aria-label={`Edit ${idOf(r)}`} onClick={() => setEditing({ row: r })}><PencilSimple size={14} /></button>
                          <button className="btn ghost icon small" aria-label={`Delete ${idOf(r)}`} onClick={() => remove(r)}><Trash size={14} /></button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {current && (
          <div className="panel">
            <div className="panel-head"><h2>Call it</h2></div>
            <div className="crud-preview flush">
              {crudEndpoints(current.name, current.idField).map((e) => (
                <div key={e.method + e.path} className="crud-endpoint">
                  <span className={`jack ${e.method}`}>{e.method}</span>
                  <code>{project.basePath}{e.path}</code>
                  <span className="muted small">{e.does}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {creating && (
        <CrudDialog projectId={project.id} onClose={() => setCreating(false)}
          onCreated={async (name) => { setCreating(false); await reloadProject(); setParams({ resource: name }); loadResources(); }} />
      )}
      {editing && (
        <RecordDialog
          resource={selected}
          row={editing.row}
          idField={current?.idField ?? 'id'}
          onClose={() => setEditing(null)}
          onSave={async (record) => {
            try {
              if (editing.row) await api.replaceRecord(project.id, selected, idOf(editing.row), record);
              else await api.addRecord(project.id, selected, record);
              toast.notify(editing.row ? 'Record saved' : 'Record added');
              setEditing(null);
              refresh();
            } catch (e) {
              toast.fail(e);
            }
          }}
        />
      )}
      {confirmClear && (
        <Modal
          title={`Delete all ${selected}?`}
          description={`Removes ${rows?.length ?? 0} records. The /${selected} endpoints keep working and start empty.`}
          onClose={() => setConfirmClear(false)}
          footer={
            <>
              <button className="btn" onClick={() => setConfirmClear(false)}>Keep them</button>
              <button className="btn danger" onClick={clear}>Delete all</button>
            </>
          }
        >
          <span />
        </Modal>
      )}
    </div>
  );
}

function RecordDialog({ resource, row, idField, onClose, onSave }: {
  resource: string;
  row: Row | null;
  idField: string;
  onClose: () => void;
  onSave: (record: unknown) => void;
}) {
  const [text, setText] = useState(JSON.stringify(row ?? {}, null, 2));
  const [error, setError] = useState('');

  const save = () => {
    try {
      const parsed = JSON.parse(text);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error();
      setError('');
      onSave(parsed);
    } catch {
      setError('Enter a JSON object, e.g. { "name": "Ada" }');
    }
  };

  return (
    <Modal
      title={row ? `Edit ${resource} ${cell(row[idField])}` : `Add to ${resource}`}
      description={row ? `The ${idField} stays the same.` : `Leave out ${idField} to have one generated.`}
      onClose={onClose}
      wide
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" onClick={save}>{row ? 'Save record' : 'Add record'}</button>
        </>
      }
    >
      <CodeEditor label="Record" value={text} onChange={setText} minHeight="200px" />
      {error && <div className="callout danger" role="alert">{error}</div>}
    </Modal>
  );
}
