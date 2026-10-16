import { CaretRight, Plus, Trash, UserPlus, UsersThree } from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Member, Organization, OrgRole, Team, UserSummary } from '../api/types';
import { PersonCell, TeamMark } from '../components/Avatar';
import { Modal } from '../components/Modal';
import { RoleBadge } from '../components/RoleBadge';
import { Skeleton } from '../components/Skeleton';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';
import { SourceTag } from './UsersPage';

export const ORG_ROLES: { value: OrgRole; label: string; hint: string }[] = [
  { value: 'viewer', label: 'Viewer', hint: 'see projects, stubs and requests in every team' },
  { value: 'editor', label: 'Editor', hint: 'also create and change projects and stubs in every team' },
  { value: 'owner', label: 'Owner', hint: 'also manage teams, members and organisation settings' },
];

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;

export function OrgPage() {
  const { currentOrg, isOwner, isAdmin, session, orgs, refreshSession, refreshProjects } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [org, setOrg] = useState<Organization | null>(null);
  const [members, setMembers] = useState<Member[] | null>(null);
  const [teams, setTeams] = useState<Team[] | null>(null);
  const [creatingTeam, setCreatingTeam] = useState(false);
  const [directory, setDirectory] = useState<UserSummary[]>([]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [newMember, setNewMember] = useState('');
  const [newRole, setNewRole] = useState<OrgRole>('editor');
  const [deleting, setDeleting] = useState(false);

  const orgId = currentOrg?.id;
  const load = useCallback(async () => {
    if (!orgId) return;
    try {
      const [o, m, t] = await Promise.all([
        api.orgs().then((list) => list.find((x) => x.id === orgId) ?? null),
        api.members(orgId),
        api.teams(orgId),
      ]);
      setOrg(o);
      setMembers(m);
      setTeams(t);
      setName(o?.name ?? '');
      setDescription(o?.description ?? '');
    } catch (e) {
      toast.fail(e);
    }
  }, [orgId, toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (isOwner) api.users().then(setDirectory).catch(() => setDirectory([]));
  }, [isOwner]);

  const candidates = useMemo(
    () => directory.filter((u) => u.source !== 'env' && !members?.some((m) => m.id === u.id && m.role)),
    [directory, members],
  );

  if (!currentOrg) {
    return (
      <div className="page">
        <div className="panel empty">
          <h2>No organisation yet</h2>
          <p>Ask an admin or an organisation owner to add you to one.</p>
        </div>
      </div>
    );
  }

  const save = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await api.updateOrg(currentOrg.id, { name, description });
      await refreshSession();
      toast.notify('Organisation saved');
      load();
    } catch (err) {
      toast.fail(err);
    }
  };

  const setRole = async (userId: string, role: OrgRole) => {
    try {
      await api.setMember(currentOrg.id, userId, role);
      toast.notify('Role updated');
      if (userId === session?.id) await refreshSession();
      load();
    } catch (err) {
      toast.fail(err);
    }
  };

  const remove = async (member: Member) => {
    try {
      await api.removeMember(currentOrg.id, member.id);
      toast.notify(`${member.displayName || member.username} removed`);
      load();
    } catch (err) {
      toast.fail(err);
    }
  };

  const add = async (e: FormEvent) => {
    e.preventDefault();
    if (!newMember) return;
    await setRole(newMember, newRole);
    setNewMember('');
  };

  const removeOrg = async () => {
    try {
      await api.deleteOrg(currentOrg.id);
      setDeleting(false);
      toast.notify(`Organisation ${currentOrg.name} deleted`);
      await Promise.all([refreshSession(), refreshProjects()]);
      navigate('/');
    } catch (err) {
      toast.fail(err);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{currentOrg.name}</h1>
          <p className="muted">
            {org ? `${plural(org.teamCount, 'team')}, ${plural(org.projectCount, 'project')} and ${plural(org.memberCount, 'person')}`.replace('persons', 'people') : ' '}
          </p>
        </div>
        <RoleBadge role={isAdmin ? 'admin' : currentOrg.role} />
      </div>

      <div className="split">
        <div className="stack">
          <div className="panel">
            <div className="panel-head">
              <h2><UsersThree size={16} weight="duotone" /> Teams</h2>
              {isOwner && (
                <button className="btn small" onClick={() => setCreatingTeam(true)}><Plus size={13} /> New team</button>
              )}
            </div>
            {teams === null ? (
              <div className="panel-body"><Skeleton rows={2} label="Loading teams" /></div>
            ) : (
              <ul className="team-list">
                {teams.map((t) => (
                  <li key={t.id}>
                    <Link to={`/org/teams/${t.id}`} className="team-row">
                      <TeamMark name={t.name} seed={t.id} size={34} />
                      <span className="team-row-text">
                        <strong>{t.name}</strong>
                        <span className="muted small">{t.description || 'No description'}</span>
                      </span>
                      <span className="muted small team-row-counts">{plural(t.projectCount, 'project')}, {t.memberCount} {t.memberCount === 1 ? 'person' : 'people'}</span>
                      <RoleBadge role={t.role} />
                      <CaretRight size={14} className="muted" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="panel">
            <div className="panel-head">
              <h2>Organisation-wide access</h2>
            </div>
            <p className="panel-body muted small" style={{ paddingBottom: 0 }}>
              These roles apply to every team in {currentOrg.name}. To give someone access to one team only, add them on that team's page.
            </p>
            {members === null ? (
              <div className="panel-body"><Skeleton rows={3} label="Loading members" /></div>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Person</th>
                    <th>Role</th>
                    {isOwner && <th><span className="sr-only">Actions</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {members.length === 0 && (
                    <tr><td colSpan={3} className="muted">No members yet. Admins can still see this organisation.</td></tr>
                  )}
                  {members.map((m) => (
                    <tr key={m.id}>
                      <td>
                        <PersonCell name={m.displayName || m.username} detail={m.email || m.username} seed={m.id} />
                        <SourceTag source={m.source} />
                        {m.disabled && <span className="tag">disabled</span>}
                        {m.teams.length > 0 && (
                          <div className="chip-row">
                            {m.teams.map((t) => <span key={t.id} className="tag">{t.name}{t.role ? `: ${t.role}` : ''}</span>)}
                          </div>
                        )}
                      </td>
                      <td>
                        {isOwner && m.role ? (
                          <select aria-label={`Role of ${m.username}`} value={m.role} onChange={(e) => setRole(m.id, e.target.value as OrgRole)}>
                            {ORG_ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                          </select>
                        ) : m.role || m.groupRole ? (
                          <RoleBadge role={m.effectiveRole} />
                        ) : (
                          <span className="muted small">Team access only</span>
                        )}
                        {m.groupRole && <div className="muted small">{m.role ? 'also ' : ''}{m.groupRole} from an Entra group</div>}
                      </td>
                      {isOwner && (
                        <td className="actions">
                          {m.role && (
                            <button className="btn ghost icon small" aria-label={`Remove ${m.username}`} title="Remove from organisation" onClick={() => remove(m)}>
                              <Trash size={14} />
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {isOwner && (
              <form className="panel-body add-member" onSubmit={add}>
                <label className="field">
                  <span>Add a person</span>
                  <select value={newMember} onChange={(e) => setNewMember(e.target.value)}>
                    <option value="">Choose a user…</option>
                    {candidates.map((u) => (
                      <option key={u.id} value={u.id}>{u.displayName || u.username} ({u.email || u.username})</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>Role</span>
                  <select value={newRole} onChange={(e) => setNewRole(e.target.value as OrgRole)}>
                    {ORG_ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                  </select>
                </label>
                <button className="btn primary" type="submit" disabled={!newMember}><UserPlus size={14} /> Add</button>
                <p className="muted small" style={{ gridColumn: '1 / -1' }}>
                  {ORG_ROLES.find((r) => r.value === newRole)?.label}s {ORG_ROLES.find((r) => r.value === newRole)?.hint}.
                  {isAdmin ? ' Create new people under Users.' : ' Missing someone? Ask an admin to create their account, or have them sign in with Microsoft once.'}
                </p>
              </form>
            )}
          </div>
        </div>

        <div className="stack">
          {isOwner && (
            <form className="panel" onSubmit={save}>
              <div className="panel-head"><h2>Settings</h2></div>
              <div className="panel-body stack">
                <label className="field">
                  <span>Name</span>
                  <input type="text" value={name} onChange={(e) => setName(e.target.value)} required />
                </label>
                <label className="field">
                  <span>Description</span>
                  <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} />
                </label>
                <div><button className="btn" type="submit">Save</button></div>
              </div>
            </form>
          )}
          <div className="panel">
            <div className="panel-head"><h2>Who can see this organisation</h2></div>
            <div className="panel-body stack small">
              <p>People with organisation-wide access, members of its teams, admins, and people with a server-wide role set under Users. Team members only see their own teams' projects.</p>
              <p className="muted">Mock URLs are public either way. Roles only control who can change the mocks.</p>
            </div>
          </div>
          {isOwner && (
            <div className="panel danger-zone">
              <div className="panel-head"><h2>Delete organisation</h2></div>
              <div className="panel-body stack">
                <p className="small">Deletes {currentOrg.name}, its {org?.projectCount ?? 0} projects and all their stubs. This can't be undone.</p>
                <div>
                  <button className="btn danger" onClick={() => setDeleting(true)} disabled={orgs.length < 2 && !isAdmin}>
                    <Trash size={14} /> Delete organisation
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
      {deleting && <DeleteOrgDialog name={currentOrg.name} onConfirm={removeOrg} onCancel={() => setDeleting(false)} />}
      {creatingTeam && (
        <NewTeamDialog
          orgId={currentOrg.id}
          onClose={() => setCreatingTeam(false)}
          onCreated={async (team) => {
            setCreatingTeam(false);
            await refreshSession();
            navigate(`/org/teams/${team.id}`);
          }}
        />
      )}
    </div>
  );
}

function DeleteOrgDialog({ name, onConfirm, onCancel }: { name: string; onConfirm: () => void; onCancel: () => void }) {
  const [typed, setTyped] = useState('');
  return (
    <Modal
      title={`Delete ${name}?`}
      description="Every project in it and every stub in those projects is deleted."
      onClose={onCancel}
      footer={
        <>
          <button className="btn" onClick={onCancel}>Cancel</button>
          <button className="btn danger" disabled={typed !== name} onClick={onConfirm}>Delete organisation</button>
        </>
      }
    >
      <label className="field">
        <span>Type <strong>{name}</strong> to confirm</span>
        <input type="text" value={typed} onChange={(e) => setTyped(e.target.value)} />
      </label>
    </Modal>
  );
}

function NewTeamDialog({ orgId, onClose, onCreated }: { orgId: string; onClose: () => void; onCreated: (team: Team) => void }) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const team = await api.createTeam(orgId, { name, description });
      toast.notify(`Team ${team.name} created`);
      onCreated(team);
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="New team" description="A team owns projects. Its members get the role you give them in this team only." onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <label className="field">
          <span>Name</span>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Checkout" required autoFocus />
        </label>
        <label className="field">
          <span>Description <span className="hint">optional</span></span>
          <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Cart, payment and order pages" />
        </label>
        <div className="modal-foot" style={{ padding: 0 }}>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy || !name.trim()}>Create team</button>
        </div>
      </form>
    </Modal>
  );
}
