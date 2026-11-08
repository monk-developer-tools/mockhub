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
        const panel = document.getElementById(t.getAttribute('aria-controls'));
        if (panel) panel.hidden = !selected;
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

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const device = document.querySelector('[data-zoom]');
  if (device && !reduceMotion) {
    let ticking = false;
    const update = () => {
      const rect = device.getBoundingClientRect();
      const start = window.innerHeight;
      const end = window.innerHeight * 0.35;
      const progress = Math.min(1, Math.max(0, (start - rect.top) / (start - end)));
      device.style.setProperty('--tilt', String(1 - progress));
      ticking = false;
    };
    window.addEventListener('scroll', () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    }, { passive: true });
    update();
  } else if (device) {
    device.style.setProperty('--tilt', '0');
  }

  const gallery = document.querySelector('.gallery');
  if (gallery) {
    const cards = Array.from(gallery.querySelectorAll('.card'));
    const prev = document.querySelector('[data-gallery="prev"]');
    const next = document.querySelector('[data-gallery="next"]');
    const dots = document.querySelector('.gallery-dots');
    const step = () => cards[0].getBoundingClientRect().width + 20;
    const current = () => Math.round(gallery.scrollLeft / step());
    const go = (index) => gallery.scrollTo({ left: index * step(), behavior: reduceMotion ? 'auto' : 'smooth' });
    const buttons = cards.map((card, i) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('aria-label', `Show highlight ${i + 1}`);
      b.tabIndex = -1;
      b.addEventListener('click', () => go(i));
      dots.appendChild(b);
      return b;
    });
    const sync = () => {
      const i = Math.min(cards.length - 1, current());
      buttons.forEach((b, j) => b.setAttribute('aria-current', String(i === j)));
      if (prev) prev.disabled = gallery.scrollLeft <= 4;
      if (next) next.disabled = gallery.scrollLeft + gallery.clientWidth >= gallery.scrollWidth - 4;
    };
    prev?.addEventListener('click', () => go(Math.max(0, current() - 1)));
    next?.addEventListener('click', () => go(Math.min(cards.length - 1, current() + 1)));
    gallery.addEventListener('scroll', () => requestAnimationFrame(sync), { passive: true });
    gallery.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight') go(Math.min(cards.length - 1, current() + 1));
      if (e.key === 'ArrowLeft') go(Math.max(0, current() - 1));
    });
    sync();
  }

  const shots = document.querySelectorAll('.tut-shot img');
  if (shots.length && typeof HTMLDialogElement === 'function') {
    const box = document.createElement('dialog');
    box.className = 'lightbox';
    box.setAttribute('aria-label', 'Screenshot');
    const big = document.createElement('img');
    box.appendChild(big);
    document.body.appendChild(box);
    box.addEventListener('click', () => box.close());
    shots.forEach((img) => {
      img.tabIndex = 0;
      img.setAttribute('role', 'button');
      const open = () => {
        big.src = img.currentSrc || img.src;
        big.alt = img.alt;
        box.showModal();
      };
      img.addEventListener('click', open);
      img.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          open();
        }
      });
    });
  }

  const hover = window.matchMedia('(hover: hover) and (min-width: 961px)');
  const menus = Array.from(document.querySelectorAll('.nav-menu')).map((menu) => {
    const trigger = menu.querySelector('.nav-menu-trigger');
    const mega = menu.querySelector('.mega');
    let timer;
    const setOpen = (open) => {
      clearTimeout(timer);
      mega.hidden = !open;
      trigger.setAttribute('aria-expanded', String(open));
    };
    return { menu, trigger, mega, setOpen, later: () => (timer = setTimeout(() => setOpen(false), 160)) };
  });
  menus.forEach((m) => {
    m.trigger.addEventListener('click', () => {
      const open = m.mega.hidden;
      menus.forEach((other) => other !== m && other.setOpen(false));
      m.setOpen(open);
    });
    m.menu.addEventListener('mouseenter', () => {
      if (!hover.matches) return;
      menus.forEach((other) => other !== m && other.setOpen(false));
      m.setOpen(true);
    });
    m.menu.addEventListener('mouseleave', () => hover.matches && m.later());
    m.menu.addEventListener('focusout', (e) => {
      if (!m.menu.contains(e.relatedTarget)) m.setOpen(false);
    });
  });
  if (menus.length) {
    document.addEventListener('click', (e) => menus.forEach((m) => !m.menu.contains(e.target) && m.setOpen(false)));
    document.addEventListener('keydown', (e) => {
      if (e.key !== 'Escape') return;
      const open = menus.find((m) => !m.mega.hidden);
      if (open) {
        open.setOpen(false);
        open.trigger.focus();
      }
    });
  }

  const status = document.querySelector('[data-demo-status]');
  if (status && 'fetch' in window) {
    const data = location.hostname === 'mockhub.devops-monk.com' ? '/status/data' : 'https://mockhub.devops-monk.com/status/data';
    const latest = fetch(`${data}/status.json`, { cache: 'no-store' }).then((res) => (res.ok ? res.json() : null)).catch(() => null);
    Promise.all([import('/assets/js/status-model.js'), latest])
      .then(([model, result]) => {
        const state = model.overall(result, Date.now());
        status.dataset.state = state;
        status.querySelector('a').textContent = model.STATES[state];
      })
      .catch(() => {
        status.dataset.state = 'unknown';
      });
  }

  const tryCard = document.querySelector('[data-try]');
  if (tryCard) {
    const host = location.hostname === 'mockhub.devops-monk.com' ? '' : 'https://mockhub.devops-monk.com';
    const $ = (name) => tryCard.querySelector(`[data-try-${name}]`);
    const tabs = Array.from(tryCard.querySelectorAll('.try-tabs button'));
    let current = tabs[0];
    const pretty = (text, type) => {
      if (type.includes('json')) {
        try {
          return JSON.stringify(JSON.parse(text), null, 2);
        } catch (e) {
          return text;
        }
      }
      return text.trim();
    };
    const show = (tab) => {
      current = tab;
      tabs.forEach((t) => t.setAttribute('aria-pressed', String(t === tab)));
      const method = tab.dataset.method;
      $('method').textContent = method;
      $('method').className = `jack ${method.toLowerCase()}`;
      $('path').textContent = `mockhub.devops-monk.com${tab.dataset.path}`;
      $('req').hidden = !tab.dataset.body;
      $('req').textContent = tab.dataset.body ?? '';
    };
    const send = async () => {
      const tab = current;
      const button = $('send');
      button.disabled = true;
      button.textContent = 'Sending…';
      const started = performance.now();
      try {
        const res = await fetch(host + tab.dataset.path, {
          method: tab.dataset.method,
          headers: tab.dataset.type ? { 'Content-Type': tab.dataset.type } : {},
          body: tab.dataset.body,
          cache: 'no-store',
        });
        const text = await res.text();
        $('time').textContent = `${Math.round(performance.now() - started)} ms`;
        $('status').textContent = `${res.status} ${res.statusText || ''}`.trim();
        $('status').className = `try-status ${res.ok ? 'ok' : 'bad'}`;
        $('stub').textContent = res.headers.get('Matched-Stub-Name') ?? '';
        $('body').textContent = pretty(text, res.headers.get('Content-Type') ?? '');
      } catch (e) {
        $('status').textContent = 'No answer';
        $('status').className = 'try-status bad';
        $('time').textContent = '';
        $('stub').textContent = '';
        $('body').textContent = `The demo couldn't be reached from this page.\nTry it in a terminal:\n\ncurl -X ${tab.dataset.method} https://mockhub.devops-monk.com${tab.dataset.path}`;
      } finally {
        button.disabled = false;
        button.textContent = 'Send';
      }
    };
    tabs.forEach((tab) => tab.addEventListener('click', () => {
      show(tab);
      send();
    }));
    $('send').addEventListener('click', send);
  }

  const docLinks = document.querySelectorAll('.tut-toc a[href^="#"]');
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
