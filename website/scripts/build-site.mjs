import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { GROUPS } from '../docs-src/docs.mjs';
import { featureCards, footer as siteFooter, groupAnchor, header as siteHeader, useCases } from '../docs-src/layout.mjs';

const website = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(website, '..');
const out = join(website, 'docs');
const pagesDir = join(website, 'docs-src', 'pages');
const EDIT = 'https://github.com/monk-developer-tools/mockhub/edit/main';
const GENERATED = { helpers: 'ui/src/model/dynamicValues.ts' };

const uiRequire = createRequire(join(repo, 'ui', 'package.json'));
const React = (await import(pathToFileURL(uiRequire.resolve('react')).href)).default;
const { renderToStaticMarkup } = await import(pathToFileURL(uiRequire.resolve('react-dom/server')).href);
const icons = await import(pathToFileURL(join(repo, 'ui', 'node_modules', '@phosphor-icons', 'react', 'dist', 'ssr', 'index.es.js')).href);
const { dynamicValues } = await import(pathToFileURL(join(repo, GENERATED.helpers)).href);

const icon = (name, size = 20) => {
  const component = icons[name];
  if (!component) throw new Error(`Unknown icon ${name}`);
  return renderToStaticMarkup(React.createElement(component, { size, weight: 'duotone', 'aria-hidden': true }));
};

const escape = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const plain = (html) => html.replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/\s+/g, ' ').trim();

function helpersPage() {
  const response = dynamicValues('callout').find((g) => g.id === 'response');
  const groups = dynamicValues('response').flatMap((g) => (g.id === 'request' ? [g, response] : [g]));
  const intro = `<p>These are the helpers behind <strong>Insert dynamic value</strong> in the stub editor. Copy one into a response body, a header or a <a href="#webhooks">webhook</a>. Turn on templating for the response first; the picker does that for you. The <a href="#templating">templating guide</a> explains the syntax.</p>
<p>In a webhook, <code>request</code> becomes <code>originalRequest</code>, and the <strong>Response components</strong> group is available.</p>
`;
  const sections = groups.map((g) => {
    const note = g.id === 'response' ? '<p>Only in webhooks and wait-for-callout calls.</p>\n' : '';
    const rows = g.items.map((item) => `    <tr><td>${escape(item.label)}</td><td><code>${escape(item.expression)}</code></td><td>${item.hint ? escape(item.hint) : ''}</td></tr>`).join('\n');
    return `<h3 id="helpers-${g.id}">${escape(g.name)}</h3>\n${note}<table>\n  <thead><tr><th>Helper</th><th>Expression</th><th>Notes</th></tr></thead>\n  <tbody>\n${rows}\n  </tbody>\n</table>\n`;
  });
  return intro + sections.join('\n');
}

const pages = GROUPS.flatMap((group) => group.pages.map((page) => Object.assign(page, { group })));
for (const page of pages) {
  page.url = `/docs/${page.slug}/`;
  page.source = page.slug === 'helpers' ? helpersPage() : readFileSync(join(pagesDir, `${page.slug}.html`), 'utf8');
  page.headings = [...page.source.matchAll(/<h3 id="([^"]+)">(.*?)<\/h3>/g)].map((m) => ({ id: m[1], title: plain(m[2]) }));
}

const unused = readdirSync(pagesDir).filter((f) => f.endsWith('.html') && !pages.some((p) => `${p.slug}.html` === f));
if (unused.length) throw new Error(`Pages not listed in docs.mjs: ${unused.join(', ')}`);

const anchors = new Map();
for (const page of pages) {
  if (anchors.has(page.slug)) throw new Error(`Duplicate anchor ${page.slug}`);
  anchors.set(page.slug, page.url);
}
for (const page of pages) {
  for (const h of page.headings) {
    if (anchors.has(h.id)) throw new Error(`Heading id ${h.id} on ${page.slug} clashes with another page or heading`);
    anchors.set(h.id, `${page.url}#${h.id}`);
  }
}

const problems = [];
function rewrite(page) {
  const own = new Set(page.headings.map((h) => h.id));
  return page.source.replace(/href="(\/docs\/)?#([^"]+)"/g, (match, prefix, id) => {
    if (!prefix && own.has(id)) return match;
    const target = anchors.get(id);
    if (!target) {
      problems.push(`${page.slug}: link to unknown #${id}`);
      return match;
    }
    return `href="${target}"`;
  });
}

const header = () => `  ${siteHeader(icon, 'docs')}`;

