export const $ = (selector, root = document) => root.querySelector(selector);

export function debounce(fn, ms = 200) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

export function show(element, text) {
  element.textContent = text ?? '';
  element.hidden = !text;
}

export function wireCopyButtons() {
  document.querySelectorAll('[data-copy]').forEach((button) => {
    button.addEventListener('click', async () => {
      const text = $(button.dataset.copy)?.textContent ?? '';
      try {
        await navigator.clipboard.writeText(text);
        button.textContent = 'Copied';
      } catch (e) {
        button.textContent = 'Copy failed';
      }
      setTimeout(() => (button.textContent = 'Copy'), 1500);
    });
  });
}

export function download(name, text) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}
