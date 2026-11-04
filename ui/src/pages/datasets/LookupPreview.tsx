import { Copy, Plus, X } from '@phosphor-icons/react';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Dataset } from '../../api/types';
import { useToast } from '../../components/Toasts';
import { filtersFrom, helperExamples, lookupExpression } from '../../model/datasets';

const MAX_FILTERS = 3;

export function LookupPreview({ orgId, dataset }: { orgId: string; dataset: Dataset }) {
  const toast = useToast();
  const firstField = dataset.identity[0] ?? dataset.columns[0]?.name ?? '';
  const [rows, setRows] = useState([{ field: firstField, value: '' }]);
  const [one, setOne] = useState(true);
  const [result, setResult] = useState<string | null>(null);

  const filters = filtersFrom(rows);
  const expression = lookupExpression(dataset.key, filters, one);

  const run = async () => {
    try {
      const page = await api.datasetRecords(orgId, dataset.key, { filters, limit: one ? 1 : 100 });
      if (one) setResult(page.records.length ? JSON.stringify(page.records[0], null, 2) : 'Nothing found: dataset-one gives an empty value, so {{else}} runs.');
      else setResult(JSON.stringify(page.records, null, 2));
    } catch (e) {
      toast.fail(e);
    }
  };

  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.notify('Copied');
    } catch {
      toast.notify('Select the text and copy it');
    }
  };

  const update = (i: number, change: Partial<{ field: string; value: string }>) =>
    setRows(rows.map((r, j) => (j === i ? { ...r, ...change } : r)));

  return (
    <div className="panel">
      <div className="panel-head"><h2>Use it in a stub</h2></div>
      <div className="panel-body stack">
        <ul className="dataset-examples">
          {helperExamples(dataset).map((ex) => (
            <li key={ex.label}>
              <span className="muted small">{ex.label}</span>
              <code>{ex.expression}</code>
              <button className="btn ghost icon small" aria-label={`Copy ${ex.label}`} onClick={() => copy(ex.expression)}><Copy size={13} /></button>
            </li>
          ))}
        </ul>
        <h3 className="small">Try a lookup</h3>
        <div className="segmented" role="group" aria-label="Helper">
          <button type="button" aria-pressed={one} onClick={() => setOne(true)}>dataset-one</button>
          <button type="button" aria-pressed={!one} onClick={() => setOne(false)}>dataset</button>
        </div>
        {rows.map((row, i) => (
          <div key={i} className="lookup-row">
            <select aria-label={`Field ${i + 1}`} value={row.field} onChange={(e) => update(i, { field: e.target.value })}>
              <option value="">Field…</option>
              {dataset.columns.map((c) => <option key={c.name} value={c.name}>{c.name}</option>)}
            </select>
            <input type="text" aria-label={`Value ${i + 1}`} placeholder="value" value={row.value} onChange={(e) => update(i, { value: e.target.value })} />
            {rows.length > 1 && (
              <button className="btn ghost icon small" aria-label={`Remove filter ${i + 1}`} onClick={() => setRows(rows.filter((_, j) => j !== i))}><X size={13} /></button>
            )}
          </div>
        ))}
        <div className="btn-row">
          {rows.length < MAX_FILTERS && (
            <button className="btn small" onClick={() => setRows([...rows, { field: '', value: '' }])}><Plus size={13} /> Filter</button>
          )}
          <button className="btn small primary" onClick={run}>Preview</button>
        </div>
        <pre className="block">{expression}</pre>
        {result !== null && <pre className="block dataset-result" aria-label="Lookup result">{result}</pre>}
      </div>
    </div>
  );
}
