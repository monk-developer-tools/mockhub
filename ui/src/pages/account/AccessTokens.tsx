import { Key, Plus, Trash } from '@phosphor-icons/react';
import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { AccessToken, TokenMode, TokenRole } from '../../api/types';
import { CopyButton } from '../../components/CopyButton';
import { Confirm } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { MCP_CLIENTS, expiryLabel, lastUsedLabel, mcpSnippet, type McpClientId } from '../../model/mcp';
import { useApp } from '../../state/AppContext';

const ROLES: { id: TokenRole; label: string }[] = [
  { id: 'viewer', label: 'Viewer: read only' },
  { id: 'editor', label: 'Editor: change stubs and data' },
  { id: 'manager', label: 'Manager: also project settings' },
  { id: 'admin', label: 'Admin: everything' },
];
const EXPIRY = [
  { days: 7, label: '7 days' },
  { days: 30, label: '30 days' },
  { days: 90, label: '90 days' },
  { days: 365, label: '1 year' },
  { days: 0, label: 'Never' },
];

function Created({ token, onDone }: { token: AccessToken & { secret: string }; onDone: () => void }) {
  const [client, setClient] = useState<McpClientId>('claude-code');
  const origin = window.location.origin;
  const file = MCP_CLIENTS.find((c) => c.id === client)?.file;
  return (
    <div className="token-created stack">
      <div className="callout" role="status">
        <div>
          <strong>{token.name}</strong> is ready. Copy it now: MockHub keeps only a fingerprint and can't show it again.
        </div>
      </div>
      <div className="token-secret">
        <code className="mono">{token.secret}</code>
        <CopyButton text={token.secret} label="Copy token" showLabel />
      </div>
      <h3>Connect an agent</h3>
      <div className="segmented" role="group" aria-label="MCP client">
        {MCP_CLIENTS.map((c) => (
          <button key={c.id} type="button" aria-pressed={client === c.id} onClick={() => setClient(c.id)}>{c.label}</button>
        ))}
      </div>
      {file && <p className="muted small">Add this to <span className="mono">{file}</span>:</p>}
      <div className="snippet">
        <pre className="block">{mcpSnippet(client, origin, token.secret)}</pre>
        <CopyButton text={mcpSnippet(client, origin, token.secret)} label="Copy setup" showLabel />
      </div>
      <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn primary" onClick={onDone}>Done</button>
      </div>
    </div>
  );
}

