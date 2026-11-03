(function () {
  const boxes = Array.from(document.querySelectorAll('.docs-search'));
  if (!boxes.length) return;

  let index;
  const load = () => (index ??= fetch('/docs/search.json').then((r) => r.json()).catch(() => []));

  const esc = (s) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const mark = (text, words) => {
    let html = esc(text);
    words.forEach((w) => {
      html = html.replace(new RegExp(`(${w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi'), '<mark>$1</mark>');
    });
    return html;
  };
  const snippet = (text, words) => {
    const lower = text.toLowerCase();
    const at = Math.max(0, Math.min(...words.map((w) => lower.indexOf(w)).filter((i) => i >= 0)) - 40);
    return (at > 0 ? '…' : '') + text.slice(at, at + 140) + '…';
  };

  const search = (pages, query) => {
    const words = query.toLowerCase().split(/\s+/).filter(Boolean);
    if (!words.length) return [];
    const hits = [];
    pages.forEach((p) => {
      const title = p.title.toLowerCase();
      const text = `${p.description} ${p.text}`.toLowerCase();
      if (!words.every((w) => title.includes(w) || text.includes(w) || p.headings.some((h) => h.title.toLowerCase().includes(w)))) return;
      const score = words.reduce((s, w) => s + (title.includes(w) ? 10 : 0) + (p.description.toLowerCase().includes(w) ? 3 : 0), 0);
      hits.push({ score, title: p.title, url: p.url, sub: p.group, body: snippet(`${p.description} ${p.text}`, words) });
      p.headings.forEach((h) => {
        const t = h.title.toLowerCase();
        if (words.every((w) => t.includes(w))) hits.push({ score: score + 6, title: h.title, url: h.url, sub: `${p.group} › ${p.title}` });
      });
    });
    return hits.sort((a, b) => b.score - a.score).slice(0, 12).map((h) => ({ ...h, words }));
  };

  boxes.forEach((box) => {
    const input = box.querySelector('input');
    const list = box.querySelector('.docs-results');
    let active = -1;

    const items = () => Array.from(list.querySelectorAll('a'));
    const select = (i) => {
      const all = items();
      active = (i + all.length) % all.length;
      all.forEach((a, j) => a.setAttribute('aria-selected', String(j === active)));
      all[active]?.scrollIntoView({ block: 'nearest' });
    };
    const close = () => {
      list.hidden = true;
      active = -1;
    };
    const render = async () => {
      const query = input.value.trim();
      if (!query) return close();
      const hits = search(await load(), query);
      list.innerHTML = hits.length
        ? hits.map((h) => `<a role="option" href="${h.url}"><strong>${mark(h.title, h.words)}</strong><small>${esc(h.sub)}</small>${h.body ? `<small>${mark(h.body, h.words)}</small>` : ''}</a>`).join('')
        : `<p>No results for “${esc(query)}”.</p>`;
      list.hidden = false;
      active = -1;
      if (hits.length) select(0);
    };

    input.addEventListener('focus', load);
    input.addEventListener('input', render);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (list.hidden) return;
        e.preventDefault();
        select(active + (e.key === 'ArrowDown' ? 1 : -1));
      } else if (e.key === 'Enter') {
        const target = items()[active];
        if (target) {
          e.preventDefault();
          location.href = target.href;
        }
      } else if (e.key === 'Escape') {
        input.value ? (input.value = '') : input.blur();
        close();
      }
    });
    document.addEventListener('click', (e) => {
      if (!box.contains(e.target)) close();
    });
  });

  document.addEventListener('keydown', (e) => {
    if (e.key !== '/' || e.metaKey || e.ctrlKey || e.altKey) return;
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName ?? '')) return;
    const visible = boxes.find((b) => b.offsetParent !== null);
    if (!visible) return;
    e.preventDefault();
    visible.querySelector('input').focus();
  });

  const menu = document.querySelector('.docs-menu');
  if (menu && window.matchMedia('(max-width: 900px)').matches) menu.open = false;

  const nav = document.querySelector('.docs-nav');
  const current = nav?.querySelector('a.active');
  if (current && nav.scrollHeight > nav.clientHeight) nav.scrollTop = current.offsetTop - nav.clientHeight / 2;

  const tocLinks = document.querySelectorAll('.docs-toc nav a');
  if (tocLinks.length && 'IntersectionObserver' in window) {
    const byId = new Map(Array.from(tocLinks).map((a) => [a.getAttribute('href').slice(1), a]));
    const spy = new IntersectionObserver(
      (entries) => entries.forEach((e) => {
        if (!e.isIntersecting) return;
        tocLinks.forEach((a) => a.classList.remove('active'));
        byId.get(e.target.id)?.classList.add('active');
      }),
      { rootMargin: '-10% 0px -75% 0px' },
    );
    byId.forEach((_, id) => {
      const el = document.getElementById(id);
      if (el) spy.observe(el);
    });
  }
})();
