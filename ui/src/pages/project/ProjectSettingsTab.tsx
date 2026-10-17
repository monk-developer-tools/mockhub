import { Key } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { CopyButton } from '../../components/CopyButton';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { Confirm } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { useApp } from '../../state/AppContext';
import { ProjectDialog } from '../ProjectDialog';
import { useProject } from './ProjectPage';

export function ProjectSettingsTab() {
  const { project, reloadProject } = useProject();
  const { canEdit, refreshProjects } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const remove = async () => {
    try {
      await api.deleteProject(project.id);
      await refreshProjects();
      toast.notify('Project deleted');
      navigate('/');
    } catch (e) {
      toast.fail(e);
    }
  };

  return (
    <div className="stack" style={{ maxWidth: 760 }}>
      <div className="panel">
        <div className="section">
          <h3>
            Details
            {canEdit && <button className="btn small" onClick={() => setEditing(true)}>Edit</button>}
          </h3>
          <table className="kv-table">
            <tbody>
              <tr><td>Name</td><td>{project.name}</td></tr>
              <tr><td>Base path</td><td>{project.basePath}</td></tr>
              <tr><td>Mock URL</td><td>{project.mockUrl}</td></tr>
              <tr><td>Created</td><td>{new Date(project.createdAt).toLocaleString()}</td></tr>
              <tr><td>Updated</td><td>{new Date(project.updatedAt).toLocaleString()}</td></tr>
            </tbody>
          </table>
        </div>
        <div className="section">
          <h3>Use it from your service</h3>
          <p className="muted small" style={{ marginBottom: 8 }}>
            Replace the real dependency's base URL with the mock URL, for example in your test environment's config:
          </p>
          <pre className="block">{`PAYMENT_API_URL=${project.mockUrl}`}</pre>
        </div>
      </div>
      <ApiKeyPanel />
      {canEdit && (
        <div className="panel">
          <div className="section">
            <h3>Delete project</h3>
            <p className="muted small" style={{ marginBottom: 10 }}>
              Deletes the project and its {project.stubCount} stubs. Download a WireMock export first if you might need them again.
            </p>
            <button className="btn danger" onClick={() => setDeleting(true)}>Delete project</button>
          </div>
        </div>
      )}
      {editing && (
        <ProjectDialog
          project={project}
          onClose={() => {
            setEditing(false);
            reloadProject();
          }}
        />
      )}
      {deleting && (
        <Confirm
          title={`Delete ${project.name}?`}
          message={`This removes the project and all ${project.stubCount} stubs under ${project.basePath}. It can't be undone.`}
          confirmLabel="Delete project"
          onConfirm={remove}
          onCancel={() => setDeleting(false)}
        />
      )}
    </div>
  );
}

export function randomKey() {
  const bytes = new Uint8Array(18);
  crypto.getRandomValues(bytes);
  return 'mk_' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

function ApiKeyPanel() {
  const { project, reloadProject } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const current = project.apiKey ?? null;
  const [on, setOn] = useState(Boolean(current));
  const [header, setHeader] = useState(current?.header ?? 'X-API-Key');
  const [value, setValue] = useState(current?.value ?? '');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setOn(Boolean(project.apiKey));
    setHeader(project.apiKey?.header ?? 'X-API-Key');
    setValue(project.apiKey?.value ?? '');
  }, [project.apiKey]);

  const changed = on !== Boolean(current) || (on && (header !== current?.header || value !== current?.value));

  const save = async () => {
    setBusy(true);
    try {
      await api.updateProject(project.id, { apiKey: on ? { header: header.trim(), value: value.trim() } : null });
      toast.notify(on ? 'API key required' : 'API key turned off');
      reloadProject();
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const example = `curl ${project.mockUrl}/… -H '${header || 'X-API-Key'}: ${value || 'your-key'}'`;

  return (
    <div className="panel">
      <div className="section">
        <h3>
          <span><Key size={15} weight="duotone" style={{ verticalAlign: -2, marginRight: 6 }} />Require an API key</span>
          <label className="switch">
            <input type="checkbox" role="switch" checked={on} disabled={!canEdit} aria-label="Require an API key"
              onChange={(e) => {
                setOn(e.target.checked);
                if (e.target.checked && !value) setValue(randomKey());
              }} />
          </label>
        </h3>
        <p className="muted small" style={{ marginBottom: 10 }}>
          Every call to {project.basePath} must send this header, like the real service would. Calls without it get a 401
          (a SOAP fault for SOAP calls). <span className="mono" style={{ whiteSpace: 'nowrap' }}>?wsdl</span> stays open. Try it, code snippets and Postman / Bruno
          exports include the key automatically.
        </p>
        {on && (
          <div className="stack">
            <div className="grid two">
              <label className="field">
                <span>Header</span>
                <input type="text" className="mono" value={header} disabled={!canEdit} onChange={(e) => setHeader(e.target.value)} />
              </label>
              <label className="field">
                <span>Key</span>
                <span className="input-suffix">
                  <input type="text" className="mono" style={{ width: '100%' }} value={value} disabled={!canEdit} onChange={(e) => setValue(e.target.value)} />
                  {canEdit && <button type="button" className="btn small" onClick={() => setValue(randomKey())}>New</button>}
                </span>
              </label>
            </div>
            <div className="copy-field">
              <code>{example}</code>
              <CopyButton text={example} label="Copy example" />
            </div>
          </div>
        )}
        {canEdit && changed && (
          <div className="btn-row" style={{ marginTop: 12 }}>
            <button className="btn primary" disabled={busy || (on && !value.trim())} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
          </div>
        )}
      </div>
    </div>
  );
}
