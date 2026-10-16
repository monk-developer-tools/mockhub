import { ArrowCounterClockwise, CaretDown, CaretRight, DownloadSimple, UploadSimple } from '@phosphor-icons/react';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { PreviewProject, RestorePreview, RestoreResult } from '../api/types';
import { FilePicker } from '../components/FilePicker';
import { useToast } from '../components/Toasts';
import { TreeCheck } from '../components/TreeCheck';
import {
  everything,
  isProjectPicked,
  NOTHING,
  orgState,
  pickedProjectIds,
  toggleOrg,
  toggleProject,
  toSelection,
  type OrgNode,
  type Picked,
} from '../model/selection';
import { useApp } from '../state/AppContext';

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;

export function BackupPage() {
  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Backup &amp; restore</h1>
          <p className="muted">
            Save organisations or single projects, with their stubs, to a file. Restore all of it or just the parts you need, here or on another MockHub.
          </p>
        </div>
      </div>
      <div className="split backup">
        <BackupPanel />
        <RestorePanel />
      </div>
    </div>
  );
}

function BackupPanel() {
  const { orgs, projects } = useApp();
  const toast = useToast();
  const tree: OrgNode[] = useMemo(
    () => orgs.map((o) => ({ id: o.id, projectIds: projects.filter((p) => p.orgId === o.id).map((p) => p.id) })),
    [orgs, projects],
  );
  const [picked, setPicked] = useState<Picked>(NOTHING);
  const [open, setOpen] = useState<ReadonlySet<string>>(new Set());
  const [busy, setBusy] = useState(false);

  const chosen = new Set(pickedProjectIds(picked, tree));
  const stubs = projects.filter((p) => chosen.has(p.id)).reduce((n, p) => n + p.stubCount, 0);
  const orgCount = tree.filter((o) => orgState(picked, o) !== 'none').length;
  const nothing = picked.orgs.size === 0 && picked.projects.size === 0;

  const download = async () => {
    setBusy(true);
    try {
      await api.backup(toSelection(picked, tree));
      toast.notify(`Backup downloaded: ${plural(chosen.size, 'project')}, ${plural(stubs, 'stub')}`);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel" aria-labelledby="backup-title">
      <div className="panel-head">
        <h2 id="backup-title"><DownloadSimple size={15} /> Back up</h2>
        <span className="btn-row">
          <button className="btn ghost small" onClick={() => setPicked(everything(tree))}>Select all</button>
          <button className="btn ghost small" onClick={() => setPicked(NOTHING)} disabled={nothing}>Clear</button>
        </span>
      </div>
      <p className="panel-body muted small">
        Tick an organisation to include all of its projects, or open it and pick single projects. Users and sign-in settings are never included.
      </p>
      <ul className="tree" aria-label="Organisations and projects to back up">
        {orgs.map((o) => {
          const node = tree.find((t) => t.id === o.id)!;
          const orgProjects = projects.filter((p) => p.orgId === o.id);
          const expanded = open.has(o.id);
          return (
            <li key={o.id} className="tree-org">
              <div className="tree-row">
                <button
                  className="btn ghost icon small"
                  aria-expanded={expanded}
                  aria-label={`${expanded ? 'Hide' : 'Show'} projects in ${o.name}`}
                  onClick={() => setOpen(toggleSet(open, o.id))}
                  disabled={orgProjects.length === 0}
                >
                  {expanded ? <CaretDown size={14} /> : <CaretRight size={14} />}
                </button>
                <label className="tree-label">
                  <TreeCheck state={orgState(picked, node)} label={o.name} onChange={() => setPicked(toggleOrg(picked, node))} />
                  <strong>{o.name}</strong>
                </label>
                <span className="muted small">
                  {plural(orgProjects.length, 'project')}, {plural(orgProjects.reduce((n, p) => n + p.stubCount, 0), 'stub')}
                </span>
              </div>
              {expanded && (
                <ul className="tree-children">
                  {orgProjects.map((p) => (
                    <li key={p.id} className="tree-row">
                      <label className="tree-label">
                        <input
                          type="checkbox"
                          aria-label={p.name}
                          checked={isProjectPicked(picked, node, p.id)}
                          onChange={() => setPicked(toggleProject(picked, node, p.id))}
                        />
                        <span className="dot" style={{ background: p.color }} aria-hidden="true" />
                        {p.name}
                        <code className="muted">{p.basePath}</code>
                      </label>
                      <span className="muted small">{plural(p.stubCount, 'stub')}</span>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
      <div className="tree-foot">
        <span className="muted small" aria-live="polite">
          {nothing ? 'Nothing selected yet.' : `${plural(orgCount, 'organisation')}, ${plural(chosen.size, 'project')}, ${plural(stubs, 'stub')}`}
        </span>
        <button className="btn primary" onClick={download} disabled={nothing || busy}>
          <DownloadSimple size={14} /> {busy ? 'Preparing…' : 'Download backup'}
        </button>
      </div>
    </section>
  );
}

function RestorePanel() {
  const { orgs, refreshProjects, refreshSession } = useApp();
  const toast = useToast();
  const [file, setFile] = useState<{ text: string; name: string } | null>(null);
  const [preview, setPreview] = useState<RestorePreview | null>(null);
  const [picked, setPicked] = useState<Picked>(NOTHING);
  const [target, setTarget] = useState('');
  const [mode, setMode] = useState<'replace' | 'copy'>('replace');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<RestoreResult | null>(null);

  const tree: OrgNode[] = useMemo(
    () => (preview?.organizations ?? []).map((o) => ({ id: o.id, projectIds: o.projects.map((p) => p.id) })),
    [preview],
  );
  const chosen = new Set(pickedProjectIds(picked, tree));

  const choose = async (text: string, name: string) => {
    setResult(null);
    try {
      const p = await api.previewRestore(text);
      const nodes = p.organizations.map((o) => ({ id: o.id, projectIds: o.projects.map((x) => x.id) }));
      setFile({ text, name });
      setPreview(p);
      setPicked(everything(nodes));
      setTarget('');
      setMode('replace');
    } catch (e) {
      toast.fail(e);
    }
  };

  const reset = () => {
    setFile(null);
    setPreview(null);
    setPicked(NOTHING);
  };

  const restore = async () => {
    if (!file) return;
    setBusy(true);
    try {
      const r = await api.restore(file.text, { ...toSelection(picked, tree), targetOrgId: target || undefined, mode });
      setResult(r);
      reset();
      toast.notify(`Restored ${plural(r.projects, 'project')} and ${plural(r.stubs, 'stub')}`);
      await Promise.all([refreshProjects(), refreshSession()]);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel" aria-labelledby="restore-title">
      <div className="panel-head">
        <h2 id="restore-title"><UploadSimple size={15} /> Restore</h2>
        {preview && (
          <button className="btn ghost small" onClick={reset}>
            <ArrowCounterClockwise size={13} /> Choose another file
          </button>
        )}
      </div>

      {!preview && (
        <div className="panel-body stack">
          <p className="muted small">Open a backup file to see what's inside. You choose what to restore before anything changes.</p>
          <FilePicker accept=".json,application/json" label="Drop a backup file here or click to choose" onText={choose} />
          {result && <RestoreSummary result={result} />}
        </div>
      )}

      {preview && file && (
        <>
          <p className="panel-body muted small">
            <strong>{file.name}</strong>
            {preview.createdAt && <> was saved {new Date(preview.createdAt).toLocaleString()}</>}
            {preview.mockhubVersion && <> by MockHub {preview.mockhubVersion}</>}.
          </p>
          <ul className="tree" aria-label="Projects in the backup">
            {preview.organizations.map((o) => {
              const node = tree.find((t) => t.id === o.id)!;
              return (
                <li key={o.id} className="tree-org">
                  <div className="tree-row">
                    <label className="tree-label">
                      <TreeCheck state={orgState(picked, node)} label={o.name} onChange={() => setPicked(toggleOrg(picked, node))} />
                      <strong>{o.name}</strong>
                    </label>
                    {!target && <span className="tag">{o.exists ? 'Exists here' : 'New organisation'}</span>}
                  </div>
                  <ul className="tree-children">
                    {o.projects.map((p) => (
                      <li key={p.id} className="tree-row">
                        <label className="tree-label">
                          <input
                            type="checkbox"
                            aria-label={p.name}
                            checked={isProjectPicked(picked, node, p.id)}
                            onChange={() => setPicked(toggleProject(picked, node, p.id))}
                          />
                          {p.name}
                          <code className="muted">/{p.slug}</code>
                        </label>
                        <span className="muted small">{plural(p.stubCount, 'stub')}</span>
                        <ProjectFate project={p} mode={mode} />
                      </li>
                    ))}
                  </ul>
                </li>
              );
            })}
          </ul>

          <div className="panel-body stack restore-options">
            <label className="field">
              <span>Restore into</span>
              <select value={target} onChange={(e) => setTarget(e.target.value)}>
                <option value="">The organisations in the backup</option>
                {orgs.map((o) => (
                  <option key={o.id} value={o.id}>{o.name}</option>
                ))}
              </select>
            </label>
            <div className="field">
              <span id="mode-label">When a project already exists</span>
              <div className="segmented" role="group" aria-labelledby="mode-label">
                <button aria-pressed={mode === 'replace'} onClick={() => setMode('replace')}>Replace it</button>
                <button aria-pressed={mode === 'copy'} onClick={() => setMode('copy')}>Add a copy</button>
              </div>
              <span className="hint">
                {mode === 'replace'
                  ? 'The project and its stubs end up exactly as they are in the backup.'
                  : 'Every chosen project is added as a new project. Existing ones stay untouched.'}
              </span>
            </div>
          </div>

          <div className="tree-foot">
            <span className="muted small" aria-live="polite">
              {chosen.size === 0 ? 'Choose at least one project.' : `${plural(chosen.size, 'project')} selected`}
            </span>
            <button className="btn primary" onClick={restore} disabled={chosen.size === 0 || busy}>
              <UploadSimple size={14} /> {busy ? 'Restoring…' : `Restore ${plural(chosen.size, 'project')}`}
            </button>
          </div>
        </>
      )}
    </section>
  );
}

function ProjectFate({ project, mode }: { project: PreviewProject; mode: 'replace' | 'copy' }) {
  if (mode === 'copy' && project.status !== 'new') return <span className="tag">Copy with a new base path</span>;
  if (project.status === 'replace') return <span className="tag warn">Replaces existing</span>;
  if (project.status === 'slug-taken') return <span className="tag warn">Moves to /{project.newSlug}</span>;
  return <span className="tag ok">New</span>;
}

function RestoreSummary({ result }: { result: RestoreResult }) {
  return (
    <div className="callout" role="status">
      Restored {plural(result.projects, 'project')} and {plural(result.stubs, 'stub')}
      {result.organizations > 0 && <> into {plural(result.organizations, 'organisation')}</>}.
      {result.renamed.length > 0 && (
        <ul className="renamed">
          {result.renamed.map((r) => (
            <li key={r.project + r.to}>
              {r.project} now answers at <code>{r.to}</code> because <code>{r.from}</code> was taken.
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function toggleSet(set: ReadonlySet<string>, id: string) {
  const next = new Set(set);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}
