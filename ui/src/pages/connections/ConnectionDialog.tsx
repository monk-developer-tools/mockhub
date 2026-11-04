import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Connection } from '../../api/types';
import { Modal } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { connectionForm, connectionInput, connectionProblem, slugKey, withType, type ConnectionForm } from '../../model/connections';

export function ConnectionDialog({ orgId, connection, onClose, onSaved }: {
  orgId: string;
  connection?: Connection;
  onClose: () => void;
  onSaved: (connection: Connection) => void;
}) {
  const toast = useToast();
  const editing = Boolean(connection);
  const [form, setForm] = useState<ConnectionForm>(() => connectionForm(connection));
  const [keyTouched, setKeyTouched] = useState(editing);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const update = (patch: Partial<ConnectionForm>) => setForm((f) => ({ ...f, ...patch }));
  const key = keyTouched ? form.key : slugKey(form.name);
  const oauth = form.type === 'oauth2';

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const current = { ...form, key };
    const problem = connectionProblem(current, editing);
    if (problem) return setError(problem);
    setSaving(true);
    setError('');
    try {
      const body = connectionInput(current, editing);
      const saved = connection ? await api.updateConnection(orgId, connection.key, body) : await api.createConnection(orgId, body);
      toast.notify(editing ? 'Connection saved' : `Connection ${saved.key} added`);
      onSaved(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={connection ? `Settings for ${connection.key}` : 'New connection'}
      description="Secrets are encrypted on the server and never shown again. Callouts and proxies that name this connection send the header for you."
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" type="submit" form="connection-form" disabled={saving}>{editing ? 'Save' : 'Add connection'}</button>
        </>
      }
    >
      <form id="connection-form" className="stack" onSubmit={submit}>
        <div className="segmented" role="group" aria-label="Connection type">
          <button type="button" aria-pressed={oauth} onClick={() => setForm(withType(form, 'oauth2'))}>OAuth2 client credentials</button>
          <button type="button" aria-pressed={!oauth} onClick={() => setForm(withType(form, 'header'))}>API key header</button>
        </div>
        <div className="grid two">
          <label className="field">
            <span>Name</span>
            <input type="text" value={form.name} onChange={(e) => update({ name: e.target.value })} placeholder="Bank API sandbox" autoFocus />
          </label>
          <label className="field">
            <span>Key</span>
            <input type="text" className="mono" value={key} disabled={editing}
              onChange={(e) => { setKeyTouched(true); update({ key: e.target.value }); }} placeholder="bank" />
            <span className="hint">Stubs name it: "connection": "{key || 'bank'}"</span>
          </label>
        </div>
        {oauth && (
          <>
            <label className="field">
              <span>Token URL</span>
              <input type="text" className="mono" value={form.tokenUrl} onChange={(e) => update({ tokenUrl: e.target.value })}
                placeholder="https://login.example.com/oauth2/token" />
            </label>
            <div className="grid two">
              <label className="field">
                <span>Client id</span>
                <input type="text" className="mono" value={form.clientId} onChange={(e) => update({ clientId: e.target.value })} />
              </label>
              <label className="field">
                <span>Client secret {editing && <span className="hint">leave empty to keep the saved one</span>}</span>
                <input type="password" autoComplete="new-password" value={form.secret} onChange={(e) => update({ secret: e.target.value })} />
              </label>
            </div>
            <div className="grid three">
              <label className="field">
                <span>Scope <span className="hint">optional</span></span>
                <input type="text" className="mono" value={form.scope} onChange={(e) => update({ scope: e.target.value })} placeholder="accounts.read" />
              </label>
              <label className="field">
                <span>Audience <span className="hint">optional</span></span>
                <input type="text" className="mono" value={form.audience} onChange={(e) => update({ audience: e.target.value })} />
              </label>
              <label className="field">
                <span>Send the client</span>
                <select value={form.clientAuth} onChange={(e) => update({ clientAuth: e.target.value as ConnectionForm['clientAuth'] })}>
                  <option value="basic">in a Basic auth header</option>
                  <option value="body">in the form body</option>
                </select>
              </label>
            </div>
          </>
        )}
        <div className="grid two">
          <label className="field">
            <span>Header name</span>
            <input type="text" className="mono" value={form.headerName} onChange={(e) => update({ headerName: e.target.value })} />
          </label>
          <label className="field">
            <span>Value prefix <span className="hint">e.g. "Bearer "</span></span>
            <input type="text" className="mono" value={form.headerPrefix} onChange={(e) => update({ headerPrefix: e.target.value })} />
          </label>
        </div>
        {!oauth && (
          <label className="field">
            <span>Header value {editing && <span className="hint">leave empty to keep the saved one</span>}</span>
            <input type="password" autoComplete="new-password" value={form.secret} onChange={(e) => update({ secret: e.target.value })} />
          </label>
        )}
        {oauth && (
          <p className="muted small">
            MockHub asks the token URL for a token with the client credentials grant, keeps it until shortly before it expires and
            sends <span className="mono">{form.headerName || 'Authorization'}: {form.headerPrefix}&lt;token&gt;</span>.
          </p>
        )}
        {error && <div className="callout danger" role="alert">{error}</div>}
      </form>
    </Modal>
  );
}
