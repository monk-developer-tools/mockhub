import { ArrowRight, Buildings, CaretRight, Folder, GearSix, MagnifyingGlass, Plus, Trash, UserPlus, UsersThree } from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Member, Organization, OrgRole, Team, UserSummary } from '../api/types';
import { PersonCell, TeamMark } from '../components/Avatar';
import { Confirm, Modal } from '../components/Modal';
import { PeopleDialog } from '../components/PeopleDialog';
import { RoleBadge } from '../components/RoleBadge';
import { Skeleton } from '../components/Skeleton';
import { useToast } from '../components/Toasts';
import { ROLE_INFO, accessChips, currentAccessOf, planChanges } from '../model/access';
import { useApp } from '../state/AppContext';
import { ProjectDialog } from './ProjectDialog';
import { SourceTag } from './UsersPage';

export const ORG_ROLES: { value: OrgRole; label: string; hint: string }[] = ROLE_INFO.map((r) => ({ value: r.value, label: r.label, hint: r.detail }));

const plural = (n: number, word: string, many = `${word}s`) => `${n} ${n === 1 ? word : many}`;

type Tab = 'teams' | 'people' | 'settings';

export function OrgPage() {
  const { currentOrg, isOwner, isAdmin, orgs, canEdit, refreshSession, refreshProjects } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [org, setOrg] = useState<Organization | null>(null);
  const [members, setMembers] = useState<Member[] | null>(null);
  const [teams, setTeams] = useState<Team[] | null>(null);
  const [directory, setDirectory] = useState<UserSummary[]>([]);
  const [creatingTeam, setCreatingTeam] = useState(false);
  const [creatingProject, setCreatingProject] = useState(false);
  const [adding, setAdding] = useState(false);
  const [editingMember, setEditingMember] = useState<Member | null>(null);
  const [removingMember, setRemovingMember] = useState<Member | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [query, setQuery] = useState('');

  const requested = params.get('tab') as Tab | null;
  const tab: Tab = requested === 'people' || (requested === 'settings' && isOwner) ? requested : 'teams';
  const show = (next: Tab) => setParams(next === 'teams' ? {} : { tab: next }, { replace: true });

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

  const people = useMemo(() => (members ?? []).filter((m) => m.role || m.groupRole || m.teams.some((t) => t.role)), [members]);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return people.filter((m) => !q || `${m.displayName} ${m.username} ${m.email}`.toLowerCase().includes(q));
  }, [people, query]);

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

  const afterAccessChange = async () => {
    setAdding(false);
    setEditingMember(null);
    await Promise.all([load(), refreshSession()]);
  };

  const removeEverywhere = async (member: Member) => {
    try {
      for (const change of planChanges(currentAccessOf(member), { scope: 'teams', teamIds: [], role: 'viewer' })) {
        if (change.kind === 'removeOrg') await api.removeMember(currentOrg.id, member.id);
        if (change.kind === 'removeTeam') await api.removeTeamMember(currentOrg.id, change.teamId, member.id);
      }
      toast.notify(`${member.displayName || member.username} no longer has access to ${currentOrg.name}`);
      setRemovingMember(null);
      await afterAccessChange();
    } catch (err) {
      toast.fail(err);
    }
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

  const teamCount = teams?.length ?? org?.teamCount ?? 0;
  const projectCount = org?.projectCount ?? 0;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{currentOrg.name}</h1>
          <p className="muted">{org?.description || 'Your organisation, its teams and the people who work in them.'}</p>
        </div>
        <div className="btn-row">
          <RoleBadge role={isAdmin ? 'admin' : currentOrg.role} />
          {isOwner && <button className="btn primary" onClick={() => setAdding(true)}><UserPlus size={15} /> Add people</button>}
        </div>
      </div>

      <section className="access-map" aria-label="How it fits together">
        <div className="access-map-row">
          <div className="map-node">
            <span className="map-icon org"><Buildings size={20} weight="duotone" /></span>
            <span className="map-kicker">Organisation</span>
            <strong>{currentOrg.name}</strong>
            <span className="map-note">Your company or department. Holds every team.</span>
          </div>
          <ArrowRight size={18} className="map-arrow" aria-hidden="true" />
          <button type="button" className="map-node" onClick={() => show('teams')}>
            <span className="map-icon team"><UsersThree size={20} weight="duotone" /></span>
            <span className="map-kicker">Teams</span>
            <strong>{plural(teamCount, 'team')}</strong>
            <span className="map-note">Groups of people who work together. Each team owns its projects.</span>
          </button>
          <ArrowRight size={18} className="map-arrow" aria-hidden="true" />
          <div className="map-node">
            <span className="map-icon project"><Folder size={20} weight="duotone" /></span>
            <span className="map-kicker">Projects</span>
            <strong>{plural(projectCount, 'project')}</strong>
            <span className="map-note">One mocked service each, with its own URL and stubs.</span>
          </div>
        </div>
        <button type="button" className="map-people" onClick={() => show('people')}>
          <UserPlus size={16} weight="duotone" />
          <span>
            <strong>{plural(people.length, 'person', 'people')}</strong> can sign in here. Give someone the <em>whole organisation</em>, or only the <em>teams</em> they work in.
          </span>
          <CaretRight size={14} />
        </button>
      </section>

      <div className="panel">
        <div className="tabs-line" role="tablist" aria-label="Organisation sections">
          <button role="tab" aria-selected={tab === 'teams'} onClick={() => show('teams')}>
            <UsersThree size={15} /> Teams <span className="count">{teamCount}</span>
          </button>
          <button role="tab" aria-selected={tab === 'people'} onClick={() => show('people')}>
            <UserPlus size={15} /> People <span className="count">{people.length}</span>
          </button>
          {isOwner && (
            <button role="tab" aria-selected={tab === 'settings'} onClick={() => show('settings')}>
              <GearSix size={15} /> Settings
            </button>
          )}
        </div>

        {tab === 'teams' && (
          <div className="panel-body">
            {teams === null ? (
              <Skeleton rows={2} label="Loading teams" />
            ) : (
              <div className="team-grid">
                {teams.map((t) => (
                  <Link key={t.id} to={`/org/teams/${t.id}`} className="team-card">
                    <span className="team-card-head">
                      <TeamMark name={t.name} seed={t.id} size={36} />
                      {!isAdmin && <RoleBadge role={t.role} />}
                    </span>
                    <strong>{t.name}</strong>
                    <span className="muted small team-card-desc">{t.description || 'No description yet'}</span>
                    <span className="team-card-stats">
                      <span><Folder size={13} /> {plural(t.projectCount, 'project')}</span>
                      <span><UsersThree size={13} /> {plural(t.memberCount, 'person', 'people')}</span>
                    </span>
                  </Link>
                ))}
                {isOwner && (
                  <button type="button" className="team-card add" onClick={() => setCreatingTeam(true)}>
                    <Plus size={20} />
                    <strong>New team</strong>
                    <span className="muted small">For example Checkout, Mobile app or QA</span>
                  </button>
                )}
              </div>
            )}
            {canEdit && teams !== null && teams.length > 0 && (
              <p className="muted small team-grid-foot">
                Projects live inside a team.{' '}
                <button type="button" className="link-button" onClick={() => setCreatingProject(true)}>Create a project</button>{' '}and pick its team.
              </p>
            )}
          </div>
        )}

        {tab === 'people' && (
          <>
            <div className="panel-body people-toolbar">
              <div className="search-field">
                <MagnifyingGlass size={14} />
                <input type="search" aria-label="Search people" placeholder="Search by name or e-mail" value={query} onChange={(e) => setQuery(e.target.value)} />
              </div>
              {isOwner && <button className="btn" onClick={() => setAdding(true)}><UserPlus size={14} /> Add people</button>}
            </div>
            {members === null ? (
              <div className="panel-body"><Skeleton rows={3} label="Loading people" /></div>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Person</th>
                    <th>Where and what they can do</th>
                    {isOwner && <th><span className="sr-only">Actions</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {filtered.length === 0 && (
                    <tr><td colSpan={3} className="muted">{query ? 'Nobody matches.' : 'Nobody has been added yet. Admins can always see this organisation.'}</td></tr>
                  )}
                  {filtered.map((m) => (
                    <tr key={m.id}>
                      <td>
                        <PersonCell name={m.displayName || m.username} detail={m.email || m.username} seed={m.id} />
                        <SourceTag source={m.source} />
                        {m.disabled && <span className="tag">disabled</span>}
                      </td>
                      <td>
                        <span className="access-chips">
                          {accessChips(m).map((c) => (
                            <span key={c.label} className={`access-chip${c.wide ? ' wide' : ''}`} title={c.fromGroup ? 'From an Entra group' : undefined}>
                              {c.wide ? <Buildings size={13} weight="duotone" /> : <UsersThree size={13} weight="duotone" />}
                              {c.label}
                              <RoleBadge role={c.role} />
                            </span>
                          ))}
                        </span>
                      </td>
                      {isOwner && (
                        <td className="actions">
                          <span className="btn-row">
                            <button className="btn small" onClick={() => setEditingMember(m)} aria-label={`Change access for ${m.username}`}>Change access</button>
                            {(m.role || m.teams.some((t) => t.role)) && (
                              <button className="btn ghost icon small" aria-label={`Remove ${m.username}`} title="Remove from organisation" onClick={() => setRemovingMember(m)}>
                                <Trash size={14} />
                              </button>
                            )}
                          </span>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <div className="panel-body role-legend">
              {ROLE_INFO.map((r) => (
                <span key={r.value}><RoleBadge role={r.value} /> {r.detail}</span>
              ))}
            </div>
          </>
        )}

        {tab === 'settings' && isOwner && (
          <div className="panel-body stack">
            <OrgSettings org={org} orgId={currentOrg.id} onSaved={async () => { await refreshSession(); load(); }} />
            <div className="callout small">
              Mock URLs answer for anyone who can reach MockHub. Roles only decide who can see and change the mocks in this app.
            </div>
            <div className="danger-zone section-block">
              <h3>Delete organisation</h3>
              <p className="small">Deletes {currentOrg.name}, its {plural(projectCount, 'project')} and all their stubs. This can't be undone.</p>
              <div>
                <button className="btn danger" onClick={() => setDeleting(true)} disabled={orgs.length < 2 && !isAdmin}>
                  <Trash size={14} /> Delete organisation
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {(adding || editingMember) && teams && (
        <PeopleDialog
          orgId={currentOrg.id}
          orgName={currentOrg.name}
          teams={teams}
          directory={directory}
          canPickOrg={isOwner}
          canCreateUsers={isAdmin}
          member={editingMember ?? undefined}
          excludeIds={people.map((p) => p.id)}
          onClose={() => { setAdding(false); setEditingMember(null); }}
          onDone={afterAccessChange}
        />
      )}
      {removingMember && (
        <Confirm
          title={`Remove ${removingMember.displayName || removingMember.username}?`}
          message={`They lose access to ${currentOrg.name} and all of its teams. Their account stays, so you can add them again later.`}
          confirmLabel="Remove"
          onConfirm={() => removeEverywhere(removingMember)}
          onCancel={() => setRemovingMember(null)}
        />
      )}
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
      {creatingProject && <ProjectDialog onClose={() => { setCreatingProject(false); load(); }} />}
    </div>
  );
}

function OrgSettings({ org, orgId, onSaved }: { org: Organization | null; orgId: string; onSaved: () => void }) {
  const toast = useToast();
  const [name, setName] = useState(org?.name ?? '');
  const [description, setDescription] = useState(org?.description ?? '');

  useEffect(() => {
    setName(org?.name ?? '');
    setDescription(org?.description ?? '');
  }, [org]);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await api.updateOrg(orgId, { name, description });
      toast.notify('Organisation saved');
      onSaved();
    } catch (err) {
      toast.fail(err);
    }
  };

  return (
    <form className="stack" onSubmit={save} style={{ maxWidth: 520 }}>
      <label className="field">
        <span>Name</span>
        <input type="text" value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <label className="field">
        <span>Description</span>
        <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What this organisation is for" />
      </label>
      <div><button className="btn" type="submit">Save</button></div>
    </form>
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
      toast.notify(`Team ${team.name} created. Add its people next.`);
      onCreated(team);
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="New team" description="A team is a group of people who work together. It owns projects, and only its members (and people with access to the whole organisation) see them." onClose={onClose}>
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
