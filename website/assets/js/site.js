(function () {
  const toggle = document.querySelector('.nav-toggle');
  const links = document.querySelector('.nav-links');
  if (toggle && links) {
    toggle.addEventListener('click', () => {
      const open = links.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));
    });
  }

  document.querySelectorAll('.code').forEach((block) => {
    const pre = block.querySelector('pre');
    if (!pre || block.dataset.nocopy !== undefined) return;
    const button = document.createElement('button');
    button.className = 'copy';
    button.type = 'button';
    button.textContent = 'Copy';
    button.setAttribute('aria-label', 'Copy code');
    button.addEventListener('click', async () => {
      const text = pre.innerText.replace(/^\$ /gm, '');
      try {
        await navigator.clipboard.writeText(text);
      } catch (e) {
        const area = document.createElement('textarea');
        area.value = text;
        document.body.appendChild(area);
        area.select();
        document.execCommand('copy');
        area.remove();
      }
      button.textContent = 'Copied';
      setTimeout(() => (button.textContent = 'Copy'), 1500);
    });
    block.appendChild(button);
  });

  document.querySelectorAll('[role="tablist"]').forEach((list) => {
    const tabs = Array.from(list.querySelectorAll('[role="tab"]'));
    const select = (tab) => {
      tabs.forEach((t) => {
        const selected = t === tab;
        t.setAttribute('aria-selected', String(selected));
        t.tabIndex = selected ? 0 : -1;
        document.getElementById(t.getAttribute('aria-controls')).hidden = !selected;
      });
    };
    tabs.forEach((tab, i) => {
      tab.addEventListener('click', () => select(tab));
      tab.addEventListener('keydown', (e) => {
        if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
        const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
        next.focus();
        select(next);
      });
    });
  });

  const reveals = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && reveals.length) {
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((e) => e.isIntersecting && (e.target.classList.add('in'), observer.unobserve(e.target))),
      { threshold: 0.15 },
    );
    reveals.forEach((el) => observer.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add('in'));
  }

  const docLinks = document.querySelectorAll('.docs aside a[href^="#"]');
  if (docLinks.length && 'IntersectionObserver' in window) {
    const byId = new Map(Array.from(docLinks).map((a) => [a.getAttribute('href').slice(1), a]));
    const spy = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (!e.isIntersecting) return;
          docLinks.forEach((a) => a.classList.remove('active'));
          byId.get(e.target.id)?.classList.add('active');
        });
      },
      { rootMargin: '-20% 0px -70% 0px' },
    );
    byId.forEach((_, id) => {
      const el = document.getElementById(id);
      if (el) spy.observe(el);
    });
  }
})();
