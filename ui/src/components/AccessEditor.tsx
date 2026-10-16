import { Buildings } from '@phosphor-icons/react';
import type { Organization, OrgRole, Team } from '../api/types';
import { TeamMark } from './Avatar';

export interface OrgWithTeams {
  org: Organization;
  teams: Team[];
}

export interface Access {
  memberships: { orgId: string; role: OrgRole }[];
  teamMemberships: { teamId: string; role: OrgRole }[];
}

export const NO_ACCESS: Access = { memberships: [], teamMemberships: [] };

const ROLES: OrgRole[] = ['viewer', 'editor', 'owner'];
const LABEL: Record<OrgRole, string> = { viewer: 'Viewer', editor: 'Editor', owner: 'Owner' };

export function summarise(access: Access) {
  const teams = access.teamMemberships.length;
  const orgs = access.memberships.length;
  if (!teams && !orgs) return 'No access yet';
  const parts = [];
  if (orgs) parts.push(`${orgs} organisation-wide ${orgs === 1 ? 'role' : 'roles'}`);
  if (teams) parts.push(`${teams} team ${teams === 1 ? 'role' : 'roles'}`);
  return parts.join(' and ');
}

export function AccessEditor({ tree, value, onChange }: { tree: OrgWithTeams[]; value: Access; onChange: (next: Access) => void }) {
  const orgRole = (orgId: string) => value.memberships.find((m) => m.orgId === orgId)?.role ?? '';
  const teamRole = (teamId: string) => value.teamMemberships.find((m) => m.teamId === teamId)?.role ?? '';

  const setOrg = (orgId: string, role: OrgRole | '') => {
    const rest = value.memberships.filter((m) => m.orgId !== orgId);
    onChange({ ...value, memberships: role ? [...rest, { orgId, role }] : rest });
  };

  const setTeam = (teamId: string, role: OrgRole | '') => {
    const rest = value.teamMemberships.filter((m) => m.teamId !== teamId);
    onChange({ ...value, teamMemberships: role ? [...rest, { teamId, role }] : rest });
  };

  if (tree.length === 0) return <p className="muted small">No organisations yet.</p>;

  return (
    <div className="access-tree">
      {tree.map(({ org, teams }) => {
        const wide = orgRole(org.id);
        return (
          <section key={org.id} className="access-org" aria-label={org.name}>
            <div className="access-row access-org-row">
              <span className="access-name">
                <Buildings size={18} weight="duotone" />
                <strong>{org.name}</strong>
              </span>
              <select aria-label={`Organisation-wide role in ${org.name}`} value={wide} onChange={(e) => setOrg(org.id, e.target.value as OrgRole | '')}>
                <option value="">No organisation-wide role</option>
                {ROLES.map((r) => <option key={r} value={r}>{LABEL[r]} in every team</option>)}
              </select>
            </div>
            <ul className="access-teams">
              {teams.map((t) => (
                <li key={t.id} className="access-row">
                  <span className="access-name">
                    <TeamMark name={t.name} seed={t.id} size={20} />
                    <span>{t.name}</span>
                  </span>
                  <select aria-label={`Role in team ${t.name}`} value={teamRole(t.id)} onChange={(e) => setTeam(t.id, e.target.value as OrgRole | '')}>
                    <option value="">{wide ? `${LABEL[wide]} through ${org.name}` : 'No access'}</option>
                    {ROLES.map((r) => <option key={r} value={r}>{LABEL[r]}</option>)}
                  </select>
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
