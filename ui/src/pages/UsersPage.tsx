import { MagnifyingGlass, Plus, ShieldCheck, Trash } from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { ServerRole, UserRecord, UserSource } from '../api/types';
import { AccessEditor, NO_ACCESS, summarise, type Access, type OrgWithTeams } from '../components/AccessEditor';
import { PersonCell } from '../components/Avatar';
import { Modal } from '../components/Modal';
import { RoleBadge } from '../components/RoleBadge';
import { Skeleton } from '../components/Skeleton';
import { useToast } from '../components/Toasts';
import { MicrosoftMark } from './LoginPage';
import { useApp } from '../state/AppContext';
import { timeAgo } from '../model/time';

const SERVER_ROLES: { value: ServerRole; label: string; hint: string }[] = [
  { value: 'none', label: 'None', hint: 'only sees organisations they are a member of' },
  { value: 'viewer', label: 'Viewer everywhere', hint: 'can look at every organisation' },
  { value: 'editor', label: 'Editor everywhere', hint: 'can change projects in every organisation' },
  { value: 'admin', label: 'Admin', hint: 'manages users, single sign-on, organisations and the server' },
];

export function SourceTag({ source }: { source: UserSource }) {
  if (source === 'azure') return <span className="tag source-tag"><MicrosoftMark /> Entra ID</span>;
  if (source === 'env') return <span className="tag source-tag">MOCKHUB_USERS</span>;
  return null;
}

