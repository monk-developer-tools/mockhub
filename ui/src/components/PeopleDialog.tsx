import { Buildings, Check, Crown, Eye, MagnifyingGlass, PencilSimple, UserPlus, UsersThree } from '@phosphor-icons/react';
import { useMemo, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Member, OrgRole, Team, UserSummary } from '../api/types';
import {
  NO_CURRENT_ACCESS,
  ROLE_INFO,
  currentAccessOf,
  planChanges,
  sentence,
  wantedFrom,
  type AccessChange,
  type Scope,
  type WantedAccess,
} from '../model/access';
import { PersonCell, TeamMark } from './Avatar';
import { Modal } from './Modal';
import { useToast } from './Toasts';

const ROLE_ICONS: Record<OrgRole, typeof Eye> = { viewer: Eye, editor: PencilSimple, manager: Crown };

interface Props {
  orgId: string;
  orgName: string;
  teams: Team[];
  directory: UserSummary[];
  canPickOrg: boolean;
  canCreateUsers: boolean;
  member?: Member;
  lockTeam?: Team;
  excludeIds?: string[];
  onClose: () => void;
  onDone: () => void;
}

export function PeopleDialog({ orgId, orgName, teams, directory, canPickOrg, canCreateUsers, member, lockTeam, excludeIds = [], onClose, onDone }: Props) {
  const toast = useToast();
  const current = useMemo(() => (member ? currentAccessOf(member) : NO_CURRENT_ACCESS), [member]);
  const [picked, setPicked] = useState<UserSummary[]>(member ? [member] : []);
  const [query, setQuery] = useState('');
  const [wanted, setWanted] = useState<WantedAccess>(() => {
    const start = wantedFrom(current, lockTeam ? [lockTeam.id] : []);
    return canPickOrg || start.scope === 'teams' ? start : { ...start, scope: 'teams' };
  });
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);

  const editing = Boolean(member);
  const teamNames = Object.fromEntries(teams.map((t) => [t.id, t.name]));
  const candidates = useMemo(() => {
    const q = query.trim().toLowerCase();
    return directory
      .filter((u) => u.source !== 'env' && !excludeIds.includes(u.id))
      .filter((u) => !q || `${u.displayName} ${u.username} ${u.email}`.toLowerCase().includes(q))
      .slice(0, 40);
  }, [directory, excludeIds, query]);
  const isPicked = (id: string) => picked.some((p) => p.id === id);
  const toggle = (u: UserSummary) => setPicked(isPicked(u.id) ? picked.filter((p) => p.id !== u.id) : [...picked, u]);
  const setScope = (scope: Scope) => setWanted({ ...wanted, scope });
  const toggleTeam = (id: string) =>
    setWanted({ ...wanted, teamIds: wanted.teamIds.includes(id) ? wanted.teamIds.filter((t) => t !== id) : [...wanted.teamIds, id] });

  const names = picked.map((p) => p.displayName || p.username);
  const ready = picked.length > 0 && (wanted.scope === 'org' || wanted.teamIds.length > 0);

  const apply = async (userId: string, change: AccessChange) => {
    if (change.kind === 'setOrg') return api.setMember(orgId, userId, change.role);
    if (change.kind === 'removeOrg') return api.removeMember(orgId, userId);
    if (change.kind === 'setTeam') return api.setTeamMember(orgId, change.teamId, userId, change.role);
    return api.removeTeamMember(orgId, change.teamId, userId);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!ready) return;
    setBusy(true);
    try {
      for (const person of picked) {
        const base = editing ? current : NO_CURRENT_ACCESS;
        const target = lockTeam && !editing ? { ...wanted, scope: 'teams' as Scope, teamIds: [lockTeam.id] } : wanted;
        for (const change of planChanges(base, target)) await apply(person.id, change);
      }
      toast.notify(editing ? `Access for ${names[0]} saved` : `${names.length === 1 ? names[0] : `${names.length} people`} added`);
      onDone();
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  const title = editing ? `Access for ${names[0]}` : lockTeam ? `Add people to ${lockTeam.name}` : `Add people to ${orgName}`;
  const step = (n: number) => <span className="step-number" aria-hidden="true">{n}</span>;
  let n = 0;

  return (
    <Modal title={title} description={editing ? 'Choose where they can work and what they can do.' : 'Three quick choices: who, where and what they can do.'} onClose={onClose} wide>
      <form className="people-dialog" onSubmit={submit}>
        {!editing && (
          <section className="people-step">
            <h3>{step(++n)} Who</h3>
            {creating ? (
              <NewPerson
                onCancel={() => setCreating(false)}
                onCreated={(u) => {
                  setPicked([...picked, u]);
                  setCreating(false);
                }}
              />
            ) : (
              <>
                <div className="search-field">
                  <MagnifyingGlass size={14} />
                  <input type="search" aria-label="Search people" placeholder="Search by name or e-mail" value={query} onChange={(e) => setQuery(e.target.value)} />
                </div>
                <ul className="pick-list" aria-label="People">
                  {candidates.map((u) => (
                    <li key={u.id}>
                      <label className={`pick-row${isPicked(u.id) ? ' on' : ''}`}>
                        <input type="checkbox" checked={isPicked(u.id)} onChange={() => toggle(u)} aria-label={u.displayName || u.username} />
                        <PersonCell name={u.displayName || u.username} detail={u.email || u.username} seed={u.id} />
                        {isPicked(u.id) && <Check size={14} weight="bold" className="pick-check" />}
                      </label>
                    </li>
                  ))}
                  {candidates.length === 0 && <li className="muted small pick-empty">{query ? 'Nobody matches.' : 'Everyone already has access.'}</li>}
                </ul>
                <p className="muted small">
                  {canCreateUsers ? (
                    <>Not listed? <button type="button" className="link-button" onClick={() => setCreating(true)}><UserPlus size={13} /> Create an account</button>{' '}or ask them to sign in with Microsoft once.</>
                  ) : (
                    'Not listed? Ask an admin to create their account, or ask them to sign in with Microsoft once.'
                  )}
                </p>
              </>
            )}
          </section>
        )}

        {!lockTeam && (
          <section className="people-step">
            <h3>{step(++n)} Where</h3>
            <div className="choice-cards two">
              {canPickOrg && (
                <label className={`choice-card${wanted.scope === 'org' ? ' on' : ''}`}>
                  <input type="radio" name="scope" checked={wanted.scope === 'org'} onChange={() => setScope('org')} />
                  <Buildings size={22} weight="duotone" />
                  <strong>Whole organisation</strong>
                  <span>Every team in {orgName}, including new ones.</span>
                </label>
              )}
              <label className={`choice-card${wanted.scope === 'teams' ? ' on' : ''}`}>
                <input type="radio" name="scope" checked={wanted.scope === 'teams'} onChange={() => setScope('teams')} />
                <UsersThree size={22} weight="duotone" />
                <strong>Only some teams</strong>
                <span>They see only the projects of the teams you pick.</span>
              </label>
            </div>
            {wanted.scope === 'teams' && (
              <ul className="team-picks" aria-label="Teams">
                {teams.map((t) => (
                  <li key={t.id}>
                    <label className={`pick-row${wanted.teamIds.includes(t.id) ? ' on' : ''}`}>
                      <input type="checkbox" checked={wanted.teamIds.includes(t.id)} onChange={() => toggleTeam(t.id)} aria-label={t.name} />
                      <TeamMark name={t.name} seed={t.id} size={24} />
                      <span className="pick-name">{t.name}</span>
                      <span className="muted small">{t.projectCount} {t.projectCount === 1 ? 'project' : 'projects'}</span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}

        <section className="people-step">
          <h3>{step(++n)} What they can do</h3>
          <div className="choice-cards three" role="radiogroup" aria-label="Role">
            {ROLE_INFO.map((r) => {
              const Icon = ROLE_ICONS[r.value];
              return (
                <label key={r.value} className={`choice-card${wanted.role === r.value ? ' on' : ''}`}>
                  <input type="radio" name="role" value={r.value} checked={wanted.role === r.value} onChange={() => setWanted({ ...wanted, role: r.value })} aria-label={r.label} />
                  <Icon size={22} weight="duotone" />
                  <strong>{r.label}</strong>
                  <span>{r.detail}</span>
                </label>
              );
            })}
          </div>
        </section>

        <div className="people-summary" aria-live="polite">
          {picked.length > 0 ? sentence(names, lockTeam ? { ...wanted, scope: 'teams', teamIds: [lockTeam.id] } : wanted, orgName, teamNames) : 'Pick at least one person.'}
        </div>

        <div className="modal-foot" style={{ padding: 0 }}>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={!ready || busy}>
            {editing ? 'Save access' : `Add ${picked.length > 1 ? `${picked.length} people` : 'person'}`}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function NewPerson({ onCancel, onCreated }: { onCancel: () => void; onCreated: (u: UserSummary) => void }) {
  const toast = useToast();
  const [displayName, setDisplayName] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const create = async () => {
    setBusy(true);
    try {
      const u = await api.createUser({ username, displayName, email, password, serverRole: 'none' });
      toast.notify(`Account for ${displayName || username} created. Share the temporary password with them.`);
      onCreated(u);
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="new-person">
      <div className="grid two">
        <label className="field">
          <span>Name</span>
          <input type="text" value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="Sam Taylor" autoFocus />
        </label>
        <label className="field">
          <span>E-mail <span className="hint">optional</span></span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="sam@example.com" />
        </label>
        <label className="field">
          <span>Username</span>
          <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="sam" autoComplete="off" />
        </label>
        <label className="field">
          <span>Temporary password <span className="hint">at least 8 characters</span></span>
          <input type="text" className="mono" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" />
        </label>
      </div>
      <div className="btn-row">
        <button type="button" className="btn" onClick={onCancel}>Back to the list</button>
        <button type="button" className="btn primary" disabled={busy || !username.trim() || password.length < 8} onClick={create}>Create account</button>
      </div>
    </div>
  );
}