const footer = `  ${siteFooter(icon)}
  <script src="/assets/js/site.js" defer></script>
  <script src="/assets/js/docs.js" defer></script>`;

const head = (title, description, extra = '') => `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escape(title)}</title>
  <meta name="description" content="${escape(description)}">
  <link rel="icon" href="/favicon.svg" type="image/svg+xml">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="/assets/css/site.css">
${extra}</head>`;

const search = (big = false) => `<div class="docs-search${big ? ' big' : ''}" role="search">
        <div class="docs-search-field">
          ${icon('MagnifyingGlass', big ? 20 : 16)}
          <input type="search" placeholder="Search the docs" aria-label="Search the docs" autocomplete="off" spellcheck="false">
          <kbd>/</kbd>
        </div>
        <div class="docs-results" role="listbox" aria-label="Search results" hidden></div>
      </div>`;

function sidebar(current) {
  const groups = GROUPS.map((g) => {
    const links = g.pages.map((p) => `<a href="${p.url}"${p.slug === current ? ' aria-current="page" class="active"' : ''}>${escape(p.title)}</a>`).join('\n          ');
    return `<div class="docs-nav-group">
          <h4>${icon(g.icon, 16)}${escape(g.title)}</h4>
          ${links}
        </div>`;
  }).join('\n        ');
  return `<aside class="docs-nav" aria-label="Docs navigation">
      <details class="docs-menu" open>
        <summary>Browse the docs</summary>
        ${search()}
        <a class="docs-home" href="/docs/">${icon('House', 16)}Overview</a>
        ${groups}
      </details>
    </aside>`;
}

function pager(index) {
  const prev = pages[index - 1];
  const next = pages[index + 1];
  const card = (p, label, cls) => (p ? `<a class="docs-pager-card docs-pager-${cls}" href="${p.url}"><span>${label}</span><strong>${escape(p.title)}</strong></a>` : '<span></span>');
  return `<nav class="docs-pager" aria-label="Previous and next">${card(prev, 'Previous', 'prev')}${card(next, 'Next', 'next')}</nav>`;
}

function toc(page) {
  if (page.headings.length < 2) return '';
  const links = page.headings.map((h) => `<a href="#${h.id}">${escape(h.title)}</a>`).join('\n        ');
  return `<aside class="docs-toc" aria-label="On this page">
      <h4>On this page</h4>
      <nav>
        ${links}
      </nav>
    </aside>`;
}

function renderPage(page, index) {
  const edit = `${EDIT}/${GENERATED[page.slug] ?? `website/docs-src/pages/${page.slug}.html`}`;
  const body = rewrite(page).trimEnd().split('\n').join('\n      ');
  return `${head(`${page.title} — MockHub docs`, page.description)}
<body>
${header()}

  <div class="wrap docs-layout${page.headings.length < 2 ? ' no-toc' : ''}">
    ${sidebar(page.slug)}

    <article class="prose docs-page" id="content">
      <nav class="docs-crumbs" aria-label="Breadcrumb"><a href="/docs/">Docs</a><span aria-hidden="true">›</span><span>${escape(page.group.title)}</span></nav>
      <div class="docs-title">
        <span class="docs-title-icon">${icon(page.icon, 26)}</span>
        <h1>${escape(page.title)}</h1>
      </div>
      <p class="lede">${escape(page.description)}</p>
      ${body}
      <div class="docs-edit"><a href="${edit}">${icon('PencilSimple', 15)}Edit this page on GitHub</a></div>
      ${pager(index)}
    </article>

    ${toc(page)}
  </div>

${footer}
</body>
</html>
`;
}