function NewTokenForm({ onCreated, onCancel }: { onCreated: (t: AccessToken & { secret: string }) => void; onCancel: () => void }) {
  const { session, projects } = useApp();
  const toast = useToast();
  const [name, setName] = useState('');
  const [role, setRole] = useState<TokenRole>('editor');
  const [mode, setMode] = useState<TokenMode>('direct');
  const [expiresInDays, setExpiry] = useState(90);
  const [scoped, setScoped] = useState(false);
  const [projectIds, setProjectIds] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      onCreated(await api.createAccessToken({ name: name.trim(), role, mode, expiresInDays, projectIds: scoped ? projectIds : [] }));
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  const toggle = (id: string) => setProjectIds((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  const roles = ROLES.filter((r) => r.id !== 'admin' || session?.isAdmin);

  return (
    <form className="token-form stack" onSubmit={submit} aria-label="New access token">
      <label className="field">
        <span>Name <span className="hint">where it's used, e.g. Claude Code on my laptop</span></span>
        <input type="text" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} required autoFocus />
      </label>
      <div className="grid two">
        <label className="field">
          <span>Access</span>
          <select value={role} onChange={(e) => setRole(e.target.value as TokenRole)}>
            {roles.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Expires after</span>
          <select value={expiresInDays} onChange={(e) => setExpiry(Number(e.target.value))}>
            {EXPIRY.map((x) => <option key={x.days} value={x.days}>{x.label}</option>)}
          </select>
        </label>
      </div>
      <fieldset className="field">
        <legend>Changes</legend>
        <div className="segmented" role="group" aria-label="Changes">
          <button type="button" aria-pressed={mode === 'direct'} onClick={() => setMode('direct')}>Apply directly</button>
          <button type="button" aria-pressed={mode === 'review'} onClick={() => setMode('review')}>Stage for review</button>
        </div>
        <span className="hint">
          {mode === 'direct'
            ? 'Changes go live at once, like yours do. Every change is in the audit log with the token name.'
            : 'Stub and runtime changes wait in the project’s Assistant tab until an editor applies them. Other changes are refused.'}
        </span>
      </fieldset>
      <fieldset className="field">
        <legend>Projects</legend>
        <div className="segmented" role="group" aria-label="Projects">
          <button type="button" aria-pressed={!scoped} onClick={() => setScoped(false)}>All I can reach</button>
          <button type="button" aria-pressed={scoped} onClick={() => setScoped(true)}>Only some</button>
        </div>
        {scoped && (
          <div className="token-projects">
            {projects.map((p) => (
              <label key={p.id} className="check">
                <input type="checkbox" checked={projectIds.includes(p.id)} onChange={() => toggle(p.id)} /> {p.name}
              </label>
            ))}
          </div>
        )}
      </fieldset>
      <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn primary" disabled={busy || !name.trim() || (scoped && projectIds.length === 0)}>
          {busy ? 'Creating…' : 'Create token'}
        </button>
      </div>
    </form>
  );
}

export function AccessTokens() {
  const toast = useToast();
  const [tokens, setTokens] = useState<AccessToken[] | null>(null);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<(AccessToken & { secret: string }) | null>(null);
  const [revoking, setRevoking] = useState<AccessToken | null>(null);

  const load = () => api.accessTokens().then(setTokens).catch(toast.fail);
  useEffect(() => {
    load();
  }, []);

  const revoke = async (token: AccessToken) => {
    try {
      await api.revokeAccessToken(token.id);
      toast.notify(`${token.name} revoked`);
      setRevoking(null);
      load();
    } catch (e) {
      toast.fail(e);
    }
  };

  if (created) return <Created token={created} onDone={() => { setCreated(null); load(); }} />;
  if (creating) return <NewTokenForm onCreated={(t) => { setCreating(false); setCreated(t); }} onCancel={() => setCreating(false)} />;

  const now = Date.now();
  return (
    <div className="stack">
      <p className="muted small">
        Tokens let AI agents (over MCP), scripts and CI use MockHub as you, with no more access than you have.
      </p>
      {tokens && tokens.length === 0 && (
        <div className="token-empty">
          <Key size={22} aria-hidden="true" />
          <span>No tokens yet.</span>
        </div>
      )}
      {tokens && tokens.length > 0 && (
        <ul className="token-list" aria-label="Access tokens">
          {tokens.map((t) => (
            <li key={t.id} className={t.expired ? 'expired' : undefined}>
              <div className="token-main">
                <strong>{t.name}</strong>
                <span className="mono muted small">{t.prefix}…</span>
              </div>
              <div className="token-meta small">
                <span className="tag">{t.role}</span>
                <span className="tag">{t.mode === 'review' ? 'stages for review' : 'applies directly'}</span>
                <span className="muted">{t.projects.length === 0 ? 'All projects' : t.projects.map((p) => p.name).join(', ')}</span>
              </div>
              <div className="token-times small muted">
                <span>{lastUsedLabel(t.lastUsedAt, now)}</span>
                <span>{t.expired ? 'Expired' : `Expires ${expiryLabel(t.expiresAt, now).toLowerCase()}`}</span>
              </div>
              <button type="button" className="btn ghost icon" aria-label={`Revoke ${t.name}`} title="Revoke" onClick={() => setRevoking(t)}>
                <Trash size={15} />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div>
        <button type="button" className="btn" onClick={() => setCreating(true)}>
          <Plus size={15} /> New token
        </button>
      </div>
      {revoking && (
        <Confirm
          title={`Revoke ${revoking.name}?`}
          message="Anything using this token stops working at once. This can't be undone."
          confirmLabel="Revoke"
          onConfirm={() => revoke(revoking)}
          onCancel={() => setRevoking(null)}
        />
      )}
    </div>
  );
}
