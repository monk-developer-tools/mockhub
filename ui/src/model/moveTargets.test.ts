import type { Project, Role } from '../api/types';
import { moveTargets } from './moveTargets';

const project = (id: string, teamId: string) => ({ id, name: id, orgId: 'o1', teamId }) as Project;

describe('moveTargets', () => {
  it('offers only other projects the person can edit', () => {
    // Given
    const roles: Record<string, Role> = { checkout: 'editor', search: 'viewer', secrets: 'none', platform: 'manager' };
    const projects = [project('cart', 'checkout'), project('cart-qa', 'checkout'), project('index', 'search'), project('vault', 'secrets'), project('gateway', 'platform')];

    // When
    const targets = moveTargets(projects, 'cart', (_org, team) => roles[team ?? ''] ?? 'none');

    // Then
    expect(targets.map((p) => p.id)).toEqual(['cart-qa', 'gateway']);
  });
});
