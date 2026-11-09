import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../../../api/client';
import type { Project, SnapshotDiff, SnapshotRestoreResult, SnapshotSummary } from '../../../api/types';
import { FilePicker } from '../../../components/FilePicker';
import { Modal } from '../../../components/Modal';
import { Skeleton } from '../../../components/Skeleton';
import { useToast } from '../../../components/Toasts';
import { timeAgo } from '../../../model/time';
import { SnapshotDiffView } from './SnapshotDiffView';

export function TakeSnapshotDialog({ project, onDone, onClose }: { project: Project; onDone: () => void; onClose: () => void }) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [purpose, setPurpose] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const s = await api.createSnapshot(project.id, name.trim(), purpose.trim());
      toast.notify(`Saved “${s.name}”`);
      onDone();
    } catch (err) {
      toast.fail(err);
      setBusy(false);
    }
  };
  return (
    <Modal title="Take a snapshot" description="Saves the stubs, response files and runtime behaviour as they are now." onClose={onClose}>
      <form className="stack" onSubmit={submit} aria-label="Take a snapshot">
        <label className="field">
          <span>Name</span>
          <input type="text" value={name} maxLength={80} placeholder="e.g. release 1.4 or before retry rules" onChange={(e) => setName(e.target.value)} required autoFocus />
        </label>
        <label className="field">
          <span>Purpose <span className="hint">optional</span></span>
          <textarea rows={2} value={purpose} maxLength={500} placeholder="Why you're saving it" onChange={(e) => setPurpose(e.target.value)} />
        </label>
        <p className="muted small">Stored records, request history and the API key value aren't included.</p>
        <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy || !name.trim()}>{busy ? 'Saving…' : 'Take snapshot'}</button>
        </div>
      </form>
    </Modal>
  );
}

export function useDiff(load: () => Promise<SnapshotDiff> | null, deps: unknown[]) {
  const toast = useToast();
  const [diff, setDiff] = useState<SnapshotDiff | null>(null);
  useEffect(() => {
    let live = true;
    setDiff(null);
    load()?.then((d) => live && setDiff(d)).catch(toast.fail);
    return () => {
      live = false;
    };
  }, deps);
  return diff;
}

export function CompareDialog({ project, from, to, title, onClose }: {
  project: Project; from: string; to: string; title: string; onClose: () => void;
}) {
  const diff = useDiff(() => api.compareSnapshots(project.id, from, to), [project.id, from, to]);
  return (
    <Modal title={title} wide onClose={onClose} footer={<button className="btn" onClick={onClose}>Close</button>}>
      {diff ? <SnapshotDiffView diff={diff} basePath={project.basePath} /> : <Skeleton rows={4} label="Comparing" />}
    </Modal>
  );
}

export function restoredNote(r: SnapshotRestoreResult): string {
  return `${r.stubs} stub${r.stubs === 1 ? '' : 's'} and ${r.files} file${r.files === 1 ? '' : 's'} in place. The previous state is saved as “${r.safety.name}”.`;
}

export function RestoreDialog({ project, snapshot, onDone, onClose }: {
  project: Project; snapshot: SnapshotSummary; onDone: () => void; onClose: () => void;
}) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const diff = useDiff(() => api.compareSnapshots(project.id, 'current', snapshot.id), [project.id, snapshot.id]);
  const restore = async () => {
    setBusy(true);
    try {
      toast.notify(`Restored “${snapshot.name}”. ${restoredNote(await api.restoreSnapshot(project.id, snapshot.id))}`);
      onDone();
    } catch (err) {
      toast.fail(err);
      setBusy(false);
    }
  };
  return (
    <Modal
      title={`Restore “${snapshot.name}”?`}
      description="Stubs, response files and runtime behaviour go back to this snapshot. MockHub saves the current state as a safety snapshot first."
      wide
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={busy || !diff || diff.identical} onClick={restore}>{busy ? 'Restoring…' : 'Restore'}</button>
        </>
      }
    >
      <p className="muted small">What restoring changes. Stored records and the API key stay as they are.</p>
      {diff ? <SnapshotDiffView diff={diff} basePath={project.basePath} /> : <Skeleton rows={3} label="Comparing" />}
    </Modal>
  );
}

export function PullDialog({ project, sources, onDone, onClose }: {
  project: Project; sources: Project[]; onDone: () => void; onClose: () => void;
}) {
  const toast = useToast();
  const [from, setFrom] = useState(sources[0]?.id ?? '');
  const [snapshot, setSnapshot] = useState('current');
  const [snapshots, setSnapshots] = useState<SnapshotSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const source = sources.find((p) => p.id === from);

  useEffect(() => {
    setSnapshot('current');
    setSnapshots([]);
    if (from) api.snapshots(from).then((list) => setSnapshots(list.filter((s) => !s.safety))).catch(toast.fail);
  }, [from]);
  const diff = useDiff(() => (from ? api.previewPull(project.id, from, snapshot) : null), [project.id, from, snapshot]);

  const pull = async () => {
    setBusy(true);
    try {
      toast.notify(`Pulled from ${source?.name}. ${restoredNote(await api.pull(project.id, from, snapshot))}`);
      onDone();
    } catch (err) {
      toast.fail(err);
      setBusy(false);
    }
  };

  return (
    <Modal
      title="Pull from another project"
      description={`Replace the stubs, response files and runtime behaviour of ${project.name} with another project's, for example dev into qa. URLs move to ${project.basePath || 'the root'}.`}
      wide
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={busy || !diff || diff.identical} onClick={pull}>{busy ? 'Pulling…' : 'Pull into this project'}</button>
        </>
      }
    >
      {sources.length === 0 ? (
        <p className="muted">There are no other projects you can read.</p>
      ) : (
        <div className="stack snap-pull">
          <div className="grid two">
            <label className="field">
              <span>From project</span>
              <select value={from} onChange={(e) => setFrom(e.target.value)}>
                {sources.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.basePath || '/'})</option>)}
              </select>
            </label>
            <label className="field">
              <span>Version</span>
              <select value={snapshot} onChange={(e) => setSnapshot(e.target.value)}>
                <option value="current">As it is now</option>
                {snapshots.map((s) => <option key={s.id} value={s.id}>{s.name} · {timeAgo(s.createdAt)}</option>)}
              </select>
            </label>
          </div>
          <p className="muted small">What pulling changes in {project.name}. A safety snapshot is saved first; stored records and the API key stay as they are.</p>
          {diff ? <SnapshotDiffView diff={diff} basePath={project.basePath} /> : <Skeleton rows={3} label="Comparing" />}
        </div>
      )}
    </Modal>
  );
}

export function UploadSnapshotDialog({ project, onDone, onClose }: { project: Project; onDone: () => void; onClose: () => void }) {
  const toast = useToast();
  const upload = async (text: string) => {
    try {
      const s = await api.uploadSnapshot(project.id, text);
      toast.notify(`Uploaded “${s.name}”. Compare it with now or restore it.`);
      onDone();
    } catch (err) {
      toast.fail(err);
    }
  };
  return (
    <Modal title="Upload a snapshot" description="Add a snapshot file downloaded from this or another MockHub. Nothing changes until you restore it." onClose={onClose}
      footer={<button className="btn" onClick={onClose}>Cancel</button>}>
      <FilePicker accept=".json,application/json" label="Drop a snapshot file here or click to choose" onText={upload} />
    </Modal>
  );
}
