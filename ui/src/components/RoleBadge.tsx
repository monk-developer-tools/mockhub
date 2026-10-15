import type { Role } from '../api/types';

const LABELS: Record<Role, string> = { none: 'None', viewer: 'Viewer', editor: 'Editor', owner: 'Owner', admin: 'Admin' };

export function roleLabel(role: Role) {
  return LABELS[role] ?? role;
}

export function RoleBadge({ role }: { role: Role }) {
  return <span className={`role-badge role-${role}`}>{roleLabel(role)}</span>;
}
