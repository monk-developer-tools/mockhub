import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

interface Toast {
  id: number;
  message: string;
  kind: 'info' | 'error';
}

interface ToastApi {
  notify: (message: string) => void;
  fail: (error: unknown) => void;
}

const ToastContext = createContext<ToastApi>({ notify: () => undefined, fail: () => undefined });

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback((message: string, kind: Toast['kind']) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, message, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), kind === 'error' ? 6000 : 3000);
  }, []);
  const api: ToastApi = {
    notify: useCallback((m: string) => push(m, 'info'), [push]),
    fail: useCallback((e: unknown) => push(e instanceof Error ? e.message : String(e), 'error'), [push]),
  };
  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`}>{t.message}</div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
