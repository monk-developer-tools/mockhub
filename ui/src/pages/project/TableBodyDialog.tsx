import { Database, Plus, Trash } from '@phosphor-icons/react';
import { useState } from 'react';
import { api } from '../../api/client';
import { Modal } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { cellText, datasetBody, editCell, emptyRow, keyFrom, type Row, type TableBody, withRows } from '../../model/tableBody';

export function TableBodyDialog({ orgId, text, table, url, param, onClose, onUse, onBind }: {
  orgId: string;
  text: string;
  table: TableBody;
  url: string;
  param: string | null;
  onClose: () => void;
  onUse: (body: string) => void;
  onBind: (body: string) => void;
}) {
  const toast = useToast();
  const [rows, setRows] = useState<Row[]>(table.rows);
  const [ids, setIds] = useState<number[]>(() => table.rows.map((_, i) => i));
  const [columns, setColumns] = useState<string[]>(table.columns);
  const [newColumn, setNewColumn] = useState('');
  const [moving, setMoving] = useState(false);
  const [key, setKey] = useState(keyFrom(url));
  const [field, setField] = useState(table.columns.includes('id') ? 'id' : table.columns[0] ?? 'id');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const addColumn = () => {
    const name = newColumn.trim();
    if (!name) return;
    if (columns.includes(name)) return setError(`There is already a ${name} column`);
    setColumns([...columns, name]);
    setNewColumn('');
    setError('');
  };

  const move = async () => {
    if (!key.trim()) return setError('Give the dataset a key, e.g. pets');
    setBusy(true);
    setError('');
    try {
      await api.createDataset(orgId, { key: key.trim(), name: key.trim(), identity: rows.every((r) => r[field] !== null && r[field] !== undefined) ? [field] : [] });
      const file = new Blob([JSON.stringify(rows)], { type: 'application/json' });
      await api.uploadDataset(orgId, key.trim(), 'json', 'replace', file);
      toast.notify(`${rows.length} rows moved to dataset ${key.trim()}`);
      onBind(datasetBody(withRows(text, table.path, rows), table.path, key.trim(), param, field));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title="Edit the body as a table"
      description={table.path ? `Rows of "${table.path}". Values keep their column's type: numbers, true/false and JSON stay as they are.` : 'Values keep their column\'s type: numbers, true/false and JSON stay as they are.'}
      onClose={onClose}
      wide
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" onClick={() => onUse(withRows(text, table.path, rows))}>Use as response body</button>
        </>
      }
    >
      <div className="stack">
        <div className="table-editor">
          <table className="data-table">
            <thead>
              <tr>
                {columns.map((c) => <th key={c} className="mono">{c}</th>)}
                <th><span className="sr-only">Remove</span></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={ids[i]}>
                  {columns.map((c) => (
                    <td key={c}>
                      <input type="text" aria-label={`Row ${i + 1} ${c}`} defaultValue={cellText(row[c])}
                        onBlur={(e) => setRows((current) => editCell(current, i, c, e.target.value))} />
                    </td>
                  ))}
                  <td className="actions">
                    <button className="btn ghost icon small" aria-label={`Remove row ${i + 1}`} onClick={() => { setRows(rows.filter((_, j) => j !== i)); setIds(ids.filter((_, j) => j !== i)); }}><Trash size={13} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="btn-row">
          <button className="btn small" onClick={() => { setRows([...rows, emptyRow(columns)]); setIds([...ids, Math.max(-1, ...ids) + 1]); }}><Plus size={13} /> Row</button>
          <input type="text" className="mono" aria-label="New column" placeholder="new column" value={newColumn}
            onChange={(e) => setNewColumn(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && addColumn()} />
          <button className="btn small" onClick={addColumn}><Plus size={13} /> Column</button>
        </div>

        {!moving ? (
          <button className="btn small" onClick={() => setMoving(true)}><Database size={14} /> Move the rows to a dataset</button>
        ) : (
          <div className="callout stack">
            <p className="small">
              The rows go to a dataset that every project in the organisation can use, and the body reads them with a helper
              {param ? <> — one record by <span className="mono">{param}</span></> : <> — the whole list</>}. Edit them later on the Datasets page.
            </p>
            <div className="grid two">
              <label className="field">
                <span>Dataset key</span>
                <input type="text" className="mono" value={key} onChange={(e) => setKey(e.target.value)} />
              </label>
              <label className="field">
                <span>Identity field</span>
                <select value={field} onChange={(e) => setField(e.target.value)}>
                  {columns.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </label>
            </div>
            <div className="btn-row">
              <button className="btn small" onClick={() => setMoving(false)}>Not now</button>
              <button className="btn small primary" disabled={busy} onClick={move}>Create dataset and use it</button>
            </div>
          </div>
        )}
        {error && <div className="callout danger" role="alert">{error}</div>}
      </div>
    </Modal>
  );
}
