import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Dataset, DatasetLimits } from '../../api/types';
import { Modal } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { fieldList, slugifyKey } from '../../model/datasets';

export function DatasetDialog({ orgId, limits, dataset, onClose, onSaved }: {
  orgId: string;
  limits: DatasetLimits;
  dataset?: Dataset;
  onClose: () => void;
  onSaved: (dataset: Dataset) => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(dataset?.name ?? '');
  const [key, setKey] = useState(dataset?.key ?? '');
  const [keyTouched, setKeyTouched] = useState(Boolean(dataset));
  const [description, setDescription] = useState(dataset?.description ?? '');
  const [identity, setIdentity] = useState(dataset ? dataset.identity.join(', ') : 'id');
  const [indexes, setIndexes] = useState(dataset?.indexes.join(', ') ?? '');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const shownKey = keyTouched ? key : slugifyKey(name);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const ids = fieldList(identity);
    const idx = fieldList(indexes);
    if (!dataset && !shownKey) return setError('Give the dataset a key, e.g. customers');
    if (ids.length > limits.maxIdentity) return setError(`Use up to ${limits.maxIdentity} identity fields`);
    if (idx.length > limits.maxIndexes) return setError(`Use up to ${limits.maxIndexes} indexed fields`);
    setSaving(true);
    try {
      const body = { name: name.trim(), description: description.trim(), identity: ids, indexes: idx };
      const saved = dataset ? await api.updateDataset(orgId, dataset.key, body) : await api.createDataset(orgId, { ...body, key: shownKey });
      toast.notify(dataset ? 'Dataset saved' : `Dataset ${saved.key} created`);
      onSaved(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={dataset ? `Settings for ${dataset.key}` : 'New dataset'}
      description={dataset ? undefined : 'Next you upload a CSV, JSON or NDJSON file.'}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" type="submit" form="dataset-form" disabled={saving}>{dataset ? 'Save' : 'Create dataset'}</button>
        </>
      }
    >
      <form id="dataset-form" className="stack" onSubmit={submit}>
        <div className="grid two">
          <label className="field">
            <span>Name</span>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Customers" autoFocus />
          </label>
          <label className="field">
            <span>Key</span>
            <input type="text" className="mono" value={shownKey} disabled={Boolean(dataset)}
              onChange={(e) => { setKeyTouched(true); setKey(e.target.value); }} placeholder="customers" />
            <span className="hint">Stubs use it: {`{{dataset-one '${shownKey || 'customers'}' …}}`}</span>
          </label>
        </div>
        <label className="field">
          <span>Description</span>
          <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What the data is for" />
        </label>
        <div className="grid two">
          <label className="field">
            <span>Identity fields</span>
            <input type="text" className="mono" value={identity} onChange={(e) => setIdentity(e.target.value)} placeholder="id" />
            <span className="hint">Up to {limits.maxIdentity}, comma separated. Uploads update or skip records with the same values.</span>
          </label>
          <label className="field">
            <span>Indexed fields</span>
            <input type="text" className="mono" value={indexes} onChange={(e) => setIndexes(e.target.value)} placeholder="email, city" />
            <span className="hint">Up to {limits.maxIndexes}. Makes lookups on big datasets fast.</span>
          </label>
        </div>
        {error && <div className="callout danger" role="alert">{error}</div>}
      </form>
    </Modal>
  );
}
