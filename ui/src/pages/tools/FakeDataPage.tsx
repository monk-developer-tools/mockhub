import { Database, DownloadSimple, Table } from '@phosphor-icons/react';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { CopyButton } from '../../components/CopyButton';
import { GeneratorForm, useGenerator } from '../../components/GeneratorForm';
import { useToast } from '../../components/Toasts';
import { PageHeader } from '../../components/ui/PageHeader';
import { recordsToCsv } from '../../model/generator';
import { atLeast, useApp } from '../../state/AppContext';

type Format = 'json' | 'csv';

function save(name: string, text: string, type: string) {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

export function FakeDataPage() {
  const { canEdit, currentOrg, roleIn } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const g = useGenerator(10);
  const [format, setFormat] = useState<Format>('json');
  const [key, setKey] = useState('people');
  const [busy, setBusy] = useState(false);
  const records = g.records;
  const text = records ? (format === 'csv' ? recordsToCsv(records) : g.wrapped(records)) : '';
  const canSaveDataset = Boolean(currentOrg) && atLeast(roleIn(currentOrg?.id), 'editor');

  const saveDataset = async (e: FormEvent) => {
    e.preventDefault();
    if (!records || !currentOrg) return;
    setBusy(true);
    try {
      const name = key.trim();
      const existing = await api.datasets(currentOrg.id);
      if (!existing.datasets.some((d) => d.key === name)) await api.createDataset(currentOrg.id, { key: name, name });
      const r = await api.uploadDataset(currentOrg.id, name, 'json', 'replace', new Blob([JSON.stringify(records)]));
      toast.notify(`Saved ${r.added + r.updated} records in dataset “${name}”`);
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <PageHeader title="Generate test data" description="Realistic names, e-mails, addresses, products and more, in many languages. Download them, or keep them in MockHub." />
      <section className="panel" aria-label="Fields">
        <div className="panel-head">
          <h2>Fields</h2>
          <button className="btn primary small" onClick={g.generate} disabled={g.busy || g.fields.length === 0}>
            <Table size={14} /> {g.busy ? 'Generating…' : records ? 'Generate again' : 'Generate'}
          </button>
        </div>
        <div className="panel-body stack">
          <GeneratorForm g={g} allowWrap={format === 'json'} />
          {g.error && <div className="callout danger" role="alert">{g.error}</div>}
        </div>
      </section>
      {records && (
        <div className="tool-grid">
          <section className="panel" aria-label="Your data">
            <div className="panel-head">
              <h2>{records.length} records</h2>
              <span className="btn-row">
                <div className="segmented" role="group" aria-label="Format">
                  <button type="button" aria-pressed={format === 'json'} onClick={() => setFormat('json')}>JSON</button>
                  <button type="button" aria-pressed={format === 'csv'} onClick={() => setFormat('csv')}>CSV</button>
                </div>
                <CopyButton text={text} showLabel />
                <button className="btn small" onClick={() => save(`test-data.${format}`, text, format === 'csv' ? 'text/csv' : 'application/json')}>
                  <DownloadSimple size={14} /> Download
                </button>
              </span>
            </div>
            <div className="panel-body">
              <pre className="block tool-output" aria-label="Generated data">{text}</pre>
            </div>
          </section>
          <section className="panel" aria-label="Keep it in MockHub">
            <div className="panel-head"><h2>Keep it in MockHub</h2></div>
            <div className="panel-body stack">
              {canEdit && (
                <div className="stack">
                  <p className="small"><strong>A REST API with these records.</strong> List, get, create, update and delete, in a new project or one you already edit.</p>
                  <div><button className="btn small" onClick={() => navigate('/new/crud', { state: { text: JSON.stringify(records, null, 2) } })}>Save as a CRUD resource</button></div>
                </div>
              )}
              {canSaveDataset && (
                <form className="stack" onSubmit={saveDataset}>
                  <p className="small"><strong>A dataset for your stubs.</strong> Any project in {currentOrg?.name} can answer from it with dataset helpers.</p>
                  <div className="field-row">
                    <label className="field grow"><span>Dataset key</span><input type="text" className="mono" value={key} onChange={(e) => setKey(e.target.value)} /></label>
                  </div>
                  <div><button className="btn small" disabled={busy || !key.trim()}><Database size={14} /> {busy ? 'Saving…' : 'Save as a dataset'}</button></div>
                </form>
              )}
              {!canEdit && !canSaveDataset && <p className="muted small">With the editor role you can save these records as a REST API or a dataset.</p>}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
