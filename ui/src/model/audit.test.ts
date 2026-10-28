import type { AuditEntry, Session } from '../api/types';
import { canSeeAuditLog, dayLabel, groupByDay, matches, whatHappened } from './audit';

const entry = (over: Partial<AuditEntry> = {}): AuditEntry => ({
  id: 'e1', at: '2026-03-10T09:00:00Z', actorId: 'u1', actor: 'Sam Patel', action: 'deleted', target: 'stub',
  targetName: 'GET /orders', details: '', orgId: 'o1', teamId: 't1', projectId: 'p1', projectName: 'Shop', ...over,
});

const session = (over: Partial<Session>): Session => ({ isAdmin: false, orgs: [], ...over }) as Session;

describe('audit model', () => {
  it('describes what happened', () => {
    // When
    const text = whatHappened(entry());

    // Then
    expect(text).toBe('deleted stub “GET /orders”');
    expect(whatHappened(entry({ action: 'cleared', target: 'request log', targetName: '' }))).toBe('cleared request log');
  });

  it('shows the audit log to admins and managers only', () => {
    // Given
    const editor = session({ orgs: [{ id: 'o1', name: 'Acme', slug: 'acme', role: 'editor', teams: [] }] });
    const teamManager = session({ orgs: [{ id: 'o1', name: 'Acme', slug: 'acme', role: 'none', teams: [{ id: 't1', name: 'Shop', slug: 'shop', role: 'manager', member: true }] }] });

    // Then
    expect(canSeeAuditLog(session({ isAdmin: true }))).toBe(true);
    expect(canSeeAuditLog(teamManager)).toBe(true);
    expect(canSeeAuditLog(editor)).toBe(false);
    expect(canSeeAuditLog(null)).toBe(false);
  });

  it('groups entries by day', () => {
    // Given
    const now = new Date(2026, 2, 10, 18, 0);
    const entries = [
      entry({ id: 'a', at: new Date(2026, 2, 10, 9, 0).toISOString() }),
      entry({ id: 'b', at: new Date(2026, 2, 10, 8, 0).toISOString() }),
      entry({ id: 'c', at: new Date(2026, 2, 9, 12, 0).toISOString() }),
    ];

    // When
    const groups = groupByDay(entries, now);

    // Then
    expect(groups.map((g) => [g.label, g.entries.map((e) => e.id)])).toEqual([['Today', ['a', 'b']], ['Yesterday', ['c']]]);
    expect(dayLabel(new Date(2026, 2, 1).toISOString(), now)).not.toMatch(/Today|Yesterday/);
  });

  it('searches across people, things and projects', () => {
    // Then
    expect(matches(entry(), 'sam')).toBe(true);
    expect(matches(entry(), 'shop')).toBe(true);
    expect(matches(entry(), 'orders')).toBe(true);
    expect(matches(entry(), 'maria')).toBe(false);
    expect(matches(entry(), '  ')).toBe(true);
  });
});
