import { DEMO, MENUS, PAGES } from './pages.mjs';

export const SITE = 'https://mockhub.devops-monk.com';
const LIVE = 'mockhub.devops-monk.com';

export const esc = (text) => String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const bySlug = new Map(PAGES.map((p) => [p.slug, p]));
const menuOf = (id) => MENUS.find((m) => m.id === id);

const json = (value) => JSON.stringify(value).replace(/</g, '\\u003c');

export function head({ title, description, path, extra = '' }) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${esc(title)}</title>
  <meta name="description" content="${esc(description)}">
  <link rel="canonical" href="${SITE}${path}">
  <meta name="theme-color" content="#ffffff" media="(prefers-color-scheme: light)">
  <meta name="theme-color" content="#000000" media="(prefers-color-scheme: dark)">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="MockHub">
  <meta property="og:title" content="${esc(title)}">
  <meta property="og:description" content="${esc(description)}">
  <meta property="og:url" content="${SITE}${path}">
  <meta property="og:image" content="${SITE}/assets/img/og.jpg">
  <meta name="twitter:card" content="summary_large_image">
  <link rel="icon" href="/favicon.svg" type="image/svg+xml">
  <link rel="stylesheet" href="/assets/css/site.css">
${extra}</head>`;
}

function picture({ pair, single, alt }, eager = false) {
  const load = eager ? 'fetchpriority="high"' : 'loading="lazy"';
  if (single) return `<img src="${single}" alt="${esc(alt)}" width="1176" height="900" ${load}>`;
  const base = `/assets/img/shots/${pair}`;
  return `<picture>
            <source media="(prefers-color-scheme: dark)" srcset="${base}-dark.webp 1440w, ${base}-dark@2x.webp 2880w" sizes="(max-width: 1180px) 94vw, 1100px">
            <img src="${base}-light.webp" srcset="${base}-light.webp 1440w, ${base}-light@2x.webp 2880w" sizes="(max-width: 1180px) 94vw, 1100px" alt="${esc(alt)}" width="1440" height="900" ${load}>
          </picture>`;
}

const frame = (image, eager) => `<div class="fp-frame">
          <div class="window-bar" aria-hidden="true"><span></span><span></span><span></span><div class="address">${LIVE}</div></div>
          ${picture(image, eager)}
        </div>`;

function tryCard(examples) {
  const [first] = examples;
  const tabs = examples.map(([label, method, path, type, body], i) => {
    const attrs = [`data-method="${method}"`, `data-path="${esc(path)}"`];
    if (type) attrs.push(`data-type="${esc(type)}"`);
    if (body) attrs.push(`data-body="${esc(body)}"`);
    return `<button type="button" aria-pressed="${i === 0}" ${attrs.join(' ')}>${esc(label)}</button>`;
  }).join('\n              ');
  return `<div class="try-card" data-try aria-label="Call a live mock">
          <div class="try-head">
            <span class="try-dot" aria-hidden="true"></span>
            <strong>Call the live demo</strong>
            <div class="try-tabs" role="group" aria-label="Example">
              ${tabs}
            </div>
          </div>
          <div class="try-url">
            <span class="jack ${first[1].toLowerCase()}" data-try-method>${first[1]}</span>
            <code data-try-path>${LIVE}${esc(first[2])}</code>
            <button class="try-send" type="button" data-try-send>Send</button>
          </div>
          <pre class="try-req" data-try-req${first[4] ? '' : ' hidden'}>${first[4] ? esc(first[4]) : ''}</pre>
          <div class="try-res" aria-live="polite">
            <div class="try-meta">
              <span class="try-status" data-try-status>Ready</span>
              <span data-try-time></span>
              <span class="try-stub" data-try-stub></span>
            </div>
            <pre data-try-body>Press Send to call the public demo.</pre>
          </div>
          <p class="try-foot">These calls go to the public demo and show up in its request log.</p>
        </div>`;
}

const WORKS = ['OpenAPI 3', 'Swagger 2', 'WSDL &amp; SOAP', 'WireMock', 'Postman', 'Bruno', 'Docker', 'Kubernetes', 'Microsoft Entra ID'];

function hero(page, icon) {
  const menu = menuOf(page.menu);
  const visual = page.hero?.try ? tryCard(page.hero.try) : page.hero?.shot ? frame(page.hero.shot, true) : '';
  const tutorial = page.tutorial ? `<a class="link-chevron" href="${page.tutorial}">Follow the tutorial</a>` : '';
  return `<section class="hero fp-hero">
      <div class="wrap hero-grid${visual ? '' : ' fp-solo'}">
        <div class="hero-copy">
          <p class="eyebrow">${icon(page.icon, 16)}${esc(menu.title)}</p>
          <h1>${esc(page.h1)}</h1>
          <p class="lede">${esc(page.lede)}</p>
          <div class="ctas">
            <a class="btn primary" href="${DEMO}">Try it in the live demo</a>
            <a class="btn secondary" href="${page.docs}">Read the docs</a>
          </div>
          <p class="fine">Free and open source · self-hosted · built on WireMock${tutorial ? ` · ${tutorial}` : ''}</p>
        </div>
        ${visual}
      </div>
      <div class="works wrap" aria-label="Works with">
        <span class="works-label">Works with</span>
        <ul>
          ${WORKS.map((w) => `<li>${w}</li>`).join('\n          ')}
        </ul>
      </div>
    </section>`;
}

const steps = (page) => `<section class="alt" aria-labelledby="how">
      <div class="wrap">
        <div class="section-head reveal">
          <p class="eyebrow">How it works</p>
          <h2 id="how">Three steps</h2>
        </div>
        <ol class="steps">
          ${page.steps.map(([title, text], i) => `<li class="step reveal">
            <span class="step-n">${i + 1}</span>
            <h3>${esc(title)}</h3>
            <p>${esc(text)}</p>
          </li>`).join('\n          ')}
        </ol>
        ${page.shot ? `<figure class="fp-shot reveal">
        ${frame(page.shot)}
          <figcaption>${esc(page.shot.alt)}</figcaption>
        </figure>` : ''}
      </div>
    </section>`;

const problem = (page, icon) => `<section aria-labelledby="problem">
      <div class="wrap">
        <div class="section-head reveal">
          <p class="eyebrow">The problem</p>
          <h2 id="problem">${esc(page.problem.title)}</h2>
        </div>
        <div class="fp-pains">
          ${page.problem.items.map(([title, text]) => `<article class="fp-pain reveal">
            ${icon('WarningCircle', 22)}
            <h3>${esc(title)}</h3>
            <p>${esc(text)}</p>
          </article>`).join('\n          ')}
        </div>
      </div>
    </section>`;

const benefits = (page, icon) => `<section class="alt" aria-labelledby="benefits">
      <div class="wrap fp-wide">
        <div class="section-head reveal">
          <p class="eyebrow">What you get</p>
          <h2 id="benefits">${esc(page.benefits.title)}</h2>
        </div>
        <div class="fp-benefits ${page.benefits.items.length % 4 === 0 ? 'cols-4' : 'cols-3'}">
          ${page.benefits.items.map(([name, title, text, href]) => `<a class="fp-benefit reveal" href="${href}">
            <span class="feature-icon">${icon(name, 22)}</span>
            <h3>${esc(title)}</h3>
            <p>${esc(text)}</p>
            <span class="fp-more" aria-hidden="true">Learn more</span>
          </a>`).join('\n          ')}
        </div>
      </div>
    </section>`;

const example = (page) => `<section aria-labelledby="example">
      <div class="wrap fp-example">
        <div class="reveal">
          <p class="eyebrow">Example</p>
          <h2 id="example">${esc(page.example.title)}</h2>
          <p class="body-lg">${esc(page.example.text)}</p>
          <p class="fp-example-links"><a class="link-chevron" href="${page.docs}">Read the docs</a></p>
        </div>
        <div class="code reveal"><pre>${esc(page.example.code)}</pre></div>
      </div>
    </section>`;

const faq = (page) => `<section class="alt" aria-labelledby="faq">
      <div class="wrap narrow">
        <div class="section-head reveal">
          <p class="eyebrow">FAQ</p>
          <h2 id="faq">Questions people ask</h2>
        </div>
        <div class="fp-faq">
          ${page.faq.map(([q, a]) => `<details class="reveal">
            <summary>${esc(q)}</summary>
            <p>${esc(a)}</p>
          </details>`).join('\n          ')}
        </div>
      </div>
    </section>`;

const related = (page, icon) => `<section aria-labelledby="related">
      <div class="wrap">
        <div class="section-head reveal">
          <h2 id="related">Keep exploring</h2>
        </div>
        <div class="fp-related">
          ${page.related.map((slug) => bySlug.get(slug)).map((p) => `<a class="fp-benefit reveal" href="/${p.slug}/">
            <span class="feature-icon">${icon(p.icon, 22)}</span>
            <h3>${esc(p.nav[0])}</h3>
            <p>${esc(p.nav[1])}</p>
            <span class="fp-more" aria-hidden="true">Open</span>
          </a>`).join('\n          ')}
        </div>
      </div>
    </section>`;

const closing = (page) => `<section class="dark cta-band">
      <div class="wrap narrow center">
        <img class="cta-logo" src="/assets/img/logo.svg" alt="" width="64" height="64">
        <h2>Try it now. No install needed.</h2>
        <p>${esc(page.definition)}</p>
        <div class="ctas">
          <a class="btn primary" href="${DEMO}">Open the live demo</a>
          <a class="link-chevron" href="/docs/quick-start/">Run it yourself in a minute</a>
        </div>
      </div>
    </section>`;

function examplesGrid(examples, icon) {
  const cards = examples.map((e) => {
    const url = `${LIVE}/${e.slug}`;
    const routes = e.stubs.slice(0, 4).map((s) => `<li><span class="jack ${s.method.toLowerCase()}">${esc(s.method)}</span><code>${esc(s.path)}</code></li>`).join('');
    const call = e.call ? `<a class="btn primary small" href="https://${url}${esc(e.call)}">Call ${esc(e.call)}</a>` : '';
    return `<article class="fp-example-card reveal" id="${esc(e.id)}">
            <header>
              <h3>${esc(e.name)}</h3>
              <ul class="fp-tags">${e.tags.map((t) => `<li>${esc(t)}</li>`).join('')}</ul>
            </header>
            <p>${esc(e.description)}</p>
            <code class="fp-base">https://${esc(url)}</code>
            <ul class="fp-routes">${routes}</ul>
            <div class="fp-card-actions">${call}<a class="link-chevron" href="${DEMO}">Open in the demo</a></div>
          </article>`;
  }).join('\n          ');
  return `<section class="alt" aria-labelledby="all-examples">
      <div class="wrap fp-wide">
        <div class="section-head reveal">
          <p class="eyebrow">${icon('Books', 16)}Live on the demo</p>
          <h2 id="all-examples">${examples.length} example projects</h2>
          <p>Sign in to the demo as demo / demo to open any of them, or install them on your own server from the Examples page.</p>
        </div>
        <div class="fp-examples">
          ${cards}
        </div>
      </div>
    </section>`;
}

const SCRIPTS = {
  jwt: 'jwt-page.js',
  'curl-to-stub': 'curl-page.js',
  'stub-checker': 'stubcheck-page.js',
  'http-echo': 'echo-page.js',
  'webhook-bin': 'bin-page.js',
  'template-playground': 'playground-page.js',
  'fake-data': 'fake-page.js',
};

const BROWSER_NOTE = 'Runs in your browser. Nothing you paste is sent anywhere.';

function toolHero(page, icon) {
  return `<section class="hero fp-hero tool-hero">
      <div class="wrap fp-wide">
        <p class="eyebrow">${icon(page.icon, 16)}${page.kind === 'tool' ? 'Free tool' : 'Free tools'} · no login</p>
        <h1>${esc(page.h1)}</h1>
        <p class="lede">${esc(page.lede)}</p>
        <p class="tool-private">${icon(page.server ? 'CloudCheck' : 'ShieldCheck', 16)}${esc(page.privacy ?? BROWSER_NOTE)}</p>
      </div>
    </section>`;
}

const catalogueScript = (data) => `<script type="application/json" id="tool-catalogue">${JSON.stringify(data).replace(/</g, '\\u003c')}</script>`;

const toolBody = (page, ctx) => `<section class="tool-section" aria-label="${esc(page.nav[0])}">
      <div class="wrap fp-wide">
        ${ctx.tools[page.tool].replace('<!-- catalogue -->', () => catalogueScript(ctx.catalogues[page.tool] ?? [])).trim().split('\n').join('\n        ')}
      </div>
    </section>`;

const toolSteps = (page) => `<section class="alt" aria-labelledby="how">
      <div class="wrap">
        <div class="section-head reveal">
          <p class="eyebrow">How to use it</p>
          <h2 id="how">Three steps</h2>
        </div>
        <ol class="steps">
          ${page.steps.map(([title, text], i) => `<li class="step reveal">
            <span class="step-n">${i + 1}</span>
            <h3>${esc(title)}</h3>
            <p>${esc(text)}</p>
          </li>`).join('\n          ')}
        </ol>
      </div>
    </section>`;

function toolsIndex(page, icon) {
  const group = (server, id, eyebrow, title, alt) => {
    const list = PAGES.filter((p) => p.kind === 'tool' && Boolean(p.server) === server);
    const cards = list.map((p) => `<a class="fp-benefit reveal" href="/${p.slug}/">
            <span class="feature-icon">${icon(p.icon, 22)}</span>
            <h3>${esc(p.nav[0])}</h3>
            <p>${esc(p.description)}</p>
            <span class="fp-more" aria-hidden="true">Open the tool</span>
          </a>`).join('\n          ');
    return `<section${alt ? ' class="alt"' : ''} aria-labelledby="${id}">
      <div class="wrap fp-wide">
        <div class="section-head reveal">
          <p class="eyebrow">${icon(server ? 'CloudCheck' : 'ShieldCheck', 16)}${esc(eyebrow)}</p>
          <h2 id="${id}">${esc(title)}</h2>
        </div>
        <div class="fp-benefits cols-${list.length % 4 === 0 ? 4 : 3}">
          ${cards}
        </div>
      </div>
    </section>`;
  };
  return [
    group(true, 'server-tools', 'On the MockHub demo server', 'Send real requests', true),
    group(false, 'browser-tools', 'In your browser, nothing sent', 'Work with stubs and tokens', false),
  ].join('\n\n    ');
}

function structured(page) {
  const crumbs = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'MockHub', item: `${SITE}/` },
      ...(page.slug.startsWith('tools/') ? [{ '@type': 'ListItem', position: 2, name: 'Free tools', item: `${SITE}/tools/` }] : []),
      { '@type': 'ListItem', position: page.slug.startsWith('tools/') ? 3 : 2, name: page.nav[0], item: `${SITE}/${page.slug}/` },
    ],
  };
  const blocks = [crumbs];
  if (page.kind === 'tool') {
    blocks.push({
      '@context': 'https://schema.org',
      '@type': 'WebApplication',
      name: page.nav[0],
      url: `${SITE}/${page.slug}/`,
      applicationCategory: 'DeveloperApplication',
      operatingSystem: 'Any (runs in the browser)',
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
    });
  }
  if (page.faq) {
    blocks.push({
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: page.faq.map(([q, a]) => ({ '@type': 'Question', name: q, acceptedAnswer: { '@type': 'Answer', text: a } })),
    });
  }
  return blocks.map((b) => `  <script type="application/ld+json">${json(b)}</script>\n`).join('');
}

const LAYOUTS = {
  examples: (page, ctx) => [hero(page, ctx.icon), examplesGrid(ctx.examples, ctx.icon), related(page, ctx.icon), closing(page)],
  'tools-index': (page, ctx) => [toolHero(page, ctx.icon), toolsIndex(page, ctx.icon), related(page, ctx.icon), closing(page)],
  tool: (page, ctx) => [toolHero(page, ctx.icon), toolBody(page, ctx), toolSteps(page), faq(page), related(page, ctx.icon), closing(page)],
  feature: (page, ctx) => [hero(page, ctx.icon), steps(page), problem(page, ctx.icon), benefits(page, ctx.icon), example(page), faq(page), related(page, ctx.icon), closing(page)],
};

export function renderFeature(page, ctx) {
  const { header, footer } = ctx;
  const sections = LAYOUTS[page.kind ?? 'feature'](page, ctx);
  const script = page.tool ? `\n  <script type="module" src="/assets/js/tools/${SCRIPTS[page.tool]}"></script>` : '';
  return `${head({ title: page.title, description: page.description, path: `/${page.slug}/`, extra: structured(page) })}
<body>
  ${header}

  <main id="content" class="fp">
    ${sections.join('\n\n    ')}
  </main>

  ${footer}
  <script src="/assets/js/site.js" defer></script>${script}
</body>
</html>
`;
}
