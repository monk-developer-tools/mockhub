import { ArrowClockwise, MagnifyingGlass, PencilSimple, Plus, Trash } from '@phosphor-icons/react';
import { useMemo, useState } from 'react';
import { api } from '../../api/client';
import type { StateContext } from '../../api/types';
import { CodeEditor } from '../../components/CodeEditor';
import { Modal } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { editableText, parseStateEdit, propertySummary, validContextName } from '../../model/state';
import { timeAgo } from '../../model/time';

export function StatePanel({ projectId, contexts, canEdit, onChanged }: {
  projectId: string;
  contexts: StateContext[] | null;
  canEdit: boolean;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState<{ context: StateContext | null } | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (contexts ?? []).filter((c) => !q || JSON.stringify(c).toLowerCase().includes(q));
  }, [contexts, query]);

  const remove = async (context: StateContext) => {
    try {
      await api.deleteStateContext(projectId, context.name);
      toast.notify(`State ${context.name} deleted`);
      onChanged();
    } catch (e) {
      toast.fail(e);
    }
  };

  const clear = async () => {
    try {
      await api.clearState(projectId);
      setConfirmClear(false);
      toast.notify('All state deleted');
      onChanged();
    } catch (e) {
      toast.fail(e);
    }
  };

  return (
    <div className="panel">
      <div className="panel-head">
        <div className="search-field">
          <MagnifyingGlass size={14} />
          <input type="search" aria-label="Search state" placeholder="Search state" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <span className="btn-row">
          <button className="btn ghost icon small" aria-label="Refresh" title="Refresh" onClick={onChanged}><ArrowClockwise size={14} /></button>
          {canEdit && (
            <>
              <button className="btn small" disabled={!contexts?.length} onClick={() => setConfirmClear(true)}>Clear</button>
              <button className="btn small primary" onClick={() => setEditing({ context: null })}><Plus size={13} /> Add context</button>
            </>
          )}
        </span>
      </div>
      {contexts === null ? (
        <div className="panel-body muted small">Loading…</div>
      ) : filtered.length === 0 ? (
        <div className="panel-body muted small">
          {contexts.length === 0
            ? 'No state yet. Stubs that record state (recordState) save it here, per context name, and it survives restarts.'
            : 'Nothing matches your search.'}
        </div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table records">
            <thead>
              <tr>
                <th>Context</th>
                <th>Properties</th>
                <th>List</th>
                <th>Updates</th>
                <th>Changed</th>
                {canEdit && <th><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => (
                <tr key={c.name}>
                  <td className="mono" title={c.name}>{c.name}</td>
                  <td title={propertySummary(c.properties)}>{propertySummary(c.properties)}</td>
                  <td>{c.list.length}</td>
                  <td>{c.updateCount}</td>
                  <td title={c.updatedAt}>{timeAgo(c.updatedAt)}</td>
                  {canEdit && (
                    <td className="actions">
                      <button className="btn ghost icon small" aria-label={`Edit ${c.name}`} onClick={() => setEditing({ context: c })}><PencilSimple size={14} /></button>
                      <button className="btn ghost icon small" aria-label={`Delete ${c.name}`} onClick={() => remove(c)}><Trash size={14} /></button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <StateDialog
          context={editing.context}
          onClose={() => setEditing(null)}
          onSave={async (name, body) => {
            try {
              await api.saveStateContext(projectId, name, body);
              toast.notify(editing.context ? 'State saved' : 'State added');
              setEditing(null);
              onChanged();
            } catch (e) {
              toast.fail(e);
            }
          }}
        />
      )}
      {confirmClear && (
        <Modal
          title="Delete all state?"
          description={`Removes ${contexts?.length ?? 0} contexts. Stubs that read state get their defaults until it is recorded again.`}
          onClose={() => setConfirmClear(false)}
          footer={
            <>
              <button className="btn" onClick={() => setConfirmClear(false)}>Keep it</button>
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

function StateDialog({ context, onClose, onSave }: {
  context: StateContext | null;
  onClose: () => void;
  onSave: (name: string, body: { properties: Record<string, string>; list: Record<string, string>[] }) => void;
}) {
  const [name, setName] = useState(context?.name ?? '');
  const [text, setText] = useState(editableText(context));
  const [error, setError] = useState('');

  const save = () => {
    const nameError = validContextName(name);
    if (nameError) return setError(nameError);
    const edit = parseStateEdit(text);
    if ('error' in edit) return setError(edit.error);
    setError('');
    onSave(name.trim(), edit);
  };

  return (
    <Modal
      title={context ? `Edit state ${context.name}` : 'Add state'}
      description="Properties are names with text values. The list holds objects, like items in a cart."
      onClose={onClose}
      wide
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" onClick={save}>{context ? 'Save state' : 'Add state'}</button>
        </>
      }
    >
      {!context && (
        <label className="field">
          <span>Context name</span>
          <input type="text" className="mono" value={name} placeholder="cart-42" onChange={(e) => setName(e.target.value)} autoFocus />
        </label>
      )}
      <CodeEditor label="State" value={text} onChange={setText} minHeight="200px" />
      {error && <div className="callout danger" role="alert">{error}</div>}
    </Modal>
  );
}
