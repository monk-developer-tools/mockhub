const escape = (text) => String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export function inline(text) {
  return escape(text).replace(/`([^`]+)`/g, '<code>$1</code>');
}

export function parseChangelog(markdown) {
  const releases = [];
  for (const line of String(markdown).split('\n')) {
    const heading = /^## (\d{4}-\d{2}-\d{2})\s*$/.exec(line);
    if (heading) {
      releases.push({ date: heading[1], items: [] });
      continue;
    }
    const item = /^- (.+)$/.exec(line);
    if (item && releases.length > 0) releases[releases.length - 1].items.push(item[1].trim());
  }
  return releases.filter((r) => r.items.length > 0);
}

export function longDate(iso) {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
}

export function changelogSections(markdown) {
  return parseChangelog(markdown).map((r) => ({
    id: `v${r.date}`,
    title: longDate(r.date),
    html: `<ul>${r.items.map((i) => `<li>${inline(i)}</li>`).join('')}</ul>`,
  }));
}

export function faqSections(pages) {
  const seen = new Set();
  return pages.filter((p) => Array.isArray(p.faq) && p.faq.length > 0).map((p) => {
    const items = p.faq.filter(([q]) => {
      const key = q.trim().toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
    return {
      id: p.slug.replace(/\//g, '-'),
      title: p.nav[0],
      html: `${items.map(([q, a]) => `<details class="prose-faq"><summary>${inline(q)}</summary><p>${inline(a)}</p></details>`).join('')}<p class="prose-more"><a href="/${p.slug}/">More on ${escape(p.nav[0])}</a></p>`,
      count: items.length,
    };
  }).filter((s) => s.count > 0);
}
