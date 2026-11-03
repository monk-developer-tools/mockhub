import { useCallback, useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { useDismiss } from '../../state/useDismiss';

export interface MenuItem {
  label: string;
  icon?: ReactNode;
  hint?: ReactNode;
  href?: string;
  onSelect?: () => void;
}

interface Props {
  label: string;
  trigger: ReactNode;
  items: MenuItem[];
  align?: 'start' | 'end';
  triggerClassName?: string;
}

const STEPS: Record<string, (index: number, count: number) => number> = {
  ArrowDown: (i, n) => (i + 1) % n,
  ArrowUp: (i, n) => (i - 1 + n) % n,
  Home: () => 0,
  End: (_, n) => n - 1,
};

export function Menu({ label, trigger, items, align = 'end', triggerClassName = 'btn ghost icon' }: Props) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const popRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const close = useCallback(() => setOpen(false), []);
  const closeAndFocus = useCallback(() => {
    setOpen(false);
    triggerRef.current?.focus();
  }, []);
  useDismiss(ref, open, closeAndFocus);

  useEffect(() => {
    if (open) popRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
  }, [open]);

  const move = (e: KeyboardEvent<HTMLDivElement>) => {
    const step = STEPS[e.key];
    if (!step) return;
    e.preventDefault();
    const entries = Array.from(popRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
    const current = entries.indexOf(document.activeElement as HTMLElement);
    entries[step(current, entries.length)]?.focus();
  };

  return (
    <div className="menu" ref={ref}>
      <button
        ref={triggerRef}
        type="button"
        className={triggerClassName}
        aria-label={label}
        title={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        {trigger}
      </button>
      {open && (
        <div ref={popRef} className={`menu-pop ${align}`} role="menu" aria-label={label} onKeyDown={move}>
          {items.map((item) => {
            const content = (
              <>
                {item.icon && <span className="menu-icon">{item.icon}</span>}
                <span className="menu-label">{item.label}</span>
                {item.hint && <span className="menu-hint">{item.hint}</span>}
              </>
            );
            return item.href ? (
              <a key={item.label} role="menuitem" className="menu-item" href={item.href} target="_blank" rel="noreferrer" onClick={close}>
                {content}
              </a>
            ) : (
              <button
                key={item.label}
                type="button"
                role="menuitem"
                className="menu-item"
                onClick={() => {
                  close();
                  item.onSelect?.();
                }}
              >
                {content}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
