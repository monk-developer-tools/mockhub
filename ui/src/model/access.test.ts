import type { Member } from '../api/types';
import { accessChips, currentAccessOf, highest, listOf, planChanges, sentence, wantedFrom } from './access';

const member = (over: Partial<Member>): Member => ({
  id: 'u1', username: 'kim', displayName: 'Kim', email: '', source: 'local', role: '', groupRole: '', effectiveRole: 'viewer', disabled: false, teams: [], ...over,
});

describe('access', () => {
  it('adds a new person to the whole organisation with one change', () => {
    // When
    const changes = planChanges({ orgRole: '', teams: [] }, { scope: 'org', teamIds: [], role: 'editor' });

    // Then
    expect(changes).toEqual([{ kind: 'setOrg', role: 'editor' }]);
  });

  it('moving someone from teams to the whole organisation drops their team roles', () => {
    // Given
    const current = { orgRole: '' as const, teams: [{ id: 't1', role: 'viewer' as const }, { id: 't2', role: 'editor' as const }] };

    // When
    const changes = planChanges(current, { scope: 'org', teamIds: [], role: 'editor' });

    // Then
    expect(changes).toEqual([
      { kind: 'setOrg', role: 'editor' },
      { kind: 'removeTeam', teamId: 't1' },
      { kind: 'removeTeam', teamId: 't2' },
    ]);
  });

  it('narrowing to some teams removes the organisation role and only touches what changed', () => {
    // Given
    const current = { orgRole: 'viewer' as const, teams: [{ id: 't1', role: 'editor' as const }, { id: 't3', role: 'editor' as const }] };

    // When
    const changes = planChanges(current, { scope: 'teams', teamIds: ['t1', 't2'], role: 'editor' });

    // Then
    expect(changes).toEqual([
      { kind: 'removeOrg' },
      { kind: 'setTeam', teamId: 't2', role: 'editor' },
      { kind: 'removeTeam', teamId: 't3' },
    ]);
  });

  it('starts editing from what the person has today', () => {
    expect(wantedFrom({ orgRole: 'manager', teams: [] })).toEqual({ scope: 'org', teamIds: [], role: 'manager' });
    expect(wantedFrom({ orgRole: '', teams: [{ id: 't1', role: 'viewer' }, { id: 't2', role: 'editor' }] })).toEqual({ scope: 'teams', teamIds: ['t1', 't2'], role: 'editor' });
    expect(wantedFrom({ orgRole: '', teams: [] }, ['t9'])).toEqual({ scope: 'teams', teamIds: ['t9'], role: 'editor' });
  });

  it('reads current access from a member, ignoring teams they only see through the organisation', () => {
    // Given
    const kim = member({ role: '', teams: [{ id: 't1', name: 'Checkout', role: 'editor' }, { id: 't2', name: 'Search', role: '' }] });

    // When / Then
    expect(currentAccessOf(kim)).toEqual({ orgRole: '', teams: [{ id: 't1', role: 'editor' }] });
  });

  it('describes the outcome in one sentence', () => {
    expect(sentence(['Kim'], { scope: 'org', teamIds: [], role: 'viewer' }, 'Acme', {})).toBe('Kim will see every project in Acme, including teams added later.');
    expect(sentence(['Kim', 'Sam'], { scope: 'teams', teamIds: ['t1', 't2'], role: 'editor' }, 'Acme', { t1: 'Checkout', t2: 'Search' }))
      .toBe('Kim and Sam will change the projects of Checkout and Search. Other teams stay hidden.');
    expect(sentence(['A', 'B', 'C'], { scope: 'org', teamIds: [], role: 'manager' }, 'Acme', {}))
      .toBe('3 people will change every project in Acme, add people and change settings, including teams added later.');
    expect(sentence(['Kim'], { scope: 'teams', teamIds: [], role: 'editor' }, 'Acme', {})).toBe('Choose at least one team.');
  });

  it('turns memberships into chips', () => {
    // Given
    const kim = member({ role: 'viewer', teams: [{ id: 't1', name: 'Checkout', role: 'editor' }] });

    // When / Then
    expect(accessChips(kim)).toEqual([
      { label: 'Whole organisation', role: 'viewer', wide: true, fromGroup: false },
      { label: 'Checkout', role: 'editor', wide: false, fromGroup: false },
    ]);
    expect(accessChips(member({ groupRole: 'editor' }))[0]).toMatchObject({ wide: true, fromGroup: true });
  });

  it('joins names and picks the highest role', () => {
    expect(listOf(['a', 'b', 'c'])).toBe('a, b and c');
    expect(highest(['viewer', 'manager', 'editor'])).toBe('manager');
  });
});
