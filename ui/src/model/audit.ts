import type { AuditEntry, Session } from '../api/types';

export function canSeeAuditLog(session: Session | null | undefined): boolean {
  if (!session) return false;
  if (session.isAdmin) return true;
  return session.orgs.some((o) => o.role === 'owner' || o.teams.some((t) => t.member && t.role === 'owner'));
}

export function whatHappened(e: AuditEntry): string {
  const name = e.targetName ? ` “${e.targetName}”` : '';
  return `${e.action} ${e.target}${name}`;
}

export function dayLabel(iso: string, now: Date = new Date()): string {
  const day = new Date(iso);
  const start = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diff = Math.round((start(now) - start(day)) / 86_400_000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  return day.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: day.getFullYear() === now.getFullYear() ? undefined : 'numeric' });
}

export function groupByDay(entries: AuditEntry[], now: Date = new Date()): { label: string; entries: AuditEntry[] }[] {
  const groups: { label: string; entries: AuditEntry[] }[] = [];
  for (const e of entries) {
    const label = dayLabel(e.at, now);
    const last = groups[groups.length - 1];
    if (last && last.label === label) last.entries.push(e);
    else groups.push({ label, entries: [e] });
  }
  return groups;
}

export function matches(e: AuditEntry, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return [e.actor, e.action, e.target, e.targetName, e.details, e.projectName ?? ''].join(' ').toLowerCase().includes(q);
}
