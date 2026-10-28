import type { Member, OrgRole } from '../api/types';

export interface RoleInfo {
  value: OrgRole;
  label: string;
  verb: string;
  detail: string;
}

export const ROLE_INFO: RoleInfo[] = [
  { value: 'viewer', label: 'Viewer', verb: 'see', detail: 'Looks at projects, stubs and the request log. Changes nothing.' },
  { value: 'editor', label: 'Editor', verb: 'change', detail: 'Creates and changes projects and stubs. The right choice for most developers and testers.' },
  { value: 'owner', label: 'Owner', verb: 'change', detail: 'Everything an editor does, plus adding people and changing settings.' },
];

export const roleInfo = (role: OrgRole) => ROLE_INFO.find((r) => r.value === role)!;

export type Scope = 'org' | 'teams';

export interface CurrentAccess {
  orgRole: OrgRole | '';
  teams: { id: string; role: OrgRole | '' }[];
}

export interface WantedAccess {
  scope: Scope;
  teamIds: string[];
  role: OrgRole;
}

export type AccessChange =
  | { kind: 'setOrg'; role: OrgRole }
  | { kind: 'removeOrg' }
  | { kind: 'setTeam'; teamId: string; role: OrgRole }
  | { kind: 'removeTeam'; teamId: string };

export const NO_CURRENT_ACCESS: CurrentAccess = { orgRole: '', teams: [] };

export function currentAccessOf(member: Pick<Member, 'role' | 'teams'> | undefined): CurrentAccess {
  if (!member) return NO_CURRENT_ACCESS;
  return { orgRole: member.role, teams: member.teams.filter((t) => t.role).map((t) => ({ id: t.id, role: t.role })) };
}

export function wantedFrom(current: CurrentAccess, fallbackTeamIds: string[] = []): WantedAccess {
  if (current.orgRole) return { scope: 'org', teamIds: fallbackTeamIds, role: current.orgRole };
  const direct = current.teams.filter((t) => t.role);
  if (direct.length === 0) return { scope: fallbackTeamIds.length ? 'teams' : 'org', teamIds: fallbackTeamIds, role: 'editor' };
  return { scope: 'teams', teamIds: direct.map((t) => t.id), role: highest(direct.map((t) => t.role as OrgRole)) };
}

export function highest(roles: OrgRole[]): OrgRole {
  return roles.reduce<OrgRole>((best, r) => (rank(r) > rank(best) ? r : best), 'viewer');
}

const rank = (role: OrgRole) => ROLE_INFO.findIndex((r) => r.value === role);

export function planChanges(current: CurrentAccess, wanted: WantedAccess): AccessChange[] {
  const changes: AccessChange[] = [];
  const directTeams = current.teams.filter((t) => t.role);
  if (wanted.scope === 'org') {
    if (current.orgRole !== wanted.role) changes.push({ kind: 'setOrg', role: wanted.role });
    directTeams.forEach((t) => changes.push({ kind: 'removeTeam', teamId: t.id }));
    return changes;
  }
  if (current.orgRole) changes.push({ kind: 'removeOrg' });
  wanted.teamIds.forEach((teamId) => {
    const before = directTeams.find((t) => t.id === teamId)?.role;
    if (before !== wanted.role) changes.push({ kind: 'setTeam', teamId, role: wanted.role });
  });
  directTeams.filter((t) => !wanted.teamIds.includes(t.id)).forEach((t) => changes.push({ kind: 'removeTeam', teamId: t.id }));
  return changes;
}

export function listOf(items: string[]): string {
  if (items.length <= 1) return items[0] ?? '';
  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`;
}

export function sentence(people: string[], wanted: WantedAccess, orgName: string, teamNames: Record<string, string>): string {
  const who = people.length === 0 ? 'Nobody' : people.length > 2 ? `${people.length} people` : listOf(people);
  const verb = roleInfo(wanted.role).verb;
  const manage = wanted.role === 'owner' ? ', add people and change settings' : '';
  if (wanted.scope === 'org') return `${who} will ${verb} every project in ${orgName}${manage}, including teams added later.`;
  const teams = wanted.teamIds.map((id) => teamNames[id] ?? id);
  if (teams.length === 0) return 'Choose at least one team.';
  return `${who} will ${verb} the projects of ${listOf(teams)}${manage}. Other teams stay hidden.`;
}

export interface AccessChip {
  label: string;
  role: OrgRole;
  wide: boolean;
  fromGroup: boolean;
}

export function accessChips(member: Member): AccessChip[] {
  const chips: AccessChip[] = [];
  if (member.role) chips.push({ label: 'Whole organisation', role: member.role, wide: true, fromGroup: false });
  else if (member.groupRole) chips.push({ label: 'Whole organisation', role: member.groupRole, wide: true, fromGroup: true });
  member.teams.forEach((t) => {
    if (t.role) chips.push({ label: t.name, role: t.role, wide: false, fromGroup: false });
  });
  return chips;
}
