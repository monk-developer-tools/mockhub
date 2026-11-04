import { GROUPS } from './docs.mjs';
import { DEMO, MENUS, PAGES } from '../site-src/pages.mjs';

export { DEMO };

export const GITHUB = 'https://github.com/monk-developer-tools/mockhub';
export const DOCKER_HUB = 'https://hub.docker.com/r/abhaypratap/mockhub';

const esc = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const chevron = '<svg class="chev" width="10" height="10" viewBox="0 0 10 10" aria-hidden="true"><path d="M2 3.5l3 3 3-3" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

export const groupAnchor = (group) => `group-${group.id}`;

function menuItems(icon, menu) {
  const entries = menu.links
    ? menu.links.map(([name, title, text, href]) => ({ name, title, text, href }))
    : PAGES.filter((p) => p.menu === menu.id && !p.hidden).map((p) => ({ name: p.icon, title: p.nav[0], text: p.nav[1], href: `/${p.slug}/` }));
  return entries.map((e) => `<a class="mega-item" href="${e.href}">
                <span class="mega-icon">${icon(e.name, 18)}</span>
                <span><strong>${esc(e.title)}${menu.badge ? ` <em class="mega-badge">${esc(menu.badge)}</em>` : ''}</strong><small>${esc(e.text)}</small></span>
              </a>`).join('\n              ');
}

function menuSide(icon, side) {
  if (!side) return '';
  const [title, text, href] = side.feature;
  return `<div class="mega-side">
              <h4>${esc(side.title)}</h4>
              ${side.links.map(([name, label, link]) => `<a href="${link}">${icon(name, 16)}${esc(label)}</a>`).join('\n              ')}
              <a class="mega-demo" href="${href}">
                <strong>${esc(title)}</strong>
                <span>${esc(text)}</span>
              </a>
            </div>`;
}

export function header(icon, current) {
  const mark = (name) => (current === name ? ' aria-current="page"' : '');
  const menus = MENUS.map((m) => `<div class="nav-menu">
          <button class="nav-menu-trigger" type="button" aria-expanded="false" aria-controls="menu-${m.id}">${esc(m.title)} ${chevron}</button>
          <div class="mega${m.side ? '' : ' mega-narrow'}" id="menu-${m.id}" hidden>
            <div class="mega-groups">
              ${menuItems(icon, m)}
            </div>
            ${menuSide(icon, m.side)}
          </div>
        </div>`).join('\n        ');
  return `<a class="skip" href="#content">Skip to content</a>
  <header class="nav">
    <div class="wrap">
      <a class="brand" href="/"><img src="/assets/img/logo.svg" alt="" width="22" height="22"> MockHub</a>
      <button class="nav-toggle" aria-label="Menu" aria-expanded="false" aria-controls="nav-links">
        <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true"><path d="M3 6h14M3 14h14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
      </button>
      <nav class="nav-links" id="nav-links" aria-label="Main">
        ${menus}
        <a href="/docs/"${mark('docs')}>Docs</a>
        <a href="/tutorials/"${mark('tutorials')}>Tutorials</a>
        <span class="nav-ctas">
          <a class="nav-ghost" href="/docs/quick-start/">Get started</a>
          <a class="pill" href="${DEMO}">Try the demo</a>
        </span>
      </nav>
    </div>
  </header>`;
}

const pageLinks = (menu) => PAGES.filter((p) => p.menu === menu && !p.hidden).map((p) => [p.nav[0], `/${p.slug}/`]);

