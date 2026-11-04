import type { Project, Role } from '../api/types';
import { atLeast } from '../state/AppContext';

export function moveTargets(projects: Project[], current: string, roleInTeam: (orgId: string | undefined, teamId: string | undefined) => Role): Project[] {
  return projects.filter((p) => p.id !== current && atLeast(roleInTeam(p.orgId, p.teamId), 'editor'));
}
