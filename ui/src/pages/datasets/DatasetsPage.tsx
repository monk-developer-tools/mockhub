import { ArrowClockwise, GearSix, MagnifyingGlass, Plus, Table, Trash, UploadSimple } from '@phosphor-icons/react';
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { Dataset, DatasetLimits } from '../../api/types';
import { EmptyArt } from '../../components/Illustrations';
import { Confirm } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { PageHeader } from '../../components/ui/PageHeader';
import { sizeLabel } from '../../model/datasets';
import { timeAgo } from '../../model/time';
import { atLeast, useApp } from '../../state/AppContext';
import { DatasetDialog } from './DatasetDialog';
import { DatasetRecords } from './DatasetRecords';
import { LookupPreview } from './LookupPreview';
import { UploadDialog } from './UploadDialog';

export function DatasetsPage() {
  const { currentOrg, roleIn } = useApp();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [datasets, setDatasets] = useState<Dataset[] | null>(null);
  const [limits, setLimits] = useState<DatasetLimits | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [version, setVersion] = useState(0);

  const orgId = currentOrg?.id;
  const canChange = atLeast(roleIn(orgId), 'editor');
  const selected = datasets?.find((d) => d.key === params.get('dataset')) ?? datasets?.[0] ?? null;

  const load = useCallback(async () => {
    if (!orgId) return;
    try {
      const result = await api.datasets(orgId);
      setDatasets(result.datasets);
      setLimits(result.limits);
    } catch (e) {
      toast.fail(e);
    }
  }, [orgId, toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  const changed = async (key?: string) => {
    await load();
    setVersion((v) => v + 1);
    if (key) setParams({ dataset: key });
  };

  const remove = async () => {
    if (!orgId || !selected) return;
    try {
      await api.deleteDataset(orgId, selected.key);
      setDeleting(false);
      toast.notify(`Dataset ${selected.key} deleted`);
      setParams({});
      changed();
    } catch (e) {
      toast.fail(e);
    }
  };

  if (!orgId) return null;

  const header = (
    <PageHeader
      title="Datasets"
      icon={<Table size={20} weight="duotone" />}
      description={`Test data for every project in ${currentOrg?.name ?? 'this organisation'}. Upload CSV or JSON once, then answer from it in any stub with the dataset helpers.`}
      actions={canChange && datasets?.length ? <button className="btn primary" onClick={() => setCreating(true)}><Plus size={14} /> New dataset</button> : undefined}
    />
  );

  const dialogs = (
    <>
      {creating && limits && (
        <DatasetDialog orgId={orgId} limits={limits} onClose={() => setCreating(false)}
          onSaved={(d) => { setCreating(false); setUploading(true); changed(d.key); }} />
      )}
      {editing && selected && limits && (
        <DatasetDialog orgId={orgId} limits={limits} dataset={selected} onClose={() => setEditing(false)}
          onSaved={(d) => { setEditing(false); changed(d.key); }} />
      )}
      {uploading && selected && limits && (
        <UploadDialog orgId={orgId} dataset={selected} limits={limits} onClose={() => setUploading(false)}
          onUploaded={() => { setUploading(false); changed(selected.key); }} />
      )}
      {deleting && selected && (
        <Confirm title={`Delete ${selected.key}?`} confirmLabel="Delete dataset"
          message={`Removes ${selected.recordCount.toLocaleString()} records. Stubs that read ${selected.key} answer with an error until you upload it again.`}
          onConfirm={remove} onCancel={() => setDeleting(false)} />
      )}
    </>
  );

  if (datasets !== null && datasets.length === 0) {
    return (
      <div className="page">
        {header}
        <div className="panel empty">
          <EmptyArt kind="projects" />
          <h2>No datasets yet</h2>
          <p>
            A dataset is a table of test data, such as customers or products, that every project in this organisation can read.
            Stubs look records up by field: <code>{"{{{dataset-one 'customers' id=request.path.id}}}"}</code>.
          </p>
          {canChange ? (
            <button className="btn primary" onClick={() => setCreating(true)}><Plus size={14} /> New dataset</button>
          ) : (
            <p className="muted small">Editors of the organisation can add datasets.</p>
          )}
        </div>
        {dialogs}
      </div>
    );
  }

  return (
    <div className="page">
      {header}
      <div className="data-layout">
        <div className="panel data-resources">
          <div className="panel-head"><h2><Table size={16} weight="duotone" /> Datasets</h2></div>
          <ul>
            {(datasets ?? []).map((d) => (
              <li key={d.key}>
                <button className={`data-resource${d.key === selected?.key ? ' active' : ''}`} onClick={() => setParams({ dataset: d.key })}>
                  <span className="mono">{d.key}</span>
                  <span className="count">{d.recordCount.toLocaleString()}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>

        {selected && (
          <div className="stack" style={{ minWidth: 0 }}>
            <div className="panel">
              <div className="panel-head">
                <div>
                  <h2>{selected.name}</h2>
                  <p className="muted small">
                    {selected.recordCount.toLocaleString()} records · {sizeLabel(selected.bytes)} · changed {timeAgo(selected.updatedAt)}
                    {selected.identity.length > 0 && <> · identified by <span className="mono">{selected.identity.join(' + ')}</span></>}
                  </p>
                </div>
                <span className="btn-row">
                  <button className="btn ghost icon small" aria-label="Refresh" title="Refresh" onClick={() => changed()}><ArrowClockwise size={14} /></button>
                  {canChange && (
                    <>
                      <button className="btn ghost icon small" aria-label="Dataset settings" title="Settings" onClick={() => setEditing(true)}><GearSix size={14} /></button>
                      <button className="btn ghost icon small" aria-label={`Delete ${selected.key}`} title="Delete" onClick={() => setDeleting(true)}><Trash size={14} /></button>
                      <button className="btn small primary" onClick={() => setUploading(true)}><UploadSimple size={13} /> Upload</button>
                    </>
                  )}
                </span>
              </div>
              {selected.description && <div className="panel-body small">{selected.description}</div>}
              {selected.columns.length > 0 && (
                <div className="panel-body dataset-columns">
                  {selected.columns.map((c) => (
                    <span key={c.name} className="chip">
                      <span className="mono">{c.name}</span> <span className="muted">{c.type}</span>
                      {selected.indexes.includes(c.name) && <span className="muted"> · indexed</span>}
                    </span>
                  ))}
                </div>
              )}
            </div>
            <DatasetRecords key={`${selected.key}-${version}`} orgId={orgId} dataset={selected} />
            <LookupPreview key={`lookup-${selected.key}`} orgId={orgId} dataset={selected} />
          </div>
        )}
      </div>
      {dialogs}
      {!selected && datasets === null && (
        <div className="panel"><div className="panel-body muted small"><MagnifyingGlass size={14} /> Loading…</div></div>
      )}
    </div>
  );
}
