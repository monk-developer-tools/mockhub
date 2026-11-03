export const DOCS_URL = 'https://mockhub.devops-monk.com/docs/';
export const GITHUB_URL = 'https://github.com/monk-developer-tools/mockhub';

export interface Crumb {
  label: string;
  to?: string;
}

export interface CrumbContext {
  orgName?: string;
  projectName?: string;
  teamName?: string;
}

const PAGES: Record<string, string> = {
  examples: 'Examples',
  playground: 'Playground',
  server: 'Server',
  org: 'Teams & people',
  audit: 'Audit log',
  'admin/users': 'Users',
  'admin/sso': 'Single sign-on',
  'admin/ai': 'AI assistant',
  'admin/backup': 'Backup & restore',
};

export const PROJECT_SECTIONS: Record<string, string> = {
  overview: 'Overview',
  stubs: 'Stubs',
  data: 'Data',
  assistant: 'Assistant',
  requests: 'Requests',
  callouts: 'Callouts',
  scenarios: 'Scenarios',
  'import-export': 'Import & export',
  activity: 'Activity',
  settings: 'Settings',
};

const DOCS: [RegExp, string][] = [
  [/^\/projects\/[^/]+\/stubs/, 'matching'],
  [/^\/projects\/[^/]+\/data/, 'crud'],
  [/^\/projects\/[^/]+\/assistant/, 'ai'],
  [/^\/projects\/[^/]+\/requests/, 'requests'],
  [/^\/projects\/[^/]+\/callouts/, 'webhooks'],
  [/^\/projects\/[^/]+\/scenarios/, 'scenarios'],
  [/^\/projects\/[^/]+\/import-export/, 'import'],
  [/^\/projects\/[^/]+\/activity/, 'audit'],
  [/^\/projects\/[^/]+\/settings/, 'api-keys'],
  [/^\/projects\//, 'concepts'],
  [/^\/playground/, 'playground'],
  [/^\/examples/, 'use-cases'],
  [/^\/org/, 'auth'],
  [/^\/audit/, 'audit'],
  [/^\/server/, 'config'],
  [/^\/admin\/users/, 'auth'],
  [/^\/admin\/sso/, 'sso'],
  [/^\/admin\/ai/, 'ai'],
  [/^\/admin\/backup/, 'persistence'],
];

export function docsAt(anchor: string): string {
  return `${DOCS_URL}#${anchor}`;
}

export function docsFor(pathname: string): string {
  const match = DOCS.find(([pattern]) => pattern.test(pathname));
  return match ? docsAt(match[1]) : DOCS_URL;
}

export function breadcrumbsFor(pathname: string, context: CrumbContext): Crumb[] {
  const home: Crumb = { label: context.orgName ?? 'Projects', to: '/' };
  const path = pathname.replace(/^\/+|\/+$/g, '');
  if (path === '') return [{ label: home.label }];

  const project = path.match(/^projects\/([^/]+)(?:\/([^/]+))?(?:\/([^/]+))?/);
  if (project) {
    const [, id, section, item] = project;
    const projectCrumb: Crumb = { label: context.projectName ?? 'Project', to: `/projects/${id}` };
    const sectionLabel = section ? PROJECT_SECTIONS[section] : undefined;
    if (!sectionLabel) return [home, { label: projectCrumb.label }];
    if (section === 'stubs' && item) {
      return [home, projectCrumb, { label: sectionLabel, to: `/projects/${id}/stubs` }, { label: item === 'new' ? 'New stub' : 'Stub' }];
    }
    return [home, projectCrumb, { label: sectionLabel }];
  }

  const team = path.match(/^org\/teams\/[^/]+/);
  if (team) return [home, { label: PAGES.org, to: '/org' }, { label: context.teamName ?? 'Team' }];

  const page = PAGES[path];
  if (!page) return [home];
  if (path.startsWith('admin/')) return [{ label: 'Administration' }, { label: page }];
  return [home, { label: page }];
}
