export const SIDEBAR_PROJECT_LIMIT = 8;

export function shortProjectName(name: string): string {
  const cut = name.search(/\s[—–]\s/);
  return cut > 0 ? name.slice(0, cut) : name;
}

export function sidebarProjects<T extends { id: string }>(projects: T[], activeId: string | undefined, limit = SIDEBAR_PROJECT_LIMIT): T[] {
  if (projects.length <= limit) return projects;
  const shown = projects.slice(0, limit);
  const active = projects.find((p) => p.id === activeId);
  return active && !shown.includes(active) ? [...shown.slice(0, limit - 1), active] : shown;
}
