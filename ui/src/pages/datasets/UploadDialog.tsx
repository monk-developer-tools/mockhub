import { UploadSimple } from '@phosphor-icons/react';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Dataset, DatasetFormat, DatasetLimits, DatasetMode } from '../../api/types';
import { Modal } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { formatOf, sizeLabel } from '../../model/datasets';

const MODES: { value: DatasetMode; label: string; hint: string }[] = [
  { value: 'replace', label: 'Replace all', hint: 'The file becomes the whole dataset.' },
  { value: 'upsert', label: 'Update and add', hint: 'Records with the same identity are replaced; new ones are added.' },
  { value: 'add', label: 'Add only', hint: 'New records are added; records with the same identity are skipped.' },
];

export function UploadDialog({ orgId, dataset, limits, onClose, onUploaded }: {
  orgId: string;
  dataset: Dataset;
  limits: DatasetLimits;
  onClose: () => void;
  onUploaded: () => void;
}) {
  const toast = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [format, setFormat] = useState<DatasetFormat>('csv');
  const [mode, setMode] = useState<DatasetMode>(dataset.recordCount > 0 && dataset.identity.length > 0 ? 'upsert' : 'replace');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const pick = async (picked: File | null) => {
    setFile(picked);
    setError('');
    if (!picked) return;
    if (picked.size > limits.maxUploadBytes) {
      setError(`${picked.name} is ${sizeLabel(picked.size)}; uploads can be at most ${sizeLabel(limits.maxUploadBytes)}`);
      return;
    }
    const head = await picked.slice(0, 200).text();
    setFormat(formatOf(picked.name, head));
  };

  const upload = async () => {
    if (!file) return setError('Choose a file first');
    setBusy(true);
    try {
      const result = await api.uploadDataset(orgId, dataset.key, format, mode, file);
      const parts = [`${result.added.toLocaleString()} added`];
      if (result.updated) parts.push(`${result.updated.toLocaleString()} updated`);
      if (result.skipped) parts.push(`${result.skipped.toLocaleString()} skipped`);
      toast.notify(`${dataset.key}: ${parts.join(', ')}`);
      onUploaded();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const needsIdentity = mode === 'upsert' && dataset.identity.length === 0;

  return (
    <Modal
      title={`Upload to ${dataset.key}`}
      description={`CSV, JSON (an array of objects) or NDJSON, up to ${sizeLabel(limits.maxUploadBytes)} and ${limits.maxRecords.toLocaleString()} records.`}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" onClick={upload} disabled={busy || !file || needsIdentity}><UploadSimple size={14} /> Upload</button>
        </>
      }
    >
      <div className="stack">
        <label className="field">
          <span>File</span>
          <input type="file" accept=".csv,.json,.ndjson,.jsonl,text/csv,application/json" onChange={(e) => pick(e.target.files?.[0] ?? null)} />
          <span className="hint">In a CSV the first line names the columns. Add a type to force one, e.g. <code>zip:string</code> or <code>price:number</code>.</span>
        </label>
        <div className="field">
          <span id="upload-format">Format</span>
          <div className="segmented" role="group" aria-labelledby="upload-format">
            {(['csv', 'json', 'ndjson'] as DatasetFormat[]).map((f) => (
              <button key={f} type="button" aria-pressed={format === f} onClick={() => setFormat(f)}>{f.toUpperCase()}</button>
            ))}
          </div>
        </div>
        <div className="field" role="radiogroup" aria-labelledby="upload-mode">
          <span id="upload-mode">When records are already there</span>
          <div className="choice-cards three">
            {MODES.map((m) => (
              <label key={m.value} className={`choice-card${mode === m.value ? ' on' : ''}`}>
                <input type="radio" name="upload-mode" value={m.value} checked={mode === m.value} onChange={() => setMode(m.value)} />
                <strong>{m.label}</strong>
                <span>{m.hint}</span>
              </label>
            ))}
          </div>
        </div>
        {needsIdentity && <div className="callout" role="note">Set identity fields in the dataset's settings to update records.</div>}
        {error && <div className="callout danger" role="alert">{error}</div>}
      </div>
    </Modal>
  );
}
