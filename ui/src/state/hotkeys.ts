import { useEffect } from 'react';

export function isTyping(target: EventTarget | null) {
  const el = target as HTMLElement | null;
  if (!el) return false;
  return el.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName) || Boolean(el.closest('.cm-editor'));
}

export interface Hotkey {
  key: string;
  mod?: boolean;
  allowWhileTyping?: boolean;
  handler: (event: KeyboardEvent) => void;
}

export function useHotkeys(hotkeys: Hotkey[], enabled = true) {
  useEffect(() => {
    if (!enabled) return;
    const onKey = (event: KeyboardEvent) => {
      for (const h of hotkeys) {
        const modPressed = event.metaKey || event.ctrlKey;
        if (event.key.toLowerCase() !== h.key.toLowerCase()) continue;
        if (Boolean(h.mod) !== modPressed) continue;
        if (!h.allowWhileTyping && isTyping(event.target)) continue;
        event.preventDefault();
        h.handler(event);
        return;
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [hotkeys, enabled]);
}

export const MOD_LABEL = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘' : 'Ctrl';
