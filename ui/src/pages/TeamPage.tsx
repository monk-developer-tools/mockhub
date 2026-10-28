import { Buildings, CaretRight, Plus, Trash, UserPlus } from '@phosphor-icons/react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { OrgRole, Team, TeamMember, UserSummary } from '../api/types';
import { PersonCell, TeamMark } from '../components/Avatar';
import { PeopleDialog } from '../components/PeopleDialog';
import { RoleBadge } from '../components/RoleBadge';
import { Skeleton } from '../components/Skeleton';
import { useToast } from '../components/Toasts';
import { ROLE_INFO } from '../model/access';
import { atLeast, useApp } from '../state/AppContext';
import { ProjectDialog } from './ProjectDialog';
import { SourceTag } from './UsersPage';

export const TEAM_ROLES: { value: OrgRole; label: string; hint: string }[] = ROLE_INFO.map((r) => ({ value: r.value, label: r.label, hint: r.detail }));

export function TeamPage() {
  const { teamId = '' } = useParams();
  const { currentOrg, isManager, isAdmin, session, projects, roleInTeam, refreshSession, refreshProjects } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [team, setTeam] = useState<Team | null>(null);
  const [members, setMembers] = useState<TeamMember[] | null>(null);
  const [directory, setDirectory] = useState<UserSummary[]>([]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [adding, setAdding] = useState(false);
  const [missing, setMissing] = useState(false);
  const [creating, setCreating] = useState(false);

  const orgId = currentOrg?.id ?? '';
  const canManage = atLeast(roleInTeam(orgId, teamId), 'manager');
  const canAddProjects = atLeast(roleInTeam(orgId, teamId), 'manager');

  const load = useCallback(async () => {
    if (!orgId) return;
    try {
      const [t, m] = await Promise.all([
        api.teams(orgId).then((list) => list.find((x) => x.id === teamId) ?? null),
        api.teamMembers(orgId, teamId),
      ]);
      setMissing(!t);
      setTeam(t);
      setMembers(m);
      setName(t?.name ?? '');
      setDescription(t?.description ?? '');
    } catch (e) {
      setMissing(true);
      toast.fail(e);
    }
  }, [orgId, teamId, toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (canManage) api.users().then(setDirectory).catch(() => setDirectory([]));
  }, [canManage]);

  const teamProjects = useMemo(() => projects.filter((p) => p.teamId === teamId), [projects, teamId]);
  const direct = (members ?? []).filter((m) => m.member);
  const viaOrg = (members ?? []).filter((m) => !m.member);

  if (missing) {
    return (
      <div className="page">
        <div className="panel empty">
          <h2>Team not found</h2>
          <p>It may have been deleted, or it belongs to another organisation.</p>
          <Link className="btn" to="/org">Back to {currentOrg?.name ?? 'the organisation'}</Link>
        </div>
      </div>
    );
  }
  if (!team || !currentOrg) return <div className="page"><Skeleton rows={4} label="Loading team" /></div>;

  const run = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action();
      toast.notify(message);
      await load();
      return true;
    } catch (err) {
      toast.fail(err);
      return false;
    }
  };

  const setRole = (userId: string, role: OrgRole) =>
    run(() => api.setTeamMember(orgId, teamId, userId, role), 'Role updated').then(async (ok) => {
      if (ok && userId === session?.id) await refreshSession();
      return ok;
    });

  const save = (e: FormEvent) => {
    e.preventDefault();
    run(() => api.updateTeam(orgId, teamId, { name, description }), 'Team saved').then(async (ok) => {
      if (ok) await refreshSession();
    });
  };

  const remove = async () => {
    if (await run(() => api.deleteTeam(orgId, teamId), `Team ${team.name} deleted`)) {
      await Promise.all([refreshSession(), refreshProjects()]);
      navigate('/org');
    }
  };

  return (
    <div className="page">
      <nav className="breadcrumbs" aria-label="Breadcrumb">
        <Link to="/org"><Buildings size={14} weight="duotone" /> {currentOrg.name}</Link>
        <CaretRight size={12} className="muted" />
        <Link to="/org">Teams</Link>
        <CaretRight size={12} className="muted" />
        <span aria-current="page">{team.name}</span>
      </nav>
      <div className="page-header team-header">
        <div className="team-title">
          <TeamMark name={team.name} seed={team.id} size={48} />
          <div>
            <h1>{team.name}</h1>
            <p className="muted">{team.description || `A team in ${currentOrg.name}`}</p>
          </div>
        </div>
        <div className="btn-row">
          <RoleBadge role={isAdmin ? 'admin' : team.role} />
          {canManage && <button className="btn primary" onClick={() => setAdding(true)}><UserPlus size={15} /> Add people</button>}
        </div>
      </div>

      <div className="split">
        <div className="panel">
          <div className="panel-head">
            <h2>Members</h2>
            <span className="muted small">{direct.length} {direct.length === 1 ? 'person' : 'people'}</span>
          </div>
          {members === null ? (
            <div className="panel-body"><Skeleton rows={3} label="Loading members" /></div>
          ) : (
            <table className="data-table">
              <tbody>
                {direct.length === 0 && (
                  <tr><td colSpan={3} className="muted">Nobody has been added to this team yet.{canManage ? ' Use Add people to bring in the people who work on it.' : ''}</td></tr>
                )}
                {direct.map((m) => (
                  <tr key={m.id}>
                    <td>
                      <PersonCell name={m.displayName || m.username} detail={m.email || m.username} seed={m.id} />
                      <SourceTag source={m.source} />
                      {m.disabled && <span className="tag">disabled</span>}
                    </td>
                    <td>
                      {canManage && m.role ? (
                        <select aria-label={`Team role of ${m.username}`} value={m.role} onChange={(e) => setRole(m.id, e.target.value as OrgRole)}>
                          {TEAM_ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                        </select>
                      ) : (
                        <RoleBadge role={m.effectiveRole} />
                      )}
                      {m.groupRole && <div className="muted small">{m.role ? 'also ' : ''}{m.groupRole} from an Entra group</div>}
                    </td>
                    <td className="actions">
                      {canManage && m.role && (
                        <button className="btn ghost icon small" aria-label={`Remove ${m.username} from the team`} title="Remove from team"
                          onClick={() => run(() => api.removeTeamMember(orgId, teamId, m.id), `${m.displayName || m.username} removed`)}>
                          <Trash size={14} />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {viaOrg.length > 0 && (
            <div className="panel-body via-org">
              <h3 className="small">Also have access through {currentOrg.name}</h3>
              <ul>
                {viaOrg.map((m) => (
                  <li key={m.id}>
                    <PersonCell name={m.displayName || m.username} detail={m.email || m.username} seed={m.id} />
                    <RoleBadge role={m.effectiveRole} />
                  </li>
                ))}
              </ul>
            </div>
          )}
          {canManage && (
            <div className="panel-body">
              <button className="btn" onClick={() => setAdding(true)}><UserPlus size={14} /> Add people to {team.name}</button>
            </div>
          )}
        </div>

        <div className="stack">
          <div className="panel">
            <div className="panel-head">
              <h2>Projects</h2>
              {canAddProjects && (
                <button className="btn small" onClick={() => setCreating(true)}><Plus size={13} /> New project</button>
              )}
            </div>
            {teamProjects.length === 0 ? (
              <p className="panel-body muted small">No projects yet. Projects created in this team are only visible to its members and to people with organisation-wide access.</p>
            ) : (
              <ul className="team-projects">
                {teamProjects.map((p) => (
                  <li key={p.id}>
                    <Link to={`/projects/${p.id}`}>
                      <span className="project-mark small" style={{ background: p.color }} aria-hidden="true">{p.name.trim().charAt(0).toUpperCase()}</span>
                      <span className="team-row-text">
                        <strong>{p.name}</strong>
                        <span className="muted small mono">{p.basePath || '/'}</span>
                      </span>
                      <span className="muted small">{p.stubCount} {p.stubCount === 1 ? 'stub' : 'stubs'}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
          {canManage && (
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
          {isManager && (
            <div className="panel danger-zone">
              <div className="panel-head"><h2>Delete team</h2></div>
              <div className="panel-body stack">
                <p className="small">
                  {team.projectCount > 0
                    ? `${team.name} still has ${team.projectCount} ${team.projectCount === 1 ? 'project' : 'projects'}. Move them to another team in their settings, or delete them, first.`
                    : `Deletes ${team.name}. Its members keep their other access.`}
                </p>
                <div>
                  <button className="btn danger" disabled={team.projectCount > 0} onClick={remove}><Trash size={14} /> Delete team</button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
      {creating && <ProjectDialog teamId={teamId} onClose={() => setCreating(false)} />}
      {adding && (
        <PeopleDialog
          orgId={orgId}
          orgName={currentOrg.name}
          teams={[team]}
          directory={directory}
          canPickOrg={false}
          canCreateUsers={isAdmin}
          lockTeam={team}
          excludeIds={direct.map((m) => m.id)}
          onClose={() => setAdding(false)}
          onDone={async () => {
            setAdding(false);
            await load();
            await refreshSession();
          }}
        />
      )}
    </div>
  );
}