const FOOTER = [
  ['Service virtualization', pageLinks('virtualization')],
  ['Mock servers', [...pageLinks('mock-servers'), ...PAGES.filter((p) => p.kind === 'compare').map((p) => [p.nav[0], `/${p.slug}/`])]],
  ['Free tools', [...pageLinks('tools'), ['All free tools', '/tools/']]],
  ['Resources', [['Documentation', '/docs/'], ['Tutorials', '/tutorials/'], ['Quick start', '/docs/quick-start/'], ['Helper reference', '/docs/helpers/'], ['API & CI', '/docs/api/'], ['Error codes', '/docs/error-codes/'], ['Deployment', '/docs/deploy/']]],
  ['Project', [['GitHub', GITHUB], ['Docker Hub', DOCKER_HUB], ['Releases', `${GITHUB}/releases`], ['Report an issue', `${GITHUB}/issues`], ['Live demo', DEMO]]],
];

export function footer(icon) {
  const cols = FOOTER.map(([title, links]) => `<div>
          <h2>${title}</h2>
          ${links.map(([label, href]) => `<a href="${href}">${esc(label)}</a>`).join('\n          ')}
        </div>`).join('\n        ');
  return `<footer class="site-footer">
    <div class="wrap">
      <div class="footer-top">
        <div class="footer-brand">
          <a class="brand" href="/"><img src="/assets/img/logo.svg" alt="" width="22" height="22"> MockHub</a>
          <p>A self-hosted mock server for REST and SOAP, built on WireMock. Free and open source.</p>
          <div class="footer-buttons">
            <a href="${GITHUB}">${icon('GithubLogo', 16)}GitHub</a>
            <a href="${DOCKER_HUB}">${icon('Cube', 16)}Docker Hub</a>
          </div>
        </div>
        ${cols}
      </div>
      <div class="footer-bottom">
        <p class="legal">MockHub is built on WireMock and is not affiliated with WireMock Inc.</p>
        <p class="demo-status" data-demo-status><span class="try-dot" aria-hidden="true"></span><a href="${DEMO}">Live demo</a></p>
      </div>
    </div>
  </footer>`;
}

export function featureCards(icon) {
  return GROUPS.map((g) => `<article class="feature-card reveal">
          <span class="feature-icon">${icon(g.icon, 22)}</span>
          <h3>${esc(g.title)}</h3>
          <p>${esc(g.description)}</p>
          <ul>
            ${g.pages.slice(0, 3).map((p) => `<li><a href="/docs/${p.slug}/">${esc(p.title)}</a></li>`).join('\n            ')}
          </ul>
        </article>`).join('\n        ');
}

const USE_CASES = [
  ['TestTube', 'Test environments without the real services', 'Give every dependency its own mock URL in QA and CI. Tests stop failing because another team\'s service is down or out of data.', '/test-environments/', 'Test environments per team'],
  ['Browser', 'Frontend work before the backend exists', 'Import the OpenAPI contract and build against realistic answers on day one, with fake data and CRUD resources that remember.', '/contract-first/', 'Frontend before backend'],
  ['ArrowBendUpRight', 'Partner APIs and their webhooks', 'Mock the payment provider and its callbacks: answer at once, then call your service back, or wait for a partner\'s reply.', '/webhook-mocking/', 'Partner APIs and webhooks'],
  ['Lightning', 'Failure and resilience testing', 'Slow answers, dropped connections and a failure rate per stub, so you can prove timeouts and retries before production does.', '/performance-testing/', 'Performance and chaos testing'],
  ['ArrowSquareIn', 'One home for scattered WireMock mocks', 'Move every team\'s mappings into one server with a UI, sign-in and an audit log, and keep every URL they call today.', '/wiremock-alternative/', 'Moving from WireMock'],
  ['PresentationChart', 'Demos and training that never break', 'Stable data and stateful scenarios that walk through a flow the same way every time, for demos, workshops and support.', '/stateful-mocks/', 'Stateful mocks'],
];

export function useCases(icon) {
  return USE_CASES.map(([name, title, text, href, link]) => `<article class="uc-card reveal">
          <span class="feature-icon">${icon(name, 22)}</span>
          <h3>${esc(title)}</h3>
          <p>${esc(text)}</p>
          <a class="link-chevron" href="${href}">${esc(link)}</a>
        </article>`).join('\n        ');
}
