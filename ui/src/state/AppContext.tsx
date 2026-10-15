import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, credentials, setUnauthorizedHandler } from '../api/client';
import type { AuthInfo, OrgAccess, Project, Role, ServerInfo, Session } from '../api/types';

const ORG_KEY = 'mockhub.org';
const RANK: Record<Role, number> = { none: 0, viewer: 1, editor: 2, owner: 3, admin: 4 };

export function atLeast(role: Role | undefined, needed: Role) {
  return RANK[role ?? 'none'] >= RANK[needed];
}

interface AppState {
  info: ServerInfo | null;
  authInfo: AuthInfo | null;
  session: Session | null;
  projects: Project[];
  orgProjects: Project[];
  orgs: OrgAccess[];
  currentOrg: OrgAccess | null;
  role: Role;
  needsLogin: boolean;
  loginError: string;
  canEdit: boolean;
  isOwner: boolean;
  isAdmin: boolean;
  roleIn: (orgId: string | undefined) => Role;
  setCurrentOrg: (orgId: string) => void;
  refreshProjects: () => Promise<void>;
  refreshSession: () => Promise<void>;
  signIn: (username: string, password: string) => Promise<void>;
  acceptToken: (token: string) => Promise<void>;
  signOut: () => void;
}

const AppContext = createContext<AppState | null>(null);

function readOrg() {
  try {
    return localStorage.getItem(ORG_KEY);
  } catch {
    return null;
  }
}

function writeOrg(id: string) {
  try {
    localStorage.setItem(ORG_KEY, id);
  } catch {
    return;
  }
}

export function readSsoHash(hash: string): { code?: string; error?: string } {
  const params = new URLSearchParams(hash.replace(/^#/, ''));
  return { code: params.get('sso_code') ?? undefined, error: params.get('sso_error') ?? undefined };
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [info, setInfo] = useState<ServerInfo | null>(null);
  const [authInfo, setAuthInfo] = useState<AuthInfo | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [needsLogin, setNeedsLogin] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [currentOrgId, setCurrentOrgId] = useState<string | null>(readOrg);

  const refreshProjects = useCallback(async () => {
    setProjects(await api.projects());
  }, []);

  const refreshSession = useCallback(async () => {
    setSession(await api.me());
  }, []);

  const load = useCallback(async () => {
    const auth = await api.authInfo();
    setAuthInfo(auth);
    if (auth.authEnabled && !credentials.get()) {
      setNeedsLogin(true);
      return;
    }
    try {
      const me = await api.me();
      setSession(me);
      setNeedsLogin(false);
      const [serverInfo, list] = await Promise.all([api.info(), api.projects()]);
      setInfo(serverInfo);
      setProjects(list);
    } catch {
      credentials.clear();
      setNeedsLogin(true);
    }
  }, []);

  const acceptToken = useCallback(
    async (token: string) => {
      credentials.set(token);
      setLoginError('');
      await load();
    },
    [load],
  );

  useEffect(() => {
    setUnauthorizedHandler(() => {
      credentials.clear();
      setSession(null);
      setNeedsLogin(true);
    });
    const { code, error } = readSsoHash(window.location.hash);
    if (code || error) window.history.replaceState(null, '', window.location.pathname + window.location.search);
    if (error) setLoginError(error);
    if (code) {
      api
        .exchangeSsoCode(code)
        .then((result) => (result.token ? acceptToken(result.token) : load()))
        .catch((e: Error) => {
          setLoginError(e.message);
          load();
        });
    } else {
      load();
    }
  }, [load, acceptToken]);

  const signIn = useCallback(
    async (username: string, password: string) => {
      const result = await api.login(username, password);
      if (result.token) credentials.set(result.token);
      setLoginError('');
      await load();
    },
    [load],
  );

  const signOut = useCallback(() => {
    credentials.clear();
    setSession(null);
    setNeedsLogin(true);
  }, []);

  const orgs = useMemo(() => session?.orgs ?? [], [session]);
  const currentOrg = orgs.find((o) => o.id === currentOrgId) ?? orgs[0] ?? null;

  const setCurrentOrg = useCallback((orgId: string) => {
    setCurrentOrgId(orgId);
    writeOrg(orgId);
  }, []);

  const roleIn = useCallback(
    (orgId: string | undefined): Role => {
      if (session?.isAdmin) return 'admin';
      return orgs.find((o) => o.id === orgId)?.role ?? 'none';
    },
    [session, orgs],
  );

  const role = roleIn(currentOrg?.id);
  const orgProjects = useMemo(() => projects.filter((p) => p.orgId === currentOrg?.id), [projects, currentOrg]);

  const value: AppState = {
    info,
    authInfo,
    session,
    projects,
    orgProjects,
    orgs,
    currentOrg,
    role,
    needsLogin,
    loginError,
    canEdit: atLeast(role, 'editor'),
    isOwner: atLeast(role, 'owner'),
    isAdmin: Boolean(session?.isAdmin),
    roleIn,
    setCurrentOrg,
    refreshProjects,
    refreshSession,
    signIn,
    acceptToken,
    signOut,
  };
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}
