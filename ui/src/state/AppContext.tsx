import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, credentials, setUnauthorizedHandler } from '../api/client';
import type { Project, ServerInfo, Session } from '../api/types';

interface AppState {
  info: ServerInfo | null;
  session: Session | null;
  projects: Project[];
  needsLogin: boolean;
  canEdit: boolean;
  isAdmin: boolean;
  refreshProjects: () => Promise<void>;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AppContext = createContext<AppState | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [info, setInfo] = useState<ServerInfo | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [needsLogin, setNeedsLogin] = useState(false);

  const refreshProjects = useCallback(async () => {
    setProjects(await api.projects());
  }, []);

  const load = useCallback(async () => {
    const { authEnabled } = await api.authInfo();
    const token = credentials.get();
    if (authEnabled && !token) {
      setNeedsLogin(true);
      return;
    }
    try {
      const current = await api.login(token ?? '');
      setSession(current);
      setNeedsLogin(false);
      const [serverInfo, list] = await Promise.all([api.info(), api.projects()]);
      setInfo(serverInfo);
      setProjects(list);
    } catch {
      credentials.clear();
      setNeedsLogin(true);
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      credentials.clear();
      setSession(null);
      setNeedsLogin(true);
    });
    load();
  }, [load]);

  const signIn = useCallback(
    async (username: string, password: string) => {
      const token = credentials.set(username, password);
      await api.login(token);
      await load();
    },
    [load],
  );

  const signOut = useCallback(() => {
    credentials.clear();
    setSession(null);
    setNeedsLogin(true);
  }, []);

  const role = session?.role;
  const value: AppState = {
    info,
    session,
    projects,
    needsLogin,
    canEdit: role === 'admin' || role === 'editor',
    isAdmin: role === 'admin',
    refreshProjects,
    signIn,
    signOut,
  };
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}
