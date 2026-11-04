import { useEffect, useState } from 'react';
import { api } from '../../api/client';

export function useConnectionKeys(orgId: string | undefined): string[] {
  const [keys, setKeys] = useState<string[]>([]);
  useEffect(() => {
    if (!orgId) return;
    let live = true;
    api.connections(orgId)
      .then((list) => live && setKeys(list.map((c) => c.key)))
      .catch(() => live && setKeys([]));
    return () => {
      live = false;
    };
  }, [orgId]);
  return keys;
}
