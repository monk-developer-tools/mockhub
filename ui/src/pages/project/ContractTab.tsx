import { ArrowClockwise, ArrowSquareOut, CheckCircle, DownloadSimple, FileArrowUp, Sparkle, Trash, X } from '@phosphor-icons/react';
import { useCallback, useEffect, useState, type ChangeEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import { Confirm, Modal } from '../../components/Modal';
import { MethodJack } from '../../components/StubRoute';
import { Loading } from '../../components/Loading';
import { useToast } from '../../components/Toasts';
import { EmptyState } from '../../components/ui/EmptyState';
import { collapse } from '../../model/diff';
import {
  filterFindings,
  groupFindings,
  isSpecFile,
  requestsLink,
  sourceLabel,
  specDiff,
  type ContractView,
  type DriftFilter,
  type DriftPatch,
  type DriftView,
} from '../../model/contract';
import { docsAt } from '../../model/navigation';
import { timeAgo } from '../../model/time';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';

type AttachMode = 'file' | 'paste' | 'url';

function AttachForm({ projectId, replacing, onSaved, onCancel }: { projectId: string; replacing: boolean; onSaved: () => void; onCancel?: () => void }) {
  const toast = useToast();
  const [mode, setMode] = useState<AttachMode>('file');
  const [pasted, setPasted] = useState('');
  const [url, setUrl] = useState('');
  const [stripPrefix, setStripPrefix] = useState('');
  const [busy, setBusy] = useState(false);

  const save = async (content: string, source: 'upload' | 'url' | 'editor', fileName?: string) => {
    setBusy(true);
    try {
      await api.saveContract(projectId, { content, source, fileName, stripPrefix });
      await api.checkDrift(projectId);
      toast.notify('Contract saved and traffic checked');
      onSaved();
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const pick = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!isSpecFile(file.name)) {
      toast.fail(new Error('Choose a .json, .yaml or .yml file'));
      return;
    }
    await save(await file.text(), 'upload', file.name);
  };

  const fetchUrl = async () => {
    setBusy(true);
    try {
      const fetched = await api.fetchOpenApi(url.trim());
      setBusy(false);
      await save(fetched.content, 'url', url.trim());
    } catch (e) {
      toast.fail(e);
      setBusy(false);
    }
  };

  return (
    <div className="stack" aria-label="Attach a contract">
      <div className="segmented" role="group" aria-label="Contract source">
        <button type="button" aria-pressed={mode === 'file'} onClick={() => setMode('file')}>Upload a file</button>
        <button type="button" aria-pressed={mode === 'paste'} onClick={() => setMode('paste')}>Paste</button>
        <button type="button" aria-pressed={mode === 'url'} onClick={() => setMode('url')}>From a URL</button>
      </div>
      {mode === 'file' && (
        <label className="btn">
          <FileArrowUp size={15} /> Choose an OpenAPI file
          <input type="file" accept=".json,.yaml,.yml" hidden onChange={pick} disabled={busy} />
        </label>
      )}
      {mode === 'paste' && (
        <label className="field">
          <span>OpenAPI 3 or Swagger 2, JSON or YAML</span>
          <textarea className="mono" rows={10} value={pasted} onChange={(e) => setPasted(e.target.value)} />
        </label>
      )}
      {mode === 'url' && (
        <label className="field">
          <span>Address of the OpenAPI document</span>
          <input type="url" className="mono" placeholder="https://api.example.com/openapi.json" value={url} onChange={(e) => setUrl(e.target.value)} />
        </label>
      )}
      <label className="field">
        <span>Prefix the traffic leaves out <span className="hint">optional; if the contract's paths start with /api/v1 but calls arrive without it</span></span>
        <input type="text" className="mono" placeholder="/api/v1" value={stripPrefix} onChange={(e) => setStripPrefix(e.target.value)} />
      </label>
      <div className="btn-row">
        {mode === 'paste' && <button className="btn primary" disabled={busy || !pasted.trim()} onClick={() => save(pasted, 'editor')}>{replacing ? 'Replace contract' : 'Attach contract'}</button>}
        {mode === 'url' && <button className="btn primary" disabled={busy || !/^https?:\/\//.test(url.trim())} onClick={fetchUrl}>{busy ? 'Downloading…' : replacing ? 'Replace contract' : 'Attach contract'}</button>}
        {onCancel && <button className="btn" onClick={onCancel}>Cancel</button>}
      </div>
    </div>
  );
}

function PatchDialog({ patch, onApply, onClose, busy }: { patch: DriftPatch; onApply: () => void; onClose: () => void; busy: boolean }) {
  const lines = collapse(specDiff(patch.before, patch.after), 3);
  return (
    <Modal
      title="Suggested contract patch"
      description={patch.summary || `${patch.operations.length} change${patch.operations.length === 1 ? '' : 's'}`}
      onClose={onClose}
      wide
      footer={
        <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
          <button className="btn" onClick={onClose}>Discard</button>
          <button className="btn primary" onClick={onApply} disabled={busy}>{busy ? 'Applying…' : 'Apply to the contract'}</button>
        </div>
      }
    >
      <p className="muted small">Review it before applying: the assistant can be wrong. Applying replaces the stored contract and checks all recorded traffic again.</p>
      <pre className="diff" aria-label="Patch">
        {lines.map((l, i) =>
          l.kind === 'gap' ? (
            <span key={i} className="diff-gap">⋯ {l.count} unchanged line{l.count === 1 ? '' : 's'}</span>
          ) : (
            <span key={i} className={`diff-${l.kind}`}>{l.kind === 'add' ? '+ ' : l.kind === 'remove' ? '- ' : '  '}{l.text}</span>
          ),
        )}
      </pre>
    </Modal>
  );
}

function NotifyForm({ projectId, contract, onSaved }: { projectId: string; contract: ContractView; onSaved: (c: ContractView) => void }) {
  const toast = useToast();
  const [url, setUrl] = useState(contract.notifyUrl ?? '');
  const save = async () => {
    try {
      onSaved(await api.setContractNotify(projectId, url.trim()));
      toast.notify(url.trim() ? 'New drift will be posted to the webhook' : 'Drift notifications turned off');
    } catch (e) {
      toast.fail(e);
    }
  };
  return (
    <div className="field">
      <span>Webhook for new drift <span className="hint">MockHub POSTs JSON here when a check finds something new</span></span>
      <div className="btn-row">
        <input type="url" className="mono" style={{ flex: 1, minWidth: 0 }} placeholder="https://hooks.example.com/drift" value={url} onChange={(e) => setUrl(e.target.value)} />
        <button className="btn" onClick={save} disabled={url.trim() === (contract.notifyUrl ?? '')}>Save</button>
      </div>
    </div>
  );
}

export function ContractTab() {
  const { project } = useProject();
  const { canEdit, aiStatus } = useApp();
  const toast = useToast();
  const [contract, setContract] = useState<ContractView | null>(null);
  const [drift, setDrift] = useState<DriftView | null>(null);
  const [filter, setFilter] = useState<DriftFilter>('open');
  const [selected, setSelected] = useState<string[]>([]);
  const [replacing, setReplacing] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [checking, setChecking] = useState(false);
  const [patch, setPatch] = useState<DriftPatch | null>(null);
  const [patching, setPatching] = useState(false);

  const load = useCallback(async () => {
    try {
      const [c, d] = await Promise.all([api.contract(project.id), api.drift(project.id)]);
      setContract(c);
      setDrift(d);
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (call: () => Promise<DriftView>) => {
    setChecking(true);
    try {
      setDrift(await call());
    } catch (e) {
      toast.fail(e);
    } finally {
      setChecking(false);
    }
  };

  const mark = async (key: string, expected: boolean) => {
    try {
      setDrift(await api.markFinding(project.id, key, expected));
      setSelected((s) => s.filter((k) => k !== key));
    } catch (e) {
      toast.fail(e);
    }
  };

  const suggest = async () => {
    setPatching(true);
    try {
      setPatch(await api.driftPatch(project.id, selected));
    } catch (e) {
      toast.fail(e);
    } finally {
      setPatching(false);
    }
  };

  const apply = async () => {
    if (!patch) return;
    setPatching(true);
    try {
      setContract(await api.saveContract(project.id, { content: patch.after, source: 'ai-patch' }));
      setDrift(await api.recheckDrift(project.id));
      setPatch(null);
      setSelected([]);
      toast.notify('Contract patched and traffic checked again');
    } catch (e) {
      toast.fail(e);
    } finally {
      setPatching(false);
    }
  };

  const remove = async () => {
    try {
      await api.removeContract(project.id);
      setRemoving(false);
      await load();
    } catch (e) {
      toast.fail(e);
    }
  };

  if (!contract || !drift) return <Loading />;

  if (!contract.attached) {
    return (
      <div className="panel">
        <EmptyState title="No contract yet" docs={docsAt('contract-drift')}>
          <p>
            Attach the project's OpenAPI document and MockHub compares every recorded call with it: paths, methods, parameters,
            headers, bodies, status codes and content types. Drift shows up here, grouped, with counts and an example.
          </p>
        </EmptyState>
        {canEdit && <div className="panel-body"><AttachForm projectId={project.id} replacing={false} onSaved={load} /></div>}
      </div>
    );
  }

  const shown = filterFindings(drift.findings, filter);
  const groups = groupFindings(shown);
  const aiOn = Boolean(aiStatus?.enabled) && canEdit;
  const toggle = (key: string) => setSelected((s) => (s.includes(key) ? s.filter((k) => k !== key) : [...s, key]));

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <div className="stack" style={{ gap: 2 }}>
            <strong>{contract.title || 'Untitled contract'} {contract.version && <span className="muted">v{contract.version}</span>}</strong>
            <span className="muted small">
              {contract.dialect} · {contract.operations?.length ?? 0} operations · {sourceLabel(contract.source)}
              {contract.fileName && <> (<span className="mono">{contract.fileName}</span>)</>} {timeAgo(contract.updatedAt)}
              {contract.updatedBy && <> by {contract.updatedBy}</>}
              {contract.stripPrefix && <> · traffic leaves out <span className="mono">{contract.stripPrefix}</span></>}
            </span>
          </div>
          <div className="btn-row">
            <button className="btn ghost" onClick={() => api.downloadContract(project.id, 'yaml').catch(toast.fail)}>
              <DownloadSimple size={15} /> Download
            </button>
            {canEdit && <button className="btn" onClick={() => setReplacing((r) => !r)}>Replace</button>}
            {canEdit && (
              <button className="btn ghost icon" aria-label="Remove contract" title="Remove contract" onClick={() => setRemoving(true)}>
                <Trash size={15} />
              </button>
            )}
          </div>
        </div>
        {(replacing || canEdit || drift.lastNotice?.error) && (
          <div className="panel-body stack">
            {replacing && <AttachForm projectId={project.id} replacing onSaved={() => { setReplacing(false); load(); }} onCancel={() => setReplacing(false)} />}
            {canEdit && <NotifyForm key={contract.notifyUrl ?? ''} projectId={project.id} contract={contract} onSaved={setContract} />}
            {drift.lastNotice?.error && (
              <div className="callout danger small" role="alert">
                <div>The last drift webhook failed: {drift.lastNotice.error}</div>
              </div>
            )}
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <div className="btn-row">
            <div className="segmented" role="group" aria-label="Filter">
              <button aria-pressed={filter === 'open'} onClick={() => setFilter('open')}>Open {drift.open > 0 && <span className="count-alert">{drift.open}</span>}</button>
              <button aria-pressed={filter === 'expected'} onClick={() => setFilter('expected')}>Expected {drift.expected > 0 && <span className="muted">{drift.expected}</span>}</button>
              <button aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>All</button>
            </div>
            <span className="muted small">
              {drift.lastCheckedAt ? `Checked ${timeAgo(drift.lastCheckedAt)}, ${drift.scanned} calls so far` : 'Not checked yet'}
              {' · checks run every 5 minutes'}
            </span>
          </div>
          <div className="btn-row">
            {aiOn && drift.open > 0 && (
              <button className="btn" onClick={suggest} disabled={patching}>
                <Sparkle size={15} /> {patching ? 'Asking…' : selected.length ? `Suggest a patch for ${selected.length}` : 'Suggest a patch'}
              </button>
            )}
            <button className="btn" onClick={() => run(() => api.checkDrift(project.id))} disabled={checking}>
              <ArrowClockwise size={15} /> {checking ? 'Checking…' : 'Check now'}
            </button>
            {canEdit && (
              <button className="btn ghost" onClick={() => run(() => api.recheckDrift(project.id))} disabled={checking} title="Forget findings and check all recorded traffic again">
                Recheck all
              </button>
            )}
          </div>
        </div>
        <div className="panel-body stack">
          {drift.capped && <p className="muted small">Showing the first 500 kinds of drift; new kinds are ignored until you fix or recheck.</p>}
          {groups.length === 0 ? (
            <div className="empty slim">
              <CheckCircle size={22} aria-hidden="true" />
              <span>{filter === 'expected' ? 'Nothing marked as expected.' : drift.scanned === 0 ? 'No recorded traffic yet. Call the mock, then check again.' : 'Traffic matches the contract.'}</span>
            </div>
          ) : (
            groups.map((g) => (
              <div key={g.kind} className="stack" style={{ gap: 6 }}>
                <h3>
                  {g.label} <span className="muted small">{g.findings.length} · {g.total} calls · {g.side}</span>
                </h3>
                <ul className="drift-list" aria-label={g.label}>
                  {g.findings.map((f) => (
                    <li key={f.key} className={f.expected ? 'expected' : undefined}>
                      {aiOn && !f.expected && (
                        <input type="checkbox" aria-label={`Choose ${f.method} ${f.path}`} checked={selected.includes(f.key)} onChange={() => toggle(f.key)} />
                      )}
                      <span className="route">
                        <MethodJack method={f.method} />
                        <span className="path">{f.path}</span>
                      </span>
                      <span className="drift-detail mono small">{f.detail}</span>
                      <span className="muted small">{f.count}× · first {timeAgo(f.firstSeen)} · last {timeAgo(f.lastSeen)}</span>
                      <span className="btn-row">
                        <Link className="btn ghost icon" to={requestsLink(project.id, project.basePath, f)} title="See the calls" aria-label={`See calls for ${f.method} ${f.path}`}>
                          <ArrowSquareOut size={15} />
                        </Link>
                        {canEdit && (
                          f.expected ? (
                            <button className="btn ghost" onClick={() => mark(f.key, false)}>Reopen</button>
                          ) : (
                            <button className="btn ghost" onClick={() => mark(f.key, true)} title="Hide it from open drift and don't notify about it">
                              <X size={14} /> Mark expected
                            </button>
                          )
                        )}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ))
          )}
        </div>
      </section>

      {patch && <PatchDialog patch={patch} busy={patching} onApply={apply} onClose={() => setPatch(null)} />}
      {removing && (
        <Confirm
          title="Remove the contract?"
          message="Its findings and expected marks are deleted too. Recorded traffic stays."
          confirmLabel="Remove"
          onConfirm={remove}
          onCancel={() => setRemoving(false)}
        />
      )}
    </div>
  );
}