function renderLanding() {
  const redirect = `  <script>
    (function () {
      var map = ${JSON.stringify(Object.fromEntries(anchors))};
      var id = location.hash.slice(1);
      if (map[id]) location.replace(map[id]);
    })();
  </script>
`;
  const groups = GROUPS.map((g) => {
    const cards = g.pages.map((p) => `<a class="doc-card" href="${p.url}">
            <span class="doc-card-icon">${icon(p.icon, 22)}</span>
            <strong>${escape(p.title)}</strong>
            <span>${escape(p.description)}</span>
            <span class="doc-card-arrow" aria-hidden="true">→</span>
          </a>`).join('\n          ');
    return `<section class="docs-group" id="${groupAnchor(g)}" aria-labelledby="${g.id}-title">
        <header>
          <span class="docs-group-icon">${icon(g.icon, 22)}</span>
          <div>
            <h2 id="${g.id}-title">${escape(g.title)}</h2>
            <p>${escape(g.description)}</p>
          </div>
          <span class="docs-group-count">${g.pages.length} ${g.pages.length === 1 ? 'guide' : 'guides'}</span>
        </header>
        <div class="doc-cards">
          ${cards}
        </div>
      </section>`;
  }).join('\n\n      ');
  return `${head('Docs — MockHub', 'How to run MockHub, mock REST and SOAP services, send webhooks, share mocks with your team, automate it from CI and deploy it.', redirect)}
<body>
${header()}

  <main id="content" class="docs-landing">
    <section class="docs-hero">
      <div class="wrap">
        <p class="eyebrow">Product documentation</p>
        <h1>MockHub documentation</h1>
        <p class="lede">Run MockHub, mock REST and SOAP services, call back with webhooks, share mocks with your team and run it in your test environments.</p>
        ${search(true)}
        <div class="docs-quick">
          <a href="/docs/quick-start/">${icon('Lightning', 16)}Quick start</a>
          <a href="/tutorials/">${icon('GraduationCap', 16)}Step-by-step tutorials</a>
          <a href="/docs/helpers/">${icon('ListMagnifyingGlass', 16)}Helper reference</a>
          <a href="/docs/api/">${icon('TerminalWindow', 16)}API &amp; CI</a>
          <a href="/docs/error-codes/">${icon('WarningCircle', 16)}Error codes</a>
        </div>
      </div>
    </section>

    <div class="wrap docs-groups">
      ${groups}
    </div>
  </main>

${footer}
</body>
</html>
`;
}

const html = pages.map((page, i) => [page, renderPage(page, i)]);
if (problems.length) throw new Error(`Broken docs links:\n  ${problems.join('\n  ')}`);

rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
writeFileSync(join(out, 'index.html'), renderLanding());
for (const [page, content] of html) {
  mkdirSync(join(out, page.slug), { recursive: true });
  writeFileSync(join(out, page.slug, 'index.html'), content);
}
writeFileSync(join(out, 'search.json'), JSON.stringify(pages.map((p) => ({
  title: p.title,
  group: p.group.title,
  url: p.url,
  description: p.description,
  headings: p.headings.map((h) => ({ title: h.title, url: `${p.url}#${h.id}` })),
  text: plain(p.source),
}))));

const blocks = {
  header: (current) => siteHeader(icon, current),
  footer: () => siteFooter(icon),
  features: () => featureCards(icon),
  usecases: () => useCases(icon),
};
const inject = (file, current) => {
  const path = join(website, file);
  const before = readFileSync(path, 'utf8');
  const after = before.replace(/(<!-- site:(\w+) -->)[\s\S]*?(<!-- \/site:\2 -->)/g, (match, open, name, close) => {
    if (!blocks[name]) throw new Error(`Unknown block ${name} in ${file}`);
    const indent = before.slice(0, before.indexOf(match)).split('\n').pop();
    return `${open}\n${indent}${blocks[name](current)}\n${indent}${close}`;
  });
  if (after !== before) writeFileSync(path, after);
};
inject('index.html', 'home');
inject(join('tutorials', 'index.html'), 'tutorials');

const groupAnchors = new Set(GROUPS.map(groupAnchor));
const siteFiles = [join(website, 'index.html'), join(website, 'tutorials', 'index.html'), ...pages.map((p) => join(out, p.slug, 'index.html'))];
const broken = [];
for (const file of siteFiles) {
  const content = readFileSync(file, 'utf8');
  for (const [, href] of content.matchAll(/href="([^"]*)"/g)) {
    if (!/^(\/|#|https?:\/\/|mailto:)/.test(href)) broken.push(`${file.replace(website, '')}: ${href || '(empty)'}`);
  }
  for (const [, path, hash] of content.matchAll(/href="(\/docs\/[^"#]*)(?:#([^"]*))?"/g)) {
    const target = join(website, path, path.endsWith('/') ? 'index.html' : '');
    if (!existsSync(target)) broken.push(`${file.replace(website, '')}: ${path}`);
    else if (hash && path !== '/docs/' && !readFileSync(target, 'utf8').includes(`id="${hash}"`)) broken.push(`${file.replace(website, '')}: ${path}#${hash}`);
    else if (hash && path === '/docs/' && !anchors.has(hash) && !groupAnchors.has(hash)) broken.push(`${file.replace(website, '')}: /docs/#${hash}`);
  }
}
if (broken.length) throw new Error(`Links to missing docs pages:\n  ${broken.join('\n  ')}`);
console.log(`Built ${pages.length} docs pages and the landing page into ${out.replace(repo + '/', '')}`);
