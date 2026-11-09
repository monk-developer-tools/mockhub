import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { ProjectEnvironments } from '../api/types';

const EVENT = 'mockhub:environments';

export function announceEnvironments(projectId: string) {
  window.dispatchEvent(new CustomEvent(EVENT, { detail: projectId }));
}

export function useEnvironments(projectId: string) {
  const [environments, setEnvironments] = useState<ProjectEnvironments | null>(null);
  const load = useCallback(() => api.environments(projectId).then(setEnvironments).catch(() => setEnvironments({ active: null, environments: [] })), [projectId]);
  useEffect(() => {
    load();
    const reload = (e: Event) => (e as CustomEvent<string>).detail === projectId && load();
    window.addEventListener(EVENT, reload);
    return () => window.removeEventListener(EVENT, reload);
  }, [load, projectId]);
  return { environments, reload: load };
}
