import { GROUPS, topicPath } from '../docs-src/docs.mjs';
import { MENUS, PAGES } from './pages.mjs';

export const OG_WIDTH = 1200;
export const OG_HEIGHT = 630;

export const ogKey = (path) => path.replace(/^\/|\/$/g, '').replace(/\//g, '-') || 'home';

export const ogImage = (path) => `/assets/img/og/${ogKey(path)}.jpg`;

const featureEyebrow = (page) => {
  if (page.kind === 'compare') return 'Comparison';
  if (page.kind === 'tool') return 'Free tool · no login';
  if (page.kind === 'tools-index') return 'Free tools · no login';
  return MENUS.find((m) => m.id === page.menu)?.title ?? 'MockHub';
};

export function ogPages() {
  return [
    { path: '/', eyebrow: 'Mock server', title: 'Mock any API in minutes. Share it with your whole team.', description: 'A self-hosted mock server for REST and SOAP, built on WireMock.' },
    { path: '/tutorials/', eyebrow: 'Tutorials', title: 'Learn MockHub step by step', description: 'A screenshot for every step, from your first stub to teams and single sign-on.' },
    { path: '/docs/', eyebrow: 'Documentation', title: 'MockHub docs', description: 'Run MockHub, mock REST and SOAP services, send webhooks and share mocks with your team.' },
    ...GROUPS.map((group) => ({
      path: topicPath(group),
      eyebrow: 'Docs',
      title: group.title,
      description: group.description,
    })),
    ...GROUPS.flatMap((group) => group.pages.map((page) => ({
      path: `/docs/${page.slug}/`,
      eyebrow: `Docs · ${group.title}`,
      title: page.title,
      description: page.description,
    }))),
    ...PAGES.map((page) => ({
      path: `/${page.slug}/`,
      eyebrow: featureEyebrow(page),
      title: page.h1,
      description: page.description,
    })),
  ];
}
