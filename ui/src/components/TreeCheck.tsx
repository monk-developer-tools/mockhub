import { useEffect, useRef } from 'react';

export function TreeCheck({ state, label, onChange }: { state: 'all' | 'some' | 'none'; label: string; onChange: () => void }) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.indeterminate = state === 'some';
  }, [state]);
  return <input ref={ref} type="checkbox" aria-label={label} checked={state === 'all'} onChange={onChange} />;
}
