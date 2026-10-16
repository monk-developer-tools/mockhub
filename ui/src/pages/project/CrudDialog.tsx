import { Database } from '@phosphor-icons/react';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import { CodeEditor } from '../../components/CodeEditor';
import { Modal } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { crudEndpoints, slugifyResource } from '../../model/crud';

const EXAMPLE = `[
  { "name": "Ada Lovelace", "email": "ada@example.com" },
  { "name": "Alan Turing", "email": "alan@example.com" }
]`;

export function CrudDialog({ projectId, onClose, onCreated }: {
  projectId: string;
  onClose: () => void;
  onCreated: (resource: string) => void;
}) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [idField, setIdField] = useState('id');
  const [idType, setIdType] = useState<'uuid' | 'number'>('number');
  const [seed, setSeed] = useState('');
  const [busy, setBusy] = useState(false);
  const resource = slugifyResource(name);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    let records: unknown[] | undefined;
    if (seed.trim()) {
      try {
        const parsed = JSON.parse(seed);
        if (!Array.isArray(parsed)) throw new Error('not an array');
        records = parsed;
      } catch {
        toast.fail(new Error('Starting records must be a JSON array of objects'));
        return;
      }
    }
    setBusy(true);
    try {
      const result = await api.createCrud(projectId, { resource, idField: idField || 'id', idType, seed: records });
      toast.notify(`/${resource} is live${result.seeded ? ` with ${result.seeded} record${result.seeded === 1 ? '' : 's'}` : ''}`);
      onCreated(resource);
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title="New CRUD resource"
      description="A REST resource that really stores data: create records with POST and read them back with GET. Nothing to configure per endpoint."
      onClose={onClose}
      wide
    >
      <form className="stack" onSubmit={submit}>
        <div className="grid two">
          <label className="field">
            <span>Resource</span>
            <input type="text" className="mono" value={name} onChange={(e) => setName(e.target.value)} placeholder="customers" required autoFocus />
            <span className="hint">Lowercase, plural. Use a slash for nesting, e.g. v1/orders.</span>
          </label>
          <div className="grid two">
            <label className="field">
              <span>Id field</span>
              <input type="text" className="mono" value={idField} onChange={(e) => setIdField(e.target.value)} />
            </label>
            <div className="field">
              <span id="crud-id-type">New ids</span>
              <div className="segmented" role="group" aria-labelledby="crud-id-type">
                <button type="button" aria-pressed={idType === 'number'} onClick={() => setIdType('number')}>1, 2, 3</button>
                <button type="button" aria-pressed={idType === 'uuid'} onClick={() => setIdType('uuid')}>UUID</button>
              </div>
            </div>
          </div>
        </div>
        {resource && (
          <div className="crud-preview" aria-label="Endpoints">
            {crudEndpoints(resource, idField || 'id').map((e) => (
              <div key={e.method + e.path} className="crud-endpoint">
                <span className={`jack ${e.method}`}>{e.method}</span>
                <code>{e.path}</code>
                <span className="muted small">{e.does}</span>
              </div>
            ))}
          </div>
        )}
        <div className="field">
          <span>Starting records <span className="hint">optional JSON array</span></span>
          <CodeEditor label="Starting records" value={seed} onChange={setSeed} minHeight="110px" maxHeight="220px" />
          <button type="button" className="link-button small" onClick={() => setSeed(EXAMPLE)}>Use example records</button>
        </div>
        <div className="modal-foot" style={{ padding: 0 }}>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy || !resource}>
            <Database size={15} /> Create resource
          </button>
        </div>
      </form>
    </Modal>
  );
}
