import { CaretLeft, CaretRight, MagnifyingGlass } from '@phosphor-icons/react';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import type { Dataset } from '../../api/types';
import { useToast } from '../../components/Toasts';

const PAGE = 50;
const MAX_COLUMNS = 8;

function cell(value: unknown) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export function DatasetRecords({ orgId, dataset }: { orgId: string; dataset: Dataset }) {
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [offset, setOffset] = useState(0);
  const [page, setPage] = useState<{ total: number; records: Record<string, unknown>[] } | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(query.trim());
      setOffset(0);
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    let live = true;
    api.datasetRecords(orgId, dataset.key, { q: search, offset, limit: PAGE })
      .then((p) => live && setPage(p))
      .catch((e) => live && toast.fail(e));
    return () => {
      live = false;
    };
  }, [orgId, dataset.key, search, offset, toast.fail]);

  const columns = useMemo(() => {
    const names = dataset.columns.map((c) => c.name);
    const first = [...dataset.identity, ...names.filter((n) => !dataset.identity.includes(n))];
    return first.slice(0, MAX_COLUMNS);
  }, [dataset]);

  const from = page && page.total > 0 ? offset + 1 : 0;
  const to = page ? Math.min(offset + PAGE, page.total) : 0;

  return (
    <div className="panel">
      <div className="panel-head">
        <div className="search-field">
          <MagnifyingGlass size={14} />
          <input type="search" aria-label="Search records" placeholder={`Search ${dataset.key}`} value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        {page && page.total > 0 && (
          <span className="btn-row">
            <span className="muted small">{from.toLocaleString()}–{to.toLocaleString()} of {page.total.toLocaleString()}</span>
            <button className="btn ghost icon small" aria-label="Previous page" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE))}><CaretLeft size={14} /></button>
            <button className="btn ghost icon small" aria-label="Next page" disabled={to >= page.total} onClick={() => setOffset(offset + PAGE)}><CaretRight size={14} /></button>
          </span>
        )}
      </div>
      {page === null ? (
        <div className="panel-body muted small">Loading…</div>
      ) : page.records.length === 0 ? (
        <div className="panel-body muted small">{dataset.recordCount === 0 ? 'No records yet. Upload a CSV, JSON or NDJSON file.' : 'Nothing matches your search.'}</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table records">
            <thead>
              <tr>{columns.map((c) => <th key={c}>{c}</th>)}</tr>
            </thead>
            <tbody>
              {page.records.map((r, i) => (
                <tr key={offset + i}>
                  {columns.map((c) => <td key={c} className={dataset.identity.includes(c) ? 'mono' : ''} title={cell(r[c])}>{cell(r[c])}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