export function UsersPage() {
  const { info, acceptToken, session, refreshSession } = useApp();
  const toast = useToast();
  const [users, setUsers] = useState<UserRecord[] | null>(null);
  const [tree, setTree] = useState<OrgWithTeams[]>([]);
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<UserRecord | null>(null);

  const load = useCallback(async () => {
    try {
      const [u, o] = await Promise.all([api.users(), api.orgs()]);
      const teams = await Promise.all(o.map((org) => api.teams(org.id)));
      setUsers(u);
      setTree(o.map((org, i) => ({ org, teams: teams[i] })));
    } catch (e) {
      toast.fail(e);
    }
  }, [toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (users ?? []).filter((u) => !q || `${u.username} ${u.displayName} ${u.email}`.toLowerCase().includes(q));
  }, [users, query]);

  const afterChange = async (user?: UserRecord) => {
    await load();
    if (user?.id === session?.id) await refreshSession();
  };

  if (info && !info.authEnabled) {
    return (
      <div className="page">
        <div className="page-header">
          <div>
            <h1>Users</h1>
            <p className="muted">Sign-in is off, so anyone who can reach MockHub can change every mock.</p>
          </div>
        </div>
        <FirstAdmin onCreated={(token) => acceptToken(token)} />
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Users</h1>
          <p className="muted">People who can sign in to MockHub, and what they can reach: whole organisations, or single teams inside them.</p>
        </div>
        <button className="btn primary" onClick={() => setCreating(true)}><Plus size={15} /> New user</button>
      </div>
      <div className="panel">
        <div className="panel-head">
          <div className="search-field">
            <MagnifyingGlass size={14} />
            <input type="search" aria-label="Search users" placeholder="Search by name or e-mail" value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
          <span className="muted small">{users ? `${users.length} user${users.length === 1 ? '' : 's'}` : ''}</span>
        </div>
        {users === null ? (
          <div className="panel-body"><Skeleton rows={4} label="Loading users" /></div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Person</th>
                <th>Server role</th>
                <th>Access</th>
                <th>Last sign-in</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((u) => (
                <tr key={u.id} className="clickable" onClick={() => setEditing(u)}>
                  <td>
                    <button className="link-button person-button" onClick={(e) => { e.stopPropagation(); setEditing(u); }}>
                      <PersonCell name={u.displayName || u.username} detail={u.email || u.username} seed={u.id} />
                    </button>
                    <SourceTag source={u.source} />
                    {u.disabled && <span className="tag">disabled</span>}
                  </td>
                  <td><RoleBadge role={u.effectiveServerRole} /></td>
                  <td>
                    <span className="badge-list">
                      {[...u.memberships, ...u.groupMemberships.filter((g) => !u.memberships.some((m) => m.orgId === g.orgId))].map((m) => (
                        <span key={m.orgId}>{m.orgName ?? m.orgId} <RoleBadge role={m.role} /></span>
                      ))}
                      {[...u.teamMemberships, ...u.groupTeamMemberships.filter((g) => !u.teamMemberships.some((m) => m.teamId === g.teamId))].map((m) => (
                        <span key={m.teamId}>{m.orgName ?? ''} › {m.teamName ?? m.teamId} <RoleBadge role={m.role} /></span>
                      ))}
                      {u.memberships.length + u.groupMemberships.length + u.teamMemberships.length + u.groupTeamMemberships.length === 0 && <span className="muted">none</span>}
                    </span>
                  </td>
                  <td className="muted num small">{u.lastLoginAt ? timeAgo(u.lastLoginAt) : 'never'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {creating && <NewUserDialog tree={tree} onClose={() => setCreating(false)} onCreated={() => { setCreating(false); afterChange(); }} />}
      {editing && (
        <EditUserDialog
          user={editing}
          tree={tree}
          isSelf={editing.id === session?.id}
          onClose={() => setEditing(null)}
          onChanged={(u) => { setEditing(u); afterChange(u); }}
          onDeleted={() => { setEditing(null); afterChange(); }}
        />
      )}
    </div>
  );
}

function FirstAdmin({ onCreated }: { onCreated: (token: string) => void }) {
  const toast = useToast();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const created = await api.createUser({ username, password, serverRole: 'admin' });
      toast.notify('Sign-in is on. You are signed in as the first admin.');
      if (created.token) onCreated(created.token);
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="panel first-admin" onSubmit={submit}>
      <div className="panel-head"><h2><ShieldCheck size={16} /> Turn on sign-in</h2></div>
      <div className="panel-body stack">
        <p className="small">Create the first admin. From then on people sign in, and you decide what each of them can change.</p>
        <div className="grid two">
          <label className="field">
            <span>Username</span>
            <input type="text" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
          </label>
          <label className="field">
            <span>Password <span className="hint">at least 8 characters</span></span>
            <input type="password" autoComplete="new-password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
        </div>
        <div><button className="btn primary" type="submit" disabled={busy}>Create admin and turn on sign-in</button></div>
      </div>
    </form>
  );
}

function NewUserDialog({ tree, onClose, onCreated }: { tree: OrgWithTeams[]; onClose: () => void; onCreated: () => void }) {
  const toast = useToast();
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [serverRole, setServerRole] = useState<ServerRole>('none');
  const [access, setAccess] = useState<Access>(NO_ACCESS);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.createUser({ username, displayName, email, password, serverRole, ...access });
      toast.notify(`${displayName || username} can now sign in`);
      onCreated();
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="New user" description="For people who sign in with a MockHub password. People with Microsoft accounts appear here after their first sign-in." onClose={onClose} wide>
      <form className="stack" onSubmit={submit}>
        <div className="grid two">
          <label className="field">
            <span>Username</span>
            <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} required autoComplete="off" />
          </label>
          <label className="field">
            <span>Temporary password <span className="hint">at least 8 characters</span></span>
            <input type="text" className="mono" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="off" />
          </label>
          <label className="field">
            <span>Display name</span>
            <input type="text" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          </label>
          <label className="field">
            <span>E-mail</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
        </div>
        <div className="field">
          <span>Access <span className="hint">{summarise(access)}</span></span>
          <AccessEditor tree={tree} value={access} onChange={setAccess} />
          <span className="hint">An organisation-wide role applies to every team in it. A team role applies to that team only. The higher one wins.</span>
        </div>
        <ServerRoleField value={serverRole} onChange={setServerRole} />
        <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy}>Create user</button>
        </div>
      </form>
    </Modal>
  );
}

function ServerRoleField({ value, onChange, disabled }: { value: ServerRole; onChange: (r: ServerRole) => void; disabled?: boolean }) {
  return (
    <label className="field">
      <span>Server role</span>
      <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value as ServerRole)}>
        {SERVER_ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
      </select>
      <span className="hint">{SERVER_ROLES.find((r) => r.value === value)?.hint}</span>
    </label>
  );
}

interface EditProps {
  user: UserRecord;
  tree: OrgWithTeams[];
  isSelf: boolean;
  onClose: () => void;
  onChanged: (user: UserRecord) => void;
  onDeleted: () => void;
}

function accessOf(user: UserRecord): Access {
  return {
    memberships: user.memberships.map((m) => ({ orgId: m.orgId, role: m.role })),
    teamMemberships: user.teamMemberships.map((m) => ({ teamId: m.teamId, role: m.role })),
  };
}

function EditUserDialog({ user, tree, isSelf, onClose, onChanged, onDeleted }: EditProps) {
  const toast = useToast();
  const [displayName, setDisplayName] = useState(user.displayName);
  const [email, setEmail] = useState(user.email);
  const [serverRole, setServerRole] = useState<ServerRole>(user.serverRole);
  const [password, setPassword] = useState('');
  const [access, setAccess] = useState<Access>(() => accessOf(user));
  const [confirmDelete, setConfirmDelete] = useState(false);
  const accessChanged = JSON.stringify(access) !== JSON.stringify(accessOf(user));
  const readOnly = user.source === 'env';

  const run = async (action: () => Promise<UserRecord | void>, message: string) => {
    try {
      const result = await action();
      toast.notify(message);
      if (result) onChanged(result);
      return true;
    } catch (err) {
      toast.fail(err);
      return false;
    }
  };

  const save = (e: FormEvent) => {
    e.preventDefault();
    run(() => api.updateUser(user.id, { displayName, email, serverRole }), 'User saved');
  };

  return (
    <Modal title={user.displayName || user.username} description={user.email || user.username} onClose={onClose} wide>
      {readOnly ? (
        <div className="callout">This account comes from <code>MOCKHUB_USERS</code> on the server. Change it there and restart MockHub.</div>
      ) : (
        <div className="stack">
          <form className="stack" onSubmit={save}>
            <div className="grid two">
              <label className="field">
                <span>Display name</span>
                <input type="text" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
              </label>
              <label className="field">
                <span>E-mail</span>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} disabled={user.source === 'azure'} />
              </label>
            </div>
            <ServerRoleField value={serverRole} onChange={setServerRole} disabled={isSelf} />
            {user.groupServerRole !== 'none' && (
              <p className="muted small">Entra groups also give this person the {user.groupServerRole} server role.</p>
            )}
            <div><button className="btn" type="submit">Save</button></div>
          </form>

          <div className="section-block stack">
            <h3>Access <span className="muted small">{summarise(access)}</span></h3>
            <AccessEditor tree={tree} value={access} onChange={setAccess} />
            {(user.groupMemberships.length > 0 || user.groupTeamMemberships.length > 0) && (
              <p className="muted small">
                Entra groups also give:{' '}
                {[
                  ...user.groupMemberships.map((m) => `${m.role} in ${m.orgName ?? m.orgId}`),
                  ...user.groupTeamMemberships.map((m) => `${m.role} in ${m.orgName ?? ''} › ${m.teamName ?? m.teamId}`),
                ].join(', ')}
                .
              </p>
            )}
            <div className="btn-row">
              <button className="btn" disabled={!accessChanged} onClick={() => run(() => api.updateUser(user.id, { ...access }), 'Access saved')}>Save access</button>
              {accessChanged && <button className="btn ghost" onClick={() => setAccess(accessOf(user))}>Undo changes</button>}
            </div>
          </div>

          {user.source === 'local' && (
            <form className="section-block btn-row" onSubmit={(e) => { e.preventDefault(); run(() => api.resetPassword(user.id, password), 'Password reset. Their other sessions are signed out.').then((ok) => ok && setPassword('')); }}>
              <label className="field" style={{ flex: 1 }}>
                <span>New password</span>
                <input type="text" className="mono" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="off" />
              </label>
              <button className="btn" type="submit" style={{ alignSelf: 'flex-end' }}>Reset password</button>
            </form>
          )}

          <div className="section-block btn-row" style={{ justifyContent: 'space-between' }}>
            <button className="btn" disabled={isSelf} onClick={() => run(() => api.updateUser(user.id, { disabled: !user.disabled }), user.disabled ? 'User enabled' : 'User disabled and signed out')}>
              {user.disabled ? 'Enable sign-in' : 'Disable sign-in'}
            </button>
            {confirmDelete ? (
              <span className="btn-row">
                <span className="small">Delete {user.username}?</span>
                <button className="btn" onClick={() => setConfirmDelete(false)}>Keep</button>
                <button className="btn danger" onClick={() => run(() => api.deleteUser(user.id), 'User deleted').then((ok) => ok && onDeleted())}>Delete</button>
              </span>
            ) : (
              <button className="btn danger" disabled={isSelf} onClick={() => setConfirmDelete(true)}><Trash size={14} /> Delete user</button>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}
