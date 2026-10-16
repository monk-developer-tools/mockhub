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

  const docLinks = document.querySelectorAll('.docs aside a[href^="#"], .tut-toc a[href^="#"]');
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
