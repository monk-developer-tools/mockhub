import { Copy, Database, FloppyDisk, FolderSimplePlus, LockSimple, Plus, Sparkle, Table, Trash, Warning } from '@phosphor-icons/react';
import { Link } from 'react-router-dom';
import { crudOf } from '../../model/crud';
import { FakeDataMenu } from '../../components/FakeDataMenu';
import { DataGeneratorDialog } from '../../components/DataGeneratorDialog';
import { formatSize } from '../../model/files';
import { UploadButton, useProjectFiles } from './ResponseFiles';
import { useEffect, useMemo, useState } from 'react';
import { useHotkeys } from '../../state/hotkeys';
import { api } from '../../api/client';
import type { Project, Stub, StubUsage } from '../../api/types';
import { timeAgo } from '../../model/time';
import { AiBodyDialog, type BodyFormat } from './AiBodyDialog';
import { CodeEditor } from '../../components/CodeEditor';
import { Confirm, Modal } from '../../components/Modal';
import { StubRoute } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import {
  applySoapDefaults,
  emptyWebhook,
  FAULTS,
  fromStub,
  isDisabled,
  METHODS,
  soapEnvelope,
  soapFault,
  toStub,
  validate,
  WEBHOOK_METHODS,
  type BodyType,
  type DelayType,
  type ResponseMode,
  type StubForm,
  type Webhook,
  type UrlMatchType,
} from '../../model/stubForm';
import { BodyMatcherRows, KeyValueRows, ValueMatcherRows } from './MatcherRows';
import { TryIt } from './TryIt';
import { CodeSnippets } from '../../components/CodeSnippets';
import { sampleRequest } from '../../model/sampleRequest';

const URL_MATCH_LABELS: Record<UrlMatchType, string> = {
  urlPath: 'Path equals',
  url: 'Path and query equal',
  urlPathTemplate: 'Path template /{id}',
  urlPathPattern: 'Path matches regex',
  urlPattern: 'Path and query match regex',
  any: 'Any URL',
};

const BODY_TYPES: { value: BodyType; label: string }[] = [
  { value: 'json', label: 'JSON' },
  { value: 'xml', label: 'XML' },
  { value: 'text', label: 'Text' },
  { value: 'file', label: 'File' },
  { value: 'base64', label: 'Binary' },
  { value: 'none', label: 'None' },
];

const DELAYS: { value: DelayType; label: string }[] = [
  { value: 'none', label: 'None' },
  { value: 'fixed', label: 'Fixed' },
  { value: 'uniform', label: 'Random range' },
  { value: 'lognormal', label: 'Realistic' },
  { value: 'chunked', label: 'Trickle' },
];

interface Props {
  project: Project;
  projects: Project[];
  stub: Stub;
  isNew: boolean;
  canEdit: boolean;
  onSaved: (stub: Stub) => void;
  onDeleted: () => void;
  onDirtyChange: (dirty: boolean) => void;
  usage?: StubUsage;
  onToggled?: (stub: Stub) => void;
  aiEnabled?: boolean;
}

type Mode = 'form' | 'json';

function withFile(form: StubForm, path: string, contentType: string): Partial<StubForm> {
  const headers = form.responseHeaders.filter((h) => h.key.toLowerCase() !== 'content-type');
  return { responseBody: path, responseHeaders: [{ key: 'Content-Type', value: contentType }, ...headers] };
}

