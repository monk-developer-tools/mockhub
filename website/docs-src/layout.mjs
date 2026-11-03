import { GROUPS } from './docs.mjs';

export const GITHUB = 'https://github.com/monk-developer-tools/mockhub';
export const DOCKER_HUB = 'https://hub.docker.com/r/abhaypratap/mockhub';
export const DEMO = '/__admin/mockhub/ui/';

const esc = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const chevron = '<svg class="chev" width="10" height="10" viewBox="0 0 10 10" aria-hidden="true"><path d="M2 3.5l3 3 3-3" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

export const groupAnchor = (group) => `group-${group.id}`;

export function header(icon, current) {
  const mark = (name) => (current === name ? ' aria-current="page"' : '');
  const items = GROUPS.map((g) => `<a class="mega-item" href="/docs/#${groupAnchor(g)}">
                <span class="mega-icon">${icon(g.icon, 18)}</span>
                <span><strong>${esc(g.title)}</strong><small>${esc(g.description)}</small></span>
              </a>`).join('\n              ');
  return `<a class="skip" href="#content">Skip to content</a>
  <header class="nav">
    <div class="wrap">
      <a class="brand" href="/"><img src="/assets/img/logo.svg" alt="" width="22" height="22"> MockHub</a>
      <button class="nav-toggle" aria-label="Menu" aria-expanded="false" aria-controls="nav-links">
        <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true"><path d="M3 6h14M3 14h14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
      </button>
      <nav class="nav-links" id="nav-links" aria-label="Main">
        <div class="nav-menu">
          <button class="nav-menu-trigger" type="button" aria-expanded="false" aria-controls="product-menu">Product ${chevron}</button>
          <div class="mega" id="product-menu" hidden>
            <div class="mega-groups">
              ${items}
            </div>
            <div class="mega-side">
              <h4>Start here</h4>
              <a href="/docs/quick-start/">${icon('Lightning', 16)}Quick start</a>
              <a href="/tutorials/">${icon('GraduationCap', 16)}Step-by-step tutorials</a>
              <a href="/#use-cases">${icon('Signpost', 16)}Use cases</a>
              <a href="/docs/migrate/">${icon('ArrowSquareIn', 16)}Moving from WireMock</a>
              <a class="mega-demo" href="${DEMO}">
                <strong>Open the live demo</strong>
                <span>Sign in as demo / demo and look around.</span>
              </a>
            </div>
          </div>
        </div>
        <a href="/#use-cases">Use cases</a>
        <a href="/docs/"${mark('docs')}>Docs</a>
        <a href="/tutorials/"${mark('tutorials')}>Tutorials</a>
        <a href="${GITHUB}">${icon('GithubLogo', 16)}GitHub</a>
        <span class="nav-ctas">
          <a class="nav-ghost" href="/docs/quick-start/">Get started</a>
          <a class="pill" href="${DEMO}">Try the demo</a>
        </span>
      </nav>
    </div>
  </header>`;
}

const FOOTER = [
  ['Product', [['Features', '/#features'], ['Use cases', '/#use-cases'], ['AI assistant', '/#ai'], ['Moving from WireMock', '/#migrate'], ['Teams', '/#teams'], ['Live demo', DEMO]]],
  ['Docs', [['Quick start', '/docs/quick-start/'], ['Request matching', '/docs/matching/'], ['Webhooks & callouts', '/docs/webhooks/'], ['Helper reference', '/docs/helpers/'], ['Error codes', '/docs/error-codes/'], ['API & CI', '/docs/api/']]],
  ['Learn', [['Tutorials', '/tutorials/'], ['Concepts', '/docs/concepts/'], ['Which feature when', '/docs/use-cases/'], ['Deployment', '/docs/deploy/'], ['Configuration', '/docs/config/']]],
  ['Project', [['GitHub', GITHUB], ['Docker Hub', DOCKER_HUB], ['Report an issue', `${GITHUB}/issues`], ['Releases', `${GITHUB}/releases`]]],
];

export function footer(icon) {
  const cols = FOOTER.map(([title, links]) => `<div>
          <h4>${title}</h4>
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
      <p class="legal">MockHub is built on WireMock and is not affiliated with WireMock Inc.</p>
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
  ['TestTube', 'Test environments without the real services', 'Give every dependency its own mock URL in QA and CI. Tests stop failing because another team\'s service is down or out of data.', '/docs/concepts/', 'How projects and base paths work'],
  ['Browser', 'Frontend work before the backend exists', 'Import the OpenAPI contract and build against realistic answers on day one, with fake data and CRUD resources that remember.', '/docs/import/', 'Import a contract'],
  ['ArrowBendUpRight', 'Partner APIs and their webhooks', 'Mock the payment provider and its callbacks: answer at once, then call your service back, or wait for a partner\'s reply.', '/docs/webhooks/', 'Webhooks and callouts'],
  ['Lightning', 'Failure and resilience testing', 'Slow answers, dropped connections and a failure rate per stub, so you can prove timeouts and retries before production does.', '/docs/faults/', 'Delays and faults'],
  ['ArrowSquareIn', 'One home for scattered WireMock mocks', 'Move every team\'s mappings into one server with a UI, sign-in and an audit log, and keep every URL they call today.', '/docs/migrate/', 'Moving from WireMock'],
  ['PresentationChart', 'Demos and training that never break', 'Stable data and stateful scenarios that walk through a flow the same way every time, for demos, workshops and support.', '/docs/scenarios/', 'Scenarios'],
];

export function useCases(icon) {
  return USE_CASES.map(([name, title, text, href, link]) => `<article class="uc-card reveal">
          <span class="feature-icon">${icon(name, 22)}</span>
          <h3>${esc(title)}</h3>
          <p>${esc(text)}</p>
          <a class="link-chevron" href="${href}">${esc(link)}</a>
        </article>`).join('\n        ');
}
