import { shortProjectName, sidebarProjects } from './sidebarProjects';

describe('sidebar projects', () => {
  it('keeps the part of a name before a dash separator', () => {
    // Given
    const names = ['Bookshop — from an OpenAPI spec', 'Orders – CRUD', 'Payments', 'Check-out'];

    // When
    const short = names.map(shortProjectName);

    // Then
    expect(short).toEqual(['Bookshop', 'Orders', 'Payments', 'Check-out']);
  });

  it('shows every project when there are few', () => {
    // Given
    const projects = [{ id: 'a' }, { id: 'b' }];

    // When
    const shown = sidebarProjects(projects, undefined, 3);

    // Then
    expect(shown).toEqual(projects);
  });

  it('caps a long list and keeps the open project visible', () => {
    // Given
    const projects = ['a', 'b', 'c', 'd', 'e'].map((id) => ({ id }));

    // When
    const plain = sidebarProjects(projects, 'b', 3);
    const withActive = sidebarProjects(projects, 'e', 3);

    // Then
    expect(plain.map((p) => p.id)).toEqual(['a', 'b', 'c']);
    expect(withActive.map((p) => p.id)).toEqual(['a', 'b', 'e']);
  });
});
