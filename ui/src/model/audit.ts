import type { AuditEntry, Session } from '../api/types';

export function canSeeAuditLog(session: Session | null | undefined): boolean {
  if (!session) return false;
  if (session.isAdmin) return true;
  return session.orgs.some((o) => o.role === 'manager' || o.teams.some((t) => t.member && t.role === 'manager'));
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

export function fullDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

export interface AuditDay {
  label: string;
  date: string;
  entries: AuditEntry[];
}

export function groupByDay(entries: AuditEntry[], now: Date = new Date()): AuditDay[] {
  const groups: AuditDay[] = [];
  for (const e of entries) {
    const label = dayLabel(e.at, now);
    const last = groups[groups.length - 1];
    if (last && last.label === label) last.entries.push(e);
    else groups.push({ label, date: fullDate(e.at), entries: [e] });
  }
  return groups;
}

export function matches(e: AuditEntry, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return [e.actor, e.action, e.target, e.targetName, e.details, e.projectName ?? ''].join(' ').toLowerCase().includes(q);
}

export type ActionKind = 'create' | 'change' | 'delete';

const CREATE_ACTIONS = ['created', 'installed', 'imported', 'gave', 'added', 'recorded', 'restored'];
const DELETE_ACTIONS = ['deleted', 'removed', 'cleared'];

export function actionKind(action: string): ActionKind {
  const verb = action.split(' ')[0];
  if (CREATE_ACTIONS.includes(verb)) return 'create';
  if (DELETE_ACTIONS.includes(verb)) return 'delete';
  return 'change';
}

export type TimeRange = 'day' | 'week' | 'month' | 'all';

const RANGE_MS: Record<Exclude<TimeRange, 'all'>, number> = { day: 86_400_000, week: 7 * 86_400_000, month: 30 * 86_400_000 };

export function inRange(e: AuditEntry, range: TimeRange, now: number = Date.now()): boolean {
  if (range === 'all') return true;
  return now - new Date(e.at).getTime() <= RANGE_MS[range];
}

export interface AuditFilter {
  query: string;
  kind: ActionKind | 'all';
  actorId: string;
  range: TimeRange;
}

export const NO_FILTER: AuditFilter = { query: '', kind: 'all', actorId: '', range: 'all' };

export function applyFilter(entries: AuditEntry[], filter: AuditFilter, now: number = Date.now()): AuditEntry[] {
  return entries.filter(
    (e) =>
      matches(e, filter.query) &&
      (filter.kind === 'all' || actionKind(e.action) === filter.kind) &&
      (!filter.actorId || e.actorId === filter.actorId) &&
      inRange(e, filter.range, now),
  );
}

export function people(entries: AuditEntry[]): { id: string; name: string; count: number }[] {
  const byId = new Map<string, { id: string; name: string; count: number }>();
  for (const e of entries) {
    const p = byId.get(e.actorId) ?? { id: e.actorId, name: e.actor, count: 0 };
    p.count += 1;
    byId.set(e.actorId, p);
  }
  return [...byId.values()].sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
}

export function countByKind(entries: AuditEntry[]): Record<ActionKind, number> {
  const counts: Record<ActionKind, number> = { create: 0, change: 0, delete: 0 };
  for (const e of entries) counts[actionKind(e.action)] += 1;
  return counts;
}

export function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
}

export function fullTimestamp(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, timeZoneName: 'short',
  });
}

export function localTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

const csvCell = (value: string) => (/[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value);

export function toCsv(entries: AuditEntry[]): string {
  const header = ['time (UTC)', 'person', 'action', 'target', 'name', 'details', 'project'];
  const rows = entries.map((e) => [e.at, e.actor, e.action, e.target, e.targetName, e.details, e.projectName ?? '']);
  return [header, ...rows].map((r) => r.map(csvCell).join(',')).join('\n') + '\n';
}
