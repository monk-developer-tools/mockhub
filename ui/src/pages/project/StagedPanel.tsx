import { Check, Robot } from '@phosphor-icons/react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { StagedChange } from '../../api/types';
import { useToast } from '../../components/Toasts';
import { timeAgo } from '../../model/time';
import { useApp } from '../../state/AppContext';
import { ChangeCard } from './ChangeCard';
import { useProject } from './ProjectPage';

const POLL_MS = 5000;

export function useStagedChanges(projectId: string) {
  const [staged, setStaged] = useState<StagedChange[]>([]);
  const reload = useCallback(() => {
    const keep = (next: StagedChange[]) => setStaged((prev) => (prev.length === 0 && next.length === 0 ? prev : next));
    return api.stagedChanges(projectId).then(keep).catch(() => keep([]));
  }, [projectId]);
  useEffect(() => {
    reload();
    const timer = setInterval(reload, POLL_MS);
    return () => clearInterval(timer);
  }, [reload]);
  return { staged, reload };
}

export function StagedPanel({ staged, reload }: { staged: StagedChange[]; reload: () => Promise<unknown> }) {
  const { project, reloadProject } = useProject();
  const { canEdit, refreshProjects } = useApp();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  if (staged.length === 0) return null;

  const apply = async (ids: string[]) => {
    setBusy(true);
    try {
      const result = await api.applyStaged(project.id, ids);
      const failed = result.results.filter((r) => !r.ok);
      const note = `Applied ${result.applied} staged change${result.applied === 1 ? '' : 's'}.`;
      if (failed.length) toast.fail(new Error(`${note} ${failed.length} failed: ${failed.map((f) => f.error).join('; ')}`));
      else toast.notify(note);
      await Promise.all([reload(), reloadProject(), refreshProjects()]);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const discard = async (ids: string[]) => {
    setBusy(true);
    try {
      for (const id of ids) await api.discardStaged(project.id, id);
      await reload();
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const ids = staged.map((s) => s.change.id);
  return (
    <div className="panel changes staged">
      <div className="panel-head">
        <h2><Robot size={15} /> {staged.length} change{staged.length === 1 ? '' : 's'} from agents</h2>
        <span className="btn-row">
          <button className="btn small" disabled={!canEdit || busy} onClick={() => discard(ids)}>Discard all</button>
          <button className="btn primary small" disabled={!canEdit || busy} onClick={() => apply(ids)}>
            <Check size={13} /> Apply all
          </button>
        </span>
      </div>
      <p className="muted small changes-note">
        Staged over MCP by tokens that need review. {canEdit ? 'Nothing is live until you apply it.' : 'An editor has to apply them.'}
      </p>
      <ul className="change-list">
        {staged.map((s) => (
          <ChangeCard key={s.change.id} change={s.change} basePath={project.basePath} canApply={canEdit && !busy}
            meta={`${s.by} · ${timeAgo(s.at)}`}
            onApply={() => apply([s.change.id])} onDiscard={() => discard([s.change.id])} />
        ))}
      </ul>
    </div>
  );
}
