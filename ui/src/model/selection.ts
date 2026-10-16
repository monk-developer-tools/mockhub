import type { BackupSelection } from '../api/types';

export interface OrgNode {
  id: string;
  projectIds: string[];
}

export interface Picked {
  orgs: ReadonlySet<string>;
  projects: ReadonlySet<string>;
}

export const NOTHING: Picked = { orgs: new Set(), projects: new Set() };

export function everything(tree: OrgNode[]): Picked {
  return { orgs: new Set(tree.map((o) => o.id)), projects: new Set() };
}

export function isProjectPicked(picked: Picked, org: OrgNode, projectId: string) {
  return picked.orgs.has(org.id) || picked.projects.has(projectId);
}

export function orgState(picked: Picked, org: OrgNode): 'all' | 'some' | 'none' {
  if (picked.orgs.has(org.id)) return 'all';
  const count = org.projectIds.filter((id) => picked.projects.has(id)).length;
  if (count === 0) return 'none';
  return count === org.projectIds.length ? 'all' : 'some';
}

export function toggleOrg(picked: Picked, org: OrgNode): Picked {
  const orgs = new Set(picked.orgs);
  const projects = new Set(picked.projects);
  const wasAll = orgState(picked, org) === 'all';
  org.projectIds.forEach((id) => projects.delete(id));
  if (wasAll) orgs.delete(org.id);
  else orgs.add(org.id);
  return { orgs, projects };
}

export function toggleProject(picked: Picked, org: OrgNode, projectId: string): Picked {
  const orgs = new Set(picked.orgs);
  const projects = new Set(picked.projects);
  if (orgs.has(org.id)) {
    orgs.delete(org.id);
    org.projectIds.filter((id) => id !== projectId).forEach((id) => projects.add(id));
  } else if (projects.has(projectId)) {
    projects.delete(projectId);
  } else {
    projects.add(projectId);
  }
  return { orgs, projects };
}

export function pickedProjectIds(picked: Picked, tree: OrgNode[]) {
  return tree.flatMap((o) => o.projectIds.filter((id) => isProjectPicked(picked, o, id)));
}

export function toSelection(picked: Picked, tree: OrgNode[]): BackupSelection {
  if (tree.length > 0 && tree.every((o) => picked.orgs.has(o.id))) return { everything: true, organizations: [], projects: [] };
  return { organizations: [...picked.orgs], projects: [...picked.projects] };
}
