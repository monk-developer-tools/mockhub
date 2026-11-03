import { describe, expect, it } from 'vitest';
import { breadcrumbsFor, docsFor, DOCS_URL } from './navigation';

describe('navigation', () => {
  it('builds project breadcrumbs down to a stub', () => {
    // When
    const crumbs = breadcrumbsFor('/projects/p1/stubs/s9', { orgName: 'Default', projectName: 'Payments' });

    // Then
    expect(crumbs).toEqual([
      { label: 'Default', to: '/' },
      { label: 'Payments', to: '/projects/p1' },
      { label: 'Stubs', to: '/projects/p1/stubs' },
      { label: 'Stub' },
    ]);
  });

  it('ends on the current page without a link', () => {
    // When
    const overview = breadcrumbsFor('/projects/p1/overview', { orgName: 'Default', projectName: 'Payments' });
    const home = breadcrumbsFor('/', { orgName: 'Default' });
    const admin = breadcrumbsFor('/admin/sso', {});
    const team = breadcrumbsFor('/org/teams/t1', { orgName: 'Default', teamName: 'Checkout' });

    // Then
    expect(overview[overview.length - 1]).toEqual({ label: 'Overview' });
    expect(home).toEqual([{ label: 'Default' }]);
    expect(admin).toEqual([{ label: 'Administration' }, { label: 'Single sign-on' }]);
    expect(team.map((c) => c.label)).toEqual(['Default', 'Teams & people', 'Checkout']);
  });

  it('points the help menu at the docs for the current section', () => {
    // When
    const callouts = docsFor('/projects/p1/callouts');
    const stub = docsFor('/projects/p1/stubs/s1');
    const unknown = docsFor('/somewhere');

    // Then
    expect(callouts).toBe(`${DOCS_URL}webhooks/`);
    expect(stub).toBe(`${DOCS_URL}matching/`);
    expect(unknown).toBe(DOCS_URL);
  });
});
