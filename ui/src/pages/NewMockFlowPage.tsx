import { ArrowLeft, Sparkle } from '@phosphor-icons/react';
import { useMemo, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { CopyButton } from '../components/CopyButton';
import { FilePicker } from '../components/FilePicker';
import { useToast } from '../components/Toasts';
import { PageHeader } from '../components/ui/PageHeader';
import { createMock, freeSlug, type Content, type Target } from '../model/createMock';
import {
  aiPrompt, callbackStub, cardFor, crudRequest, detectContentType, echoStub, isStartKind, nameFromSource, payloadStub, proxyStub,
  receiverStub, SAMPLES, tunnelCommand, type StartKind,
} from '../model/starts';
import { atLeast, useApp } from '../state/AppContext';
import { slugify } from './ProjectDialog';

const ACCEPT: Partial<Record<StartKind, string>> = {
  openapi: '.yaml,.yml,.json',
  wsdl: '.wsdl,.xml',
  graphql: '.graphql,.graphqls,.gql,.json,.txt',
  grpc: '.proto,.txt',
  crud: '.csv,.json,.txt',
  postman: '.json',
  json: '.json,.xml,.txt',
};

const SOURCE_LABEL: Partial<Record<StartKind, string>> = {
  openapi: 'OpenAPI 3 or Swagger 2 file',
  wsdl: 'WSDL 1.1 file',
  graphql: 'GraphQL schema (SDL) or introspection result',
  grpc: '.proto file',
  postman: 'Postman collection (v2.0 or v2.1)',
};

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

export function NewMockFlowPage() {
  const { kind: param } = useParams();
  if (!isStartKind(param)) return <Navigate to="/new" replace />;
  return <Flow key={param} kind={param} />;
}

function Flow({ kind }: { kind: StartKind }) {
  const navigate = useNavigate();
  const { search } = useLocation();
  const query = new URLSearchParams(search);
  const toast = useToast();
  const { info, currentOrg, orgProjects, projects, writableTeams, roleInTeam, refreshProjects, canEdit, aiStatus } = useApp();
  const card = cardFor(kind);

  const [text, setText] = useState('');
  const [fileName, setFileName] = useState('');
  const [sourceTab, setSourceTab] = useState<'file' | 'url' | 'paste'>('file');
  const [url, setUrl] = useState('');
  const [stripPrefix, setStripPrefix] = useState('');
  const [keepContract, setKeepContract] = useState(false);
  const [method, setMethod] = useState('GET');
  const [path, setPath] = useState(kind === 'webhook' ? '/hooks' : kind === 'echo' ? '/echo' : '/data');
  const [status, setStatus] = useState(kind === 'webhook' ? 202 : 200);
  const [contentType, setContentType] = useState('application/json');
  const [hookMode, setHookMode] = useState<'receive' | 'send'>('receive');
  const [callbackUrl, setCallbackUrl] = useState('');
  const [delay, setDelay] = useState(2);
  const [targetKind, setTargetKind] = useState<'url' | 'tunnel'>(query.get('target') === 'tunnel' ? 'tunnel' : 'url');
  const [serviceUrl, setServiceUrl] = useState('');
  const [tunnelName, setTunnelName] = useState('my-laptop');
  const [resource, setResource] = useState('users');
  const [description, setDescription] = useState('');

  const teams = writableTeams(currentOrg?.id);
  const editable = useMemo(
    () => orgProjects.filter((p) => atLeast(roleInTeam(p.orgId, p.teamId), 'editor')),
    [orgProjects, roleInTeam],
  );
  const [mode, setMode] = useState<'new' | 'existing'>(teams.length ? 'new' : 'existing');
  const [name, setName] = useState('');
  const [nameTouched, setNameTouched] = useState(false);
  const [teamId, setTeamId] = useState('');
  const [projectId, setProjectId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notes, setNotes] = useState<string[]>([]);

  const suggested = kind === 'crud' ? title(resource) : kind === 'proxy' ? (targetKind === 'tunnel' ? title(tunnelName) : hostOf(serviceUrl)) : nameFromSource(kind, text, fileName);
  const projectName = nameTouched ? name : suggested || card.title;
  const chosenTeam = teamId || teams[0]?.id || '';
  const chosenProject = projectId || editable[0]?.id || '';
  const usesSource = kind in SOURCE_LABEL;

  const takeText = (value: string, file = '') => {
    setText(value);
    setFileName(file);
    setError('');
  };

  const loadUrl = async () => {
    setBusy(true);
    setError('');
    try {
      const spec = await api.fetchOpenApi(url.trim());
      takeText(spec.content, spec.title ? `${spec.title}.json` : '');
      if (!nameTouched && spec.title) setName(spec.title);
      setSourceTab('paste');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const content = (): Content => {
    if (kind === 'openapi' || kind === 'wsdl' || kind === 'graphql' || kind === 'postman') {
      if (!text.trim()) throw new Error(`Upload or paste the ${SOURCE_LABEL[kind]} first, or use the sample.`);
      const options: Record<string, string> = {};
      if ((kind === 'openapi' || kind === 'postman') && stripPrefix.trim()) options.stripPrefix = stripPrefix.trim();
      if (kind === 'openapi' && keepContract) options.contract = 'true';
      return { kind, text, options };
    }
    if (kind === 'grpc') {
      if (!text.trim()) throw new Error('Upload or paste a .proto file first, or use the sample.');
      return { kind, text, fileName: fileName || 'service.proto' };
    }
    if (kind === 'crud') return { kind, request: crudRequest(resource, text) };
    if (kind === 'json') return { kind, stubs: [payloadStub({ method, path, status, contentType, body: text })] };
    if (kind === 'webhook') {
      return { kind, stubs: [hookMode === 'receive' ? receiverStub(path) : callbackStub({ path, status, callbackUrl, delaySeconds: delay })] };
    }
    if (kind === 'proxy') return { kind, stubs: [proxyStub(targetKind === 'tunnel' ? `tunnel://${tunnelName.trim()}` : serviceUrl)] };
    if (kind === 'echo') return { kind, stubs: [echoStub(path)] };
    aiPrompt(description);
    return { kind: 'ai' };
  };

  const target = (): Target => {
    if (mode === 'existing') {
      if (!chosenProject) throw new Error('Choose the project to add to.');
      return { mode, projectId: chosenProject };
    }
    if (!projectName.trim()) throw new Error('Give the project a name.');
    if (!currentOrg || !chosenTeam) throw new Error('You need the manager role in a team to create a project. Add to an existing project instead.');
    return {
      mode,
      name: projectName.trim(),
      slug: freeSlug(slugify(projectName), projects.map((p) => p.slug)),
      orgId: currentOrg.id,
      teamId: chosenTeam,
      description: `Made from ${fileName || card.title}`,
    };
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setNotes([]);
    let plan: { target: Target; content: Content };
    try {
      plan = { target: target(), content: content() };
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      return;
    }
    setBusy(true);
    try {
      const created = await createMock(api, plan.target, plan.content);
      await refreshProjects();
      if (kind === 'ai') {
        navigate(`/projects/${created.projectId}/assistant`, { state: { prompt: aiPrompt(description) } });
        return;
      }
      toast.notify(created.made === 1 ? 'Your mock is ready' : `Your mock is ready: ${created.made} stubs`);
      const next = new URLSearchParams({ ready: kind });
      if (created.notes.length) next.set('notes', created.notes.slice(0, 5).join('\n'));
      if (kind === 'proxy' && targetKind === 'tunnel') next.set('tunnel', tunnelName.trim());
      navigate(`/projects/${created.projectId}/overview?${next.toString()}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  if (!canEdit) return <Navigate to="/new" replace />;

  return (
    <div className="page narrow">
      <Link className="back-link" to="/new"><ArrowLeft size={14} /> All ways to start</Link>
      <PageHeader title={card.title} description={card.blurb} />
      <form className="panel" onSubmit={submit}>
        <div className="panel-body stack">
          {usesSource && (
            <section className="stack" aria-label="Source">
              <div className="segmented" role="group" aria-label="How to give the file">
                <button type="button" aria-pressed={sourceTab === 'file'} onClick={() => setSourceTab('file')}>Upload</button>
                {kind === 'openapi' && <button type="button" aria-pressed={sourceTab === 'url'} onClick={() => setSourceTab('url')}>From URL</button>}
                <button type="button" aria-pressed={sourceTab === 'paste'} onClick={() => setSourceTab('paste')}>Paste</button>
              </div>
              {sourceTab === 'file' && (
                <FilePicker accept={ACCEPT[kind] ?? '*'} label={fileName ? `Loaded ${fileName}. Drop another file to replace it` : `Drop the ${SOURCE_LABEL[kind]} here or click to choose`}
                  onText={(t, f) => takeText(t, f)} />
              )}
              {sourceTab === 'url' && (
                <div className="field-row">
                  <label className="field grow">
                    <span>Address of the spec</span>
                    <input type="url" className="mono" placeholder="https://petstore3.swagger.io/api/v3/openapi.json" value={url} onChange={(e) => setUrl(e.target.value)} />
                  </label>
                  <button type="button" className="btn" disabled={busy || !url.trim()} onClick={loadUrl}>Load</button>
                </div>
              )}
              {sourceTab === 'paste' && (
                <label className="field">
                  <span>{SOURCE_LABEL[kind]}</span>
                  <textarea className="mono" rows={12} spellCheck={false} value={text} onChange={(e) => takeText(e.target.value, fileName)} />
                </label>
              )}
              <div className="btn-row">
                {SAMPLES[kind].text && (
                  <button type="button" className="btn small ghost" onClick={() => { takeText(SAMPLES[kind].text, SAMPLES[kind].fileName); setSourceTab('paste'); }}>
                    Use a sample
                  </button>
                )}
                {text && <span className="muted small">{fileName || 'Pasted text'} · {text.length.toLocaleString()} characters</span>}
              </div>
              {(kind === 'openapi' || kind === 'postman') && (
                <label className="field">
                  <span>Strip path prefix <span className="hint">optional, like /api/v1</span></span>
                  <input type="text" className="mono" value={stripPrefix} onChange={(e) => setStripPrefix(e.target.value)} />
                </label>
              )}
              {kind === 'openapi' && (
                <label className="check">
                  <input type="checkbox" checked={keepContract} onChange={(e) => setKeepContract(e.target.checked)} /> Keep it as the contract and check traffic against it
                </label>
              )}
            </section>
          )}

          {kind === 'crud' && (
            <section className="stack" aria-label="Resource">
              <label className="field">
                <span>Resource name <span className="hint">the path, like /users</span></span>
                <input type="text" className="mono" value={resource} onChange={(e) => setResource(e.target.value)} />
              </label>
              <FilePicker accept={ACCEPT.crud ?? '*'} label={fileName ? `Loaded ${fileName}` : 'Drop a CSV or JSON file of records here, or click to choose'} onText={(t, f) => takeText(t, f)} />
              <label className="field">
                <span>Records <span className="hint">optional: a JSON array, or CSV with a header row</span></span>
                <textarea className="mono" rows={8} spellCheck={false} value={text} placeholder={SAMPLES.crud.text} onChange={(e) => takeText(e.target.value, fileName)} />
              </label>
              <div className="btn-row"><button type="button" className="btn small ghost" onClick={() => takeText(SAMPLES.crud.text, SAMPLES.crud.fileName)}>Use sample records</button></div>
            </section>
          )}

          {kind === 'json' && (
            <section className="stack" aria-label="Answer">
              <label className="field">
                <span>The answer to send</span>
                <textarea className="mono" rows={10} spellCheck={false} value={text} placeholder={SAMPLES.json.text}
                  onChange={(e) => { takeText(e.target.value); setContentType(detectContentType(e.target.value)); }} />
              </label>
              <div className="field-row">
                <label className="field"><span>Method</span>
                  <select value={method} onChange={(e) => setMethod(e.target.value)}>{METHODS.map((m) => <option key={m}>{m}</option>)}</select>
                </label>
                <label className="field grow"><span>Path</span><input type="text" className="mono" value={path} onChange={(e) => setPath(e.target.value)} /></label>
                <label className="field"><span>Status</span><input type="number" min={100} max={599} value={status} onChange={(e) => setStatus(Number(e.target.value))} /></label>
                <label className="field"><span>Content type</span>
                  <select value={contentType} onChange={(e) => setContentType(e.target.value)}>
                    {['application/json', 'application/xml', 'text/xml', 'text/html', 'text/plain', 'text/csv'].map((c) => <option key={c}>{c}</option>)}
                  </select>
                </label>
              </div>
              <div className="btn-row"><button type="button" className="btn small ghost" onClick={() => { takeText(SAMPLES.json.text); setContentType('application/json'); }}>Use a sample</button></div>
            </section>
          )}

          {kind === 'webhook' && (
            <section className="stack" aria-label="Webhook">
              <div className="segmented" role="group" aria-label="Which way">
                <button type="button" aria-pressed={hookMode === 'receive'} onClick={() => setHookMode('receive')}>Receive webhooks</button>
                <button type="button" aria-pressed={hookMode === 'send'} onClick={() => setHookMode('send')}>Answer, then call back</button>
              </div>
              <p className="muted small">
                {hookMode === 'receive'
                  ? 'An endpoint that answers 200 at once and records every webhook sent to it, with its headers and body, in the Requests tab.'
                  : 'A stub that answers at once, then POSTs to your service after a delay. Every callback, its answer and any error show in the Callouts tab.'}
              </p>
              <label className="field">
                <span>{hookMode === 'receive' ? 'Path to receive on' : 'Path your app calls'} <span className="hint">{hookMode === 'receive' ? 'everything under it is caught' : 'answered for POST'}</span></span>
                <input type="text" className="mono" value={path} onChange={(e) => setPath(e.target.value)} />
              </label>
              {hookMode === 'send' && (
                <div className="field-row">
                  <label className="field grow"><span>Call back to</span>
                    <input type="url" className="mono" placeholder="https://your-service.example.com/hooks" value={callbackUrl} onChange={(e) => setCallbackUrl(e.target.value)} />
                  </label>
                  <label className="field"><span>After (seconds)</span><input type="number" min={0} max={300} value={delay} onChange={(e) => setDelay(Number(e.target.value))} /></label>
                  <label className="field"><span>Answer status</span><input type="number" min={100} max={599} value={status} onChange={(e) => setStatus(Number(e.target.value))} /></label>
                </div>
              )}
            </section>
          )}

          {kind === 'proxy' && (
            <section className="stack" aria-label="Service">
              <div className="segmented" role="group" aria-label="Where the service is">
                <button type="button" aria-pressed={targetKind === 'url'} onClick={() => setTargetKind('url')}>On the internet</button>
                <button type="button" aria-pressed={targetKind === 'tunnel'} onClick={() => setTargetKind('tunnel')}>On my machine (tunnel)</button>
              </div>
              {targetKind === 'url' ? (
                <label className="field">
                  <span>Address of the service</span>
                  <input type="url" className="mono" placeholder="https://api.example.com" value={serviceUrl} onChange={(e) => setServiceUrl(e.target.value)} />
                </label>
              ) : (
                <>
                  <label className="field">
                    <span>Tunnel name</span>
                    <input type="text" className="mono" value={tunnelName} onChange={(e) => setTunnelName(e.target.value)} />
                  </label>
                  <div className="code-line">
                    <code className="mono small">{tunnelCommand(info?.baseUrl ?? window.location.origin, tunnelName.trim() || 'my-laptop')}</code>
                    <CopyButton text={tunnelCommand(info?.baseUrl ?? window.location.origin, tunnelName.trim() || 'my-laptop')} />
                  </div>
                  <p className="muted small">Run this on your machine with an access token (your avatar → Access tokens), and change the port to your service's.</p>
                </>
              )}
              <p className="muted small">Every call to the project goes on to the service and is recorded in Requests. Add stubs later to replace one answer at a time.</p>
            </section>
          )}

          {kind === 'echo' && (
            <label className="field">
              <span>Path <span className="hint">every method and everything under it is echoed</span></span>
              <input type="text" className="mono" value={path} onChange={(e) => setPath(e.target.value)} />
            </label>
          )}

          {kind === 'ai' && (
            <section className="stack" aria-label="Description">
              {!aiStatus?.enabled && <div className="callout warn">AI isn't set up on this server. An admin can turn it on in AI settings.</div>}
              <label className="field">
                <span>Describe the API</span>
                <textarea rows={5} value={description} placeholder={SAMPLES.ai.text} onChange={(e) => setDescription(e.target.value)} />
              </label>
              <p className="muted small"><Sparkle size={13} /> The assistant drafts the stubs in a new chat. You review them before anything is saved.</p>
            </section>
          )}

          <fieldset className="target">
            <legend>Where it goes</legend>
            <div className="segmented" role="group" aria-label="Where it goes">
              <button type="button" aria-pressed={mode === 'new'} disabled={!teams.length} onClick={() => setMode('new')}>A new project</button>
              <button type="button" aria-pressed={mode === 'existing'} disabled={!editable.length} onClick={() => setMode('existing')}>A project I already have</button>
            </div>
            {mode === 'new' && teams.length > 0 && (
              <div className="field-row">
                <label className="field grow">
                  <span>Project name</span>
                  <input type="text" value={projectName} onChange={(e) => { setName(e.target.value); setNameTouched(true); }} />
                </label>
                {teams.length > 1 && (
                  <label className="field"><span>Team</span>
                    <select value={chosenTeam} onChange={(e) => setTeamId(e.target.value)}>{teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</select>
                  </label>
                )}
              </div>
            )}
            {mode === 'existing' && editable.length > 0 && (
              <label className="field">
                <span>Project</span>
                <select value={chosenProject} onChange={(e) => setProjectId(e.target.value)}>{editable.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select>
              </label>
            )}
            {!teams.length && !editable.length && (
              <p className="callout">You can't create projects or edit one yet. Ask a manager of your team for the editor role.</p>
            )}
            {mode === 'new' && !teams.length && editable.length > 0 && <p className="muted small">Creating projects needs the manager role, so this goes into a project you edit.</p>}
          </fieldset>

          {error && <div className="callout danger" role="alert">{error}</div>}
          {notes.length > 0 && <div className="callout">{notes.join(' ')}</div>}
          <div className="btn-row">
            <button type="submit" className="btn primary" disabled={busy || (!teams.length && !editable.length)}>
              {busy ? 'Creating…' : kind === 'ai' ? 'Draft with AI' : 'Create mock'}
            </button>
            <Link className="btn ghost" to="/new">Cancel</Link>
          </div>
        </div>
      </form>
    </div>
  );
}

function title(text: string) {
  return text.trim().replace(/[-_]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

function hostOf(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return '';
  }
}
