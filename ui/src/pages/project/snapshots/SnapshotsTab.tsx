import { ArrowCounterClockwise, ArrowsLeftRight, Camera, DownloadSimple, GitDiff, Trash, UploadSimple } from '@phosphor-icons/react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../../api/client';
import type { SnapshotSummary } from '../../../api/types';
import { Confirm } from '../../../components/Modal';
import { Skeleton } from '../../../components/Skeleton';
import { useToast } from '../../../components/Toasts';
import { contentsLabel } from '../../../model/snapshots';
import { timeAgo } from '../../../model/time';
import { useApp } from '../../../state/AppContext';
import { useProject } from '../ProjectPage';
import { CompareDialog, PullDialog, RestoreDialog, TakeSnapshotDialog, UploadSnapshotDialog } from './SnapshotDialogs';

type Dialog =
  | { kind: 'take' }
  | { kind: 'upload' }
  | { kind: 'pull' }
  | { kind: 'compare'; from: string; to: string; title: string }
  | { kind: 'restore'; snapshot: SnapshotSummary }
  | { kind: 'delete'; snapshot: SnapshotSummary };

export function SnapshotsTab() {
  const { project, reloadProject } = useProject();
  const { canEdit, projects, refreshProjects } = useApp();
  const toast = useToast();
  const [snapshots, setSnapshots] = useState<SnapshotSummary[] | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [dialog, setDialog] = useState<Dialog | null>(null);

  const load = useCallback(() => api.snapshots(project.id).then(setSnapshots).catch(toast.fail), [project.id]);
  useEffect(() => {
    load();
  }, [load]);

  const close = () => setDialog(null);
  const changed = async () => {
    setDialog(null);
    setSelected([]);
    await Promise.all([load(), reloadProject(), refreshProjects()]);
  };
  const remove = async (s: SnapshotSummary) => {
    try {
      await api.deleteSnapshot(project.id, s.id);
      toast.notify(`Deleted “${s.name}”`);
      setSelected((ids) => ids.filter((id) => id !== s.id));
      setDialog(null);
      load();
    } catch (e) {
      toast.fail(e);
    }
  };
  const toggle = (id: string) =>
    setSelected((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids.slice(-1), id]));
  const compareSelected = () => {
    const [a, b] = selected.map((id) => snapshots?.find((s) => s.id === id)).filter(Boolean) as SnapshotSummary[];
    const [older, newer] = a.createdAt <= b.createdAt ? [a, b] : [b, a];
    setDialog({ kind: 'compare', from: older.id, to: newer.id, title: `From “${older.name}” to “${newer.name}”` });
  };

  return (
    <div className="panel">
      <div className="panel-head">
        <div>
          <h2>Snapshots</h2>
          <p className="muted small">Save the stubs, response files and runtime behaviour, see what changed since, and roll back.</p>
        </div>
        <span className="btn-row">
          {selected.length === 2 && (
            <button className="btn small" onClick={compareSelected}><GitDiff size={14} /> Compare selected</button>
          )}
          {canEdit && (
            <>
              <button className="btn small" onClick={() => setDialog({ kind: 'upload' })}><UploadSimple size={14} /> Upload</button>
              <button className="btn small" onClick={() => setDialog({ kind: 'pull' })}><ArrowsLeftRight size={14} /> Pull from a project</button>
              <button className="btn primary small" onClick={() => setDialog({ kind: 'take' })}><Camera size={14} /> Take snapshot</button>
            </>
          )}
        </span>
      </div>
      <div className="panel-body">
        {!snapshots && <Skeleton rows={3} label="Loading snapshots" />}
        {snapshots && snapshots.length === 0 && (
          <div className="snap-empty">
            <Camera size={22} aria-hidden="true" />
            <div>
              <strong>No snapshots yet</strong>
              <p className="muted small">
                Take one before a release, a demo or a risky change. Restoring a snapshot or pulling from another project also saves the
                current state first, so you can always go back.
              </p>
            </div>
          </div>
        )}
        {snapshots && snapshots.length > 0 && (
          <ul className="snap-list" aria-label="Snapshots">
            {snapshots.map((s) => (
              <li key={s.id} className={s.safety ? 'safety' : undefined}>
                <input type="checkbox" aria-label={`Select ${s.name} to compare`} checked={selected.includes(s.id)} onChange={() => toggle(s.id)} />
                <div className="snap-main">
                  <div className="snap-title">
                    <strong>{s.name}</strong>
                    {s.safety && <span className="tag" title="Saved automatically before a restore or pull">safety</span>}
                  </div>
                  {s.purpose && <span className="small">{s.purpose}</span>}
                  <span className="muted small">
                    {s.createdBy || 'Unknown'} · <time dateTime={s.createdAt} title={new Date(s.createdAt).toLocaleString()}>{timeAgo(s.createdAt)}</time>
                    {' · '}{contentsLabel(s.stubCount, s.fileCount, s.crudResources)}
                    {s.sourceProjectName && s.sourceProjectName !== project.name && <> · from {s.sourceProjectName}</>}
                  </span>
                </div>
                <div className="btn-row snap-actions">
                  <button className="btn ghost small" onClick={() => setDialog({ kind: 'compare', from: s.id, to: 'current', title: `Changes since “${s.name}”` })}>
                    <GitDiff size={14} /> Compare with now
                  </button>
                  {canEdit && (
                    <button className="btn small" onClick={() => setDialog({ kind: 'restore', snapshot: s })} aria-label={`Restore ${s.name}`}>
                      <ArrowCounterClockwise size={14} /> Restore
                    </button>
                  )}
                  <button className="btn ghost icon small" title="Download" aria-label={`Download ${s.name}`} onClick={() => api.downloadSnapshot(project.id, s.id).catch(toast.fail)}>
                    <DownloadSimple size={15} />
                  </button>
                  {canEdit && (
                    <button className="btn ghost icon small" title="Delete" aria-label={`Delete ${s.name}`} onClick={() => setDialog({ kind: 'delete', snapshot: s })}>
                      <Trash size={15} />
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        {snapshots && snapshots.length > 0 && (
          <p className="muted small snap-note">
            Snapshots don't hold stored records, request history or the API key value. The last 10 safety snapshots are kept.
          </p>
        )}
      </div>
      {dialog?.kind === 'take' && <TakeSnapshotDialog project={project} onDone={changed} onClose={close} />}
      {dialog?.kind === 'upload' && <UploadSnapshotDialog project={project} onDone={changed} onClose={close} />}
      {dialog?.kind === 'pull' && (
        <PullDialog project={project} sources={projects.filter((p) => p.id !== project.id)} onDone={changed} onClose={close} />
      )}
      {dialog?.kind === 'compare' && <CompareDialog project={project} from={dialog.from} to={dialog.to} title={dialog.title} onClose={close} />}
      {dialog?.kind === 'restore' && <RestoreDialog project={project} snapshot={dialog.snapshot} onDone={changed} onClose={close} />}
      {dialog?.kind === 'delete' && (
        <Confirm
          title={`Delete “${dialog.snapshot.name}”?`}
          message="The snapshot is removed for everyone. The project itself doesn't change."
          confirmLabel="Delete"
          onConfirm={() => remove(dialog.snapshot)}
          onCancel={close}
        />
      )}
    </div>
  );
}