export function StubEditor({ project, projects, stub, isNew, canEdit, onSaved, onDeleted, onDirtyChange, usage, onToggled, aiEnabled = false }: Props) {
  const crud = crudOf(stub);
  const [generating, setGenerating] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [generatingData, setGeneratingData] = useState(false);
  const disabled = isDisabled(stub);
  const { files, reload: reloadFiles } = useProjectFiles(project.id);
  const toast = useToast();
  const initial = useMemo(() => fromStub(stub), [stub]);
  const [form, setForm] = useState<StubForm>(initial.form);
  const [unsupported, setUnsupported] = useState<string[]>(initial.unsupported);
  const [mode, setMode] = useState<Mode>(initial.unsupported.length ? 'json' : 'form');
  const [jsonText, setJsonText] = useState(() => JSON.stringify(stub, null, 2));
  const [jsonError, setJsonError] = useState('');
  const [dirty, setDirty] = useState(isNew);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [moving, setMoving] = useState(false);

  useEffect(() => {
    const next = fromStub(stub);
    setForm(next.form);
    setUnsupported(next.unsupported);
    setMode(next.unsupported.length ? 'json' : 'form');
    setJsonText(JSON.stringify(stub, null, 2));
    setJsonError('');
    setDirty(isNew);
  }, [stub, isNew]);

  useEffect(() => {
    onDirtyChange(dirty);
  }, [dirty, onDirtyChange]);

  const update = (patch: Partial<StubForm>) => {
    if (!canEdit) return;
    setForm((f) => ({ ...f, ...patch }));
    setDirty(true);
  };

  const currentStub = (): Stub => {
    if (mode === 'json') return JSON.parse(jsonText) as Stub;
    return toStub(form.kind === 'soap' ? applySoapDefaults(form) : form, stub);
  };

  const switchMode = (next: Mode) => {
    if (next === mode) return;
    if (next === 'json') {
      setJsonText(JSON.stringify(toStub(form.kind === 'soap' ? applySoapDefaults(form) : form, stub), null, 2));
      setMode('json');
      return;
    }
    try {
      const parsed = fromStub(JSON.parse(jsonText) as Stub);
      setForm(parsed.form);
      setUnsupported(parsed.unsupported);
      setJsonError('');
      setMode('form');
    } catch {
      setJsonError('Fix the JSON before switching to the form.');
    }
  };

  const errors = mode === 'form' ? validate(form) : jsonError ? [jsonError] : [];
  const preview = useMemo(() => {
    try {
      return currentStub();
    } catch {
      return stub;
    }
  }, [form, jsonText, mode]);

  const save = async () => {
    let body: Stub;
    try {
      body = currentStub();
    } catch {
      setJsonError('The JSON is not valid.');
      return;
    }
    setBusy(true);
    try {
      const saved = isNew ? await api.createStub(project.id, body) : await api.updateStub(project.id, String(stub.id), body);
      toast.notify(isNew ? 'Stub created' : 'Stub saved');
      setDirty(false);
      onSaved(saved);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setConfirmDelete(false);
    try {
      await api.deleteStub(project.id, String(stub.id));
      toast.notify('Stub deleted');
      onDeleted();
    } catch (e) {
      toast.fail(e);
    }
  };

  const duplicate = async () => {
    try {
      const copy = await api.cloneStub(project.id, String(stub.id));
      toast.notify('Stub duplicated');
      onSaved(copy);
    } catch (e) {
      toast.fail(e);
    }
  };

  const move = async (target: string) => {
    setMoving(false);
    try {
      await api.moveStub(project.id, String(stub.id), target);
      toast.notify('Stub moved');
      onDeleted();
    } catch (e) {
      toast.fail(e);
    }
  };

  const toggle = async () => {
    if (dirty) {
      toast.notify('Save or discard your changes first');
      return;
    }
    setToggling(true);
    try {
      const saved = await api.setStubEnabled(project.id, String(stub.id), disabled);
      toast.notify(disabled ? 'Stub turned on' : 'Stub turned off');
      onToggled?.(saved);
    } catch (e) {
      toast.fail(e);
    } finally {
      setToggling(false);
    }
  };

  const canSave = canEdit && !busy && errors.length === 0 && (dirty || isNew);
  useHotkeys(useMemo(() => [{ key: 's', mod: true, allowWhileTyping: true, handler: () => canSave && save() }], [canSave, form, jsonText, mode]));

  const readOnly = !canEdit;
  const soap = form.kind === 'soap';
  const bodyLanguage = form.bodyType === 'json' ? 'json' : form.bodyType === 'xml' ? 'xml' : 'text';

  return (
    <div className="panel editor">
      <div className="editor-head">
        <StubRoute stub={preview} basePath={project.basePath} />
        <div className="segmented" role="group" aria-label="Editor mode">
          <button aria-pressed={mode === 'form'} onClick={() => switchMode('form')}>Form</button>
          <button aria-pressed={mode === 'json'} onClick={() => switchMode('json')}>JSON</button>
        </div>
        {canEdit && (
          <div className="btn-row">
            {!isNew && (
              <>
                <button className="btn ghost icon" title="Duplicate" aria-label="Duplicate" onClick={duplicate}>
                  <Copy size={15} />
                </button>
                {projects.length > 1 && (
                  <button className="btn ghost icon" title="Move to another project" aria-label="Move to another project" onClick={() => setMoving(true)}>
                    <FolderSimplePlus size={15} />
                  </button>
                )}
                <button className="btn ghost icon danger" title="Delete" aria-label="Delete" onClick={() => setConfirmDelete(true)}>
                  <Trash size={15} />
                </button>
              </>
            )}
            <button className="btn primary" onClick={save} disabled={!canSave} title="Save (Ctrl/⌘ S)">
              <FloppyDisk size={15} /> {isNew ? 'Create stub' : 'Save'}
            </button>
          </div>
        )}
      </div>

      {!isNew && (
        <div className="stub-status">
          <label className={`switch${disabled ? ' off' : ''}`}>
            <input type="checkbox" role="switch" checked={!disabled} disabled={!canEdit || toggling} onChange={toggle} aria-label="Stub is on" />
            <span>{disabled ? 'Off' : 'On'}</span>
          </label>
          <span className="muted small">
            {disabled
              ? 'Requests skip this stub, as if it were deleted. Turn it on to answer again.'
              : usage
                ? `${usage.count} ${usage.count === 1 ? 'call' : 'calls'} in the request log, last ${timeAgo(usage.lastCalledAt)}`
                : 'Not called yet'}
          </span>
        </div>
      )}

      {errors.length > 0 && (
        <div className="section">
          <div className="callout danger" role="alert">
            <Warning size={16} />
            <div>
              {errors.length === 1 ? errors[0] : <ul>{errors.map((e) => <li key={e}>{e}</li>)}</ul>}
            </div>
          </div>
        </div>
      )}

      {crud && (
        <div className="section">
          <div className="callout crud-callout">
            <Database size={16} weight="duotone" />
            <div>
              This stub serves the <strong>/{crud.resource}</strong> CRUD resource: list, read, create, replace, patch and delete.
              Records live in the <Link to={`/projects/${project.id}/data?resource=${encodeURIComponent(crud.resource)}`}>Data tab</Link>.
            </div>
          </div>
        </div>
      )}

      {mode === 'json' ? (
        <div className="section">
          {unsupported.length > 0 && !crud && (
            <div className="callout warn" style={{ marginBottom: 12 }}>
              <Warning size={16} />
              <div>
                This stub uses features the form can't show, so it opens as JSON. Everything WireMock supports works here.
                <ul>{unsupported.map((u) => <li key={u}>{u}</li>)}</ul>
              </div>
            </div>
          )}
          <p className="muted small" style={{ marginBottom: 8 }}>
            {project.basePath ? <>URLs are relative to <span className="mono">{project.basePath}</span>.</> : 'URLs start at the server root.'} See the{' '}
            <a href="https://wiremock.org/docs/stubbing/" target="_blank" rel="noreferrer">WireMock stubbing reference</a>.
          </p>
          <CodeEditor
            label="Stub JSON"
            value={jsonText}
            readOnly={readOnly}
            minHeight="420px"
            maxHeight="none"
            onChange={(v) => {
              setJsonText(v);
              setDirty(true);
              try {
                JSON.parse(v);
                setJsonError('');
              } catch (e) {
                setJsonError(`JSON: ${(e as Error).message}`);
              }
            }}
          />
        </div>
      ) : (
        <fieldset disabled={readOnly} className="editor-form" style={{ border: 0, margin: 0, padding: 0, minWidth: 0 }}>
          {readOnly && (
            <p className="readonly-note" role="note">
              <LockSimple size={14} /> Read-only. Your account can view this stub but not change it. Sign in as an editor to edit it.
            </p>
          )}
          <div className="section">
            <div className="grid two">
              <label className="field">
                <span>Name</span>
                <input type="text" value={form.name} placeholder="Get customer" onChange={(e) => update({ name: e.target.value })} />
              </label>
              <div className="field">
                <span>Service type</span>
                <div className="segmented" role="group" aria-label="Service type">
                  <button type="button" aria-pressed={!soap} onClick={() => update({ kind: 'rest' })}>REST / HTTP</button>
                  <button
                    type="button"
                    aria-pressed={soap}
                    onClick={() => update(applySoapDefaults({ ...form, kind: 'soap', soapOperation: form.soapOperation || 'Operation' }))}
                  >
                    SOAP
                  </button>
                </div>
              </div>
              <label className="field">
                <span>Description <span className="hint">shown in Postman and Bruno docs</span></span>
                <input type="text" value={form.description} onChange={(e) => update({ description: e.target.value })} />
              </label>
              <div className="grid two">
                <label className="field">
                  <span>Folder</span>
                  <input type="text" value={form.folder} placeholder="auto" onChange={(e) => update({ folder: e.target.value })} />
                </label>
                <label className="field">
                  <span>Priority <span className="hint">1 wins</span></span>
                  <input type="number" min={1} value={form.priority} placeholder="5" onChange={(e) => update({ priority: e.target.value })} />
                </label>
              </div>
            </div>
          </div>

          <div className="section">
            <h3>Request</h3>
            <div className="rows">
              <div className="row" style={{ gridTemplateColumns: '120px 210px 1fr' }}>
                <select aria-label="Method" value={form.method} onChange={(e) => update({ method: e.target.value })}>
                  {METHODS.map((m) => (
                    <option key={m}>{m}</option>
                  ))}
                </select>
                <select aria-label="URL match" value={form.urlMatch} onChange={(e) => update({ urlMatch: e.target.value as UrlMatchType })}>
                  {(Object.keys(URL_MATCH_LABELS) as UrlMatchType[]).map((k) => (
                    <option key={k} value={k}>{URL_MATCH_LABELS[k]}</option>
                  ))}
                </select>
                <input
                  type="text"
                  className="mono"
                  aria-label="URL"
                  value={form.urlMatch === 'any' ? '' : form.url}
                  disabled={form.urlMatch === 'any'}
                  placeholder={form.urlMatch === 'any' ? `anything under ${project.basePath}` : '/customers/{id}'}
                  onChange={(e) => update({ url: e.target.value })}
                />
              </div>
              <p className="muted small mono">
                {project.mockUrl}
                {form.urlMatch === 'any' ? '/…' : form.url}
              </p>
            </div>
          </div>

          {soap && (
            <div className="section">
              <h3>SOAP</h3>
              <div className="grid three">
                <label className="field">
                  <span>Operation element</span>
                  <input type="text" className="mono" value={form.soapOperation} onChange={(e) => update(applySoapDefaults({ ...form, soapOperation: e.target.value }))} />
                  <span className="hint">The first element inside soap:Body, e.g. GetCustomer</span>
                </label>
                <label className="field">
                  <span>SOAPAction <span className="hint">optional, used in exports</span></span>
                  <input type="text" className="mono" value={form.soapAction} onChange={(e) => update({ soapAction: e.target.value })} />
                </label>
                <div className="field">
                  <span>SOAP version</span>
                  <div className="segmented" role="group" aria-label="SOAP version">
                    {(['1.1', '1.2'] as const).map((v) => (
                      <button type="button" key={v} aria-pressed={form.soapVersion === v} onClick={() => update(applySoapDefaults({ ...form, soapVersion: v }))}>
                        {v}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className="section">
            <h3>Query parameters <span className="muted small">all must match</span></h3>
            <ValueMatcherRows label="Parameter" keyPlaceholder="status" rows={form.query} onChange={(query) => update({ query })} disabled={readOnly} />
          </div>
          <div className="section">
            <h3>Headers</h3>
            <ValueMatcherRows label="Header" keyPlaceholder="Authorization" rows={form.headers} onChange={(headers) => update({ headers })} disabled={readOnly} />
          </div>
          <div className="section">
            <h3>Cookies</h3>
            <ValueMatcherRows label="Cookie" keyPlaceholder="session" rows={form.cookies} onChange={(cookies) => update({ cookies })} disabled={readOnly} />
          </div>
          <div className="section">
            <h3>
              Body {soap ? <span className="muted small">extra XPath conditions, e.g. a field value</span> : <span className="muted small">all must match</span>}
            </h3>
            <BodyMatcherRows rows={form.body} onChange={(body) => update({ body })} disabled={readOnly} />
          </div>

          <div className="section">
            <h3>
              Response
              <div className="segmented" role="group" aria-label="Response type">
                {(['direct', 'proxy', 'fault'] as ResponseMode[]).map((m) => (
                  <button type="button" key={m} aria-pressed={form.responseMode === m} onClick={() => update({ responseMode: m })}>
                    {m === 'direct' ? 'Respond' : m === 'proxy' ? 'Proxy' : 'Fault'}
                  </button>
                ))}
              </div>
            </h3>

            {form.responseMode === 'direct' && (
              <div className="rows" style={{ gap: 14 }}>
                <div className="grid two">
                  <label className="field">
                    <span>Status</span>
                    <input type="number" className="mono" value={form.status} onChange={(e) => update({ status: e.target.value })} />
                  </label>
                  <label className="field">
                    <span>Status message <span className="hint">optional</span></span>
                    <input type="text" value={form.statusMessage} onChange={(e) => update({ statusMessage: e.target.value })} />
                  </label>
                </div>
                <div className="field">
                  <span>Headers</span>
                  <KeyValueRows rows={form.responseHeaders} onChange={(responseHeaders) => update({ responseHeaders })} disabled={readOnly} />
                </div>
                <div className="field">
                  <span style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    Body
                    <span className="btn-row">
                      {aiEnabled && canEdit && ['json', 'xml', 'text'].includes(form.bodyType) && (
                        <button type="button" className="btn small" onClick={() => setGenerating(true)}>
                          <Sparkle size={13} /> Generate with AI
                        </button>
                      )}
                      {soap && (
                        <>
                          <button type="button" className="btn small" onClick={() => update({ responseBody: soapEnvelope(form.soapVersion, `<ns:${form.soapOperation || 'Operation'}Response xmlns:ns="http://example.com/">\n      <ns:result>ok</ns:result>\n    </ns:${form.soapOperation || 'Operation'}Response>`) })}>
                            Insert envelope
                          </button>
                          <button type="button" className="btn small" onClick={() => update({ responseBody: soapFault(form.soapVersion, 'Something went wrong'), status: '500' })}>
                            Insert fault
                          </button>
                        </>
                      )}
                      <span className="segmented" role="group" aria-label="Body type">
                        {BODY_TYPES.map((b) => (
                          <button type="button" key={b.value} aria-pressed={form.bodyType === b.value} onClick={() => update({ bodyType: b.value })}>
                            {b.label}
                          </button>
                        ))}
                      </span>
                    </span>
                  </span>
                  {form.bodyType === 'file' ? (
                    <div className="file-pick">
                      <select aria-label="Response file" value={form.responseBody} disabled={readOnly} onChange={(e) => {
                        const picked = (files ?? []).find((f) => f.path === e.target.value);
                        update(picked ? withFile(form, picked.path, picked.contentType) : { responseBody: e.target.value });
                      }}>
                        <option value="">Choose a file…</option>
                        {(files ?? []).map((f) => <option key={f.path} value={f.path}>{f.name} ({formatSize(f.size)})</option>)}
                        {form.responseBody && !(files ?? []).some((f) => f.path === form.responseBody) && (
                          <option value={form.responseBody}>{form.responseBody}</option>
                        )}
                      </select>
                      {!readOnly && (
                        <UploadButton projectId={project.id} small onUploaded={(f) => { reloadFiles(); update(withFile(form, f.path, f.contentType)); }} />
                      )}
                    </div>
                  ) : form.bodyType === 'base64' ? (
                    <textarea className="mono" rows={3} placeholder="Base64-encoded bytes" value={form.responseBody} onChange={(e) => update({ responseBody: e.target.value })} />
                  ) : form.bodyType !== 'none' ? (
                    <CodeEditor label="Response body" value={form.responseBody} language={bodyLanguage} readOnly={readOnly} minHeight="160px" onChange={(responseBody) => update({ responseBody })} />
                  ) : null}
                </div>
                <label className="check">
                  <input type="checkbox" checked={form.templating} onChange={(e) => update({ templating: e.target.checked })} />
                  Response templating
                  <span className="muted small">
                    use <span className="mono">{'{{request.path.id}}'}</span>, <span className="mono">{"{{jsonPath request.body '$.name'}}"}</span>,{' '}
                    <span className="mono">{"{{fake 'Name.fullName'}}"}</span>, <span className="mono">{'{{now}}'}</span>
                  </span>
                </label>
                {!readOnly && (
                  <div className="btn-row">
                    <FakeDataMenu onPick={() => update({ templating: true })} />
                    <button type="button" className="btn small" onClick={() => setGeneratingData(true)}>
                      <Table size={14} /> Generate data
                    </button>
                  </div>
                )}
              </div>
            )}

            {form.responseMode === 'proxy' && (
              <div className="grid two">
                <label className="field">
                  <span>Forward to</span>
                  <input type="url" className="mono" placeholder="https://api.example.com" value={form.proxyBaseUrl} onChange={(e) => update({ proxyBaseUrl: e.target.value })} />
                  <span className="hint">The request path after {project.basePath} is appended.</span>
                </label>
                <label className="field">
                  <span>Remove prefix <span className="hint">optional, default {project.basePath}</span></span>
                  <input type="text" className="mono" value={form.proxyRemovePrefix} onChange={(e) => update({ proxyRemovePrefix: e.target.value })} />
                </label>
              </div>
            )}

            {form.responseMode === 'fault' && (
              <label className="field">
                <span>Fault</span>
                <select value={form.fault} onChange={(e) => update({ fault: e.target.value as StubForm['fault'] })}>
                  {FAULTS.map((f) => (
                    <option key={f} value={f}>{f.toLowerCase().replace(/_/g, ' ')}</option>
                  ))}
                </select>
              </label>
            )}
          </div>

          {form.responseMode !== 'fault' && (
            <div className="section">
              <h3>Delay</h3>
              <div className="rows">
                <div className="segmented" role="group" aria-label="Delay">
                  {DELAYS.map((d) => (
                    <button type="button" key={d.value} aria-pressed={form.delayType === d.value} onClick={() => update({ delayType: d.value })}>
                      {d.label}
                    </button>
                  ))}
                </div>
                {form.delayType === 'fixed' && (
                  <label className="field" style={{ maxWidth: 220 }}>
                    <span>Milliseconds</span>
                    <input type="number" value={form.fixedDelay} onChange={(e) => update({ fixedDelay: e.target.value })} />
                  </label>
                )}
                {form.delayType === 'uniform' && (
                  <div className="grid two" style={{ maxWidth: 460 }}>
                    <label className="field"><span>From ms</span><input type="number" value={form.delayLower} onChange={(e) => update({ delayLower: e.target.value })} /></label>
                    <label className="field"><span>To ms</span><input type="number" value={form.delayUpper} onChange={(e) => update({ delayUpper: e.target.value })} /></label>
                  </div>
                )}
                {form.delayType === 'lognormal' && (
                  <div className="grid two" style={{ maxWidth: 460 }}>
                    <label className="field"><span>Median ms</span><input type="number" value={form.delayMedian} onChange={(e) => update({ delayMedian: e.target.value })} /></label>
                    <label className="field"><span>Spread (sigma)</span><input type="number" step="0.1" value={form.delaySigma} onChange={(e) => update({ delaySigma: e.target.value })} /></label>
                  </div>
                )}
                {form.delayType === 'chunked' && (
                  <div className="grid two" style={{ maxWidth: 460 }}>
                    <label className="field"><span>Chunks</span><input type="number" value={form.chunks} onChange={(e) => update({ chunks: e.target.value })} /></label>
                    <label className="field"><span>Total ms</span><input type="number" value={form.chunkDuration} onChange={(e) => update({ chunkDuration: e.target.value })} /></label>
                  </div>
                )}
              </div>
            </div>
          )}

          {form.responseMode !== 'fault' && (
            <div className="section">
              <h3>Failures <span className="muted small">optional: make some calls fail at random</span></h3>
              <div className="rows">
                <div className="failure-row">
                  <label className="field">
                    <span>Fail</span>
                    <span className="input-suffix">
                      <input type="number" min={0} max={100} step="any" aria-label="Failure rate in percent" placeholder="0" value={form.errorPercent} onChange={(e) => update({ errorPercent: e.target.value })} />
                      <span>% of calls</span>
                    </span>
                  </label>
                  <div className="field">
                    <span>with</span>
                    <div className="segmented" role="group" aria-label="Failure type">
                      <button type="button" aria-pressed={form.errorMode === 'status'} onClick={() => update({ errorMode: 'status' })}>Error status</button>
                      <button type="button" aria-pressed={form.errorMode === 'fault'} onClick={() => update({ errorMode: 'fault' })}>Network fault</button>
                    </div>
                  </div>
                  {form.errorMode === 'status' ? (
                    <label className="field">
                      <span>Status</span>
                      <input type="number" className="mono" aria-label="Failure status" value={form.errorStatus} onChange={(e) => update({ errorStatus: e.target.value })} />
                    </label>
                  ) : (
                    <label className="field">
                      <span>Fault</span>
                      <select aria-label="Failure fault" value={form.errorFault} onChange={(e) => update({ errorFault: e.target.value as StubForm['errorFault'] })}>
                        {FAULTS.map((f) => <option key={f} value={f}>{f.toLowerCase().replace(/_/g, ' ')}</option>)}
                      </select>
                    </label>
                  )}
                </div>
                {Number(form.errorPercent) > 0 && form.errorMode === 'status' && (
                  <div className="field">
                    <span>Failure body <span className="hint">optional, leave empty for the default</span></span>
                    <CodeEditor
                      label="Failure body"
                      value={form.errorBody}
                      language={soap ? 'xml' : 'json'}
                      readOnly={readOnly}
                      minHeight="60px"
                      maxHeight="200px"
                      onChange={(errorBody) => update({ errorBody })}
                    />
                  </div>
                )}
                {Number(form.errorPercent) > 0 && (
                  <p className="muted small">
                    {form.errorMode === 'status'
                      ? `About ${form.errorPercent}% of calls get ${form.errorStatus || 500}${form.errorBody.trim() ? ' with the body above' : soap ? ' with a SOAP fault' : ' with {"error":"Simulated failure"}'} and the header X-MockHub-Simulated-Error. The rest get the normal response.`
                      : `About ${form.errorPercent}% of calls end with a ${form.errorFault.toLowerCase().replace(/_/g, ' ')}. The rest get the normal response.`}
                  </p>
                )}
              </div>
            </div>
          )}

          <div className="section">
            <h3>Scenario <span className="muted small">optional: answer differently as state changes between calls</span></h3>
            <div className="grid three">
              <label className="field">
                <span>Scenario name</span>
                <input type="text" value={form.scenarioName} placeholder="Checkout" onChange={(e) => update({ scenarioName: e.target.value })} />
              </label>
              <label className="field">
                <span>Only when state is</span>
                <input type="text" value={form.requiredState} placeholder="Started" disabled={!form.scenarioName} onChange={(e) => update({ requiredState: e.target.value })} />
              </label>
              <label className="field">
                <span>Then move to state</span>
                <input type="text" value={form.newState} placeholder="unchanged" disabled={!form.scenarioName} onChange={(e) => update({ newState: e.target.value })} />
              </label>
            </div>
          </div>

          <div className="section">
            <h3>
              Then call <span className="muted small">optional: send webhooks after responding</span>
            </h3>
            {form.webhooks.length === 0 && (
              <p className="muted small">
                Call another service when this stub answers, e.g. to simulate a payment callback. Use{' '}
                <span className="mono">{'{{originalRequest.body}}'}</span> or <span className="mono">{"{{jsonPath originalRequest.body '$.id'}}"}</span>{' '}
                to pass data from the request.
              </p>
            )}
            {form.webhooks.map((w, i) => {
              const set = (patch: Partial<Webhook>) => update({ webhooks: form.webhooks.map((x, j) => (j === i ? { ...x, ...patch } : x)) });
              return (
                <div key={i} className="webhook">
                  <div className="webhook-head">
                    <select aria-label={`Webhook ${i + 1} method`} value={w.method} disabled={readOnly} onChange={(e) => set({ method: e.target.value })}>
                      {WEBHOOK_METHODS.map((m) => <option key={m}>{m}</option>)}
                    </select>
                    <input type="text" className="mono" aria-label={`Webhook ${i + 1} URL`} placeholder="https://partner.example.com/callbacks" value={w.url} disabled={readOnly} onChange={(e) => set({ url: e.target.value })} />
                    <input type="number" min={0} aria-label={`Webhook ${i + 1} delay`} placeholder="delay ms" value={w.delay} disabled={readOnly} onChange={(e) => set({ delay: e.target.value })} />
                    {!readOnly && (
                      <button type="button" className="btn ghost icon small" aria-label={`Remove webhook ${i + 1}`} onClick={() => update({ webhooks: form.webhooks.filter((_, j) => j !== i) })}>
                        <Trash size={14} />
                      </button>
                    )}
                  </div>
                  <KeyValueRows rows={w.headers} onChange={(headers) => set({ headers })} disabled={readOnly} />
                  {w.method !== 'GET' && (
                    <CodeEditor label={`Webhook ${i + 1} body`} value={w.body} language="json" readOnly={readOnly} minHeight="70px" maxHeight="220px" onChange={(body) => set({ body })} />
                  )}
                </div>
              );
            })}
            {!readOnly && (
              <button type="button" className="btn small" onClick={() => update({ webhooks: [...form.webhooks, emptyWebhook()] })}>
                <Plus size={13} /> Add webhook
              </button>
            )}
          </div>

          <div className="section">
            <h3>Example request body <span className="muted small">used by Try it and the Postman / Bruno exports</span></h3>
            <CodeEditor label="Example request body" value={form.sampleBody} language={soap ? 'xml' : 'json'} readOnly={readOnly} minHeight="70px" onChange={(sampleBody) => update({ sampleBody })} />
          </div>
        </fieldset>
      )}

      {!isNew && <TryIt project={project} form={mode === 'form' ? form : fromStub(preview).form} />}
      {!isNew && (
        <div className="section">
          <h3>Call it from your code</h3>
          <CodeSnippets request={sampleRequest(mode === 'form' ? form : fromStub(preview).form, project.apiKey)} baseUrl={project.mockUrl} />
        </div>
      )}

      {generatingData && (
        <DataGeneratorDialog
          useLabel="Use as response body"
          onClose={() => setGeneratingData(false)}
          onUse={(json) => {
            update({ responseBody: json, bodyType: 'json' });
            setGeneratingData(false);
          }}
        />
      )}
      {generating && (
        <AiBodyDialog
          projectId={project.id}
          format={form.bodyType as BodyFormat}
          method={form.method === 'ANY' ? 'GET' : form.method}
          path={form.urlMatch === 'any' ? '' : form.url}
          status={Number(form.status) || 200}
          current={form.responseBody}
          onClose={() => setGenerating(false)}
          onUse={(responseBody) => {
            update({ responseBody });
            setGenerating(false);
          }}
        />
      )}
      {confirmDelete && (
        <Confirm
          title="Delete stub?"
          message="Requests that only this stub matched will get a 404 afterwards."
          confirmLabel="Delete stub"
          onConfirm={remove}
          onCancel={() => setConfirmDelete(false)}
        />
      )}
      {moving && (
        <Modal title="Move stub" description="The stub keeps its relative path under the new project's base URL." onClose={() => setMoving(false)}>
          <div className="rows">
            {projects
              .filter((p) => p.id !== project.id)
              .map((p) => (
                <button key={p.id} className="btn" style={{ justifyContent: 'flex-start' }} onClick={() => move(p.id)}>
                  <span className="swatch" style={{ background: p.color }} /> {p.name} <span className="muted mono small">{p.basePath}</span>
                </button>
              ))}
          </div>
        </Modal>
      )}
    </div>
  );
}
