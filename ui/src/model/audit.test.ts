import type { AuditEntry, Session } from '../api/types';
import { actionKind, applyFilter, canSeeAuditLog, clockTime, countByKind, dayLabel, groupByDay, matches, NO_FILTER, people, toCsv, whatHappened } from './audit';

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

  it('sorts actions into created, changed and deleted', () => {
    // Then
    expect(actionKind('created')).toBe('create');
    expect(actionKind('installed')).toBe('create');
    expect(actionKind('updated')).toBe('change');
    expect(actionKind('turned off')).toBe('change');
    expect(actionKind('deleted')).toBe('delete');
    expect(actionKind('cleared')).toBe('delete');
  });

  it('filters by kind, person and time range', () => {
    // Given
    const now = new Date('2026-03-10T12:00:00Z').getTime();
    const entries = [
      entry({ id: 'a', at: '2026-03-10T11:00:00Z', action: 'created', actorId: 'u1' }),
      entry({ id: 'b', at: '2026-03-08T11:00:00Z', action: 'deleted', actorId: 'u2', actor: 'Maria Silva' }),
      entry({ id: 'c', at: '2026-01-01T11:00:00Z', action: 'updated', actorId: 'u1' }),
    ];
    const ids = (f: Partial<typeof NO_FILTER>) => applyFilter(entries, { ...NO_FILTER, ...f }, now).map((e) => e.id);

    // Then
    expect(ids({})).toEqual(['a', 'b', 'c']);
    expect(ids({ kind: 'delete' })).toEqual(['b']);
    expect(ids({ actorId: 'u1' })).toEqual(['a', 'c']);
    expect(ids({ range: 'day' })).toEqual(['a']);
    expect(ids({ range: 'week' })).toEqual(['a', 'b']);
    expect(ids({ range: 'month', actorId: 'u2', query: 'maria' })).toEqual(['b']);
  });

  it('lists the people who made changes, busiest first, and counts each kind', () => {
    // Given
    const entries = [entry({ actorId: 'u2', actor: 'Maria' }), entry({ action: 'created' }), entry({ action: 'updated' })];

    // Then
    expect(people(entries)).toEqual([{ id: 'u1', name: 'Sam Patel', count: 2 }, { id: 'u2', name: 'Maria', count: 1 }]);
    expect(countByKind(entries)).toEqual({ create: 1, change: 1, delete: 1 });
  });

  it('shows the exact time to the second', () => {
    // Given
    const at = new Date(2026, 2, 10, 14, 5, 9).toISOString();

    // Then
    expect(clockTime(at)).toMatch(/14.05.09/);
  });

  it('exports entries as CSV with quoted cells', () => {
    // When
    const csv = toCsv([entry({ details: 'status 200, then 404' })]);

    // Then
    expect(csv.split('\n')[0]).toBe('time (UTC),person,action,target,name,details,project');
    expect(csv.split('\n')[1]).toBe('2026-03-10T09:00:00Z,Sam Patel,deleted,stub,GET /orders,"status 200, then 404",Shop');
  });
});
