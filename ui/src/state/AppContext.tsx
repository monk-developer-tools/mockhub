import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, credentials, setUnauthorizedHandler } from '../api/client';
import type { AiStatus, AuthInfo, OrgAccess, Project, Role, ServerInfo, Session, TeamAccess } from '../api/types';

const ORG_KEY = 'mockhub.org';
const RANK: Record<Role, number> = { none: 0, viewer: 1, editor: 2, manager: 3, admin: 4 };

export function atLeast(role: Role | undefined, needed: Role) {
  return RANK[role ?? 'none'] >= RANK[needed];
}

export function highest(roles: Role[]): Role {
  return roles.reduce<Role>((best, r) => (RANK[r] > RANK[best] ? r : best), 'none');
}

interface AppState {
  info: ServerInfo | null;
  authInfo: AuthInfo | null;
  aiStatus: AiStatus | null;
  refreshAiStatus: () => Promise<void>;
  session: Session | null;
  projects: Project[];
  orgProjects: Project[];
  orgs: OrgAccess[];
  currentOrg: OrgAccess | null;
  role: Role;
  needsLogin: boolean;
  loginError: string;
  canEdit: boolean;
  canManage: boolean;
  isManager: boolean;
  isAdmin: boolean;
  roleIn: (orgId: string | undefined) => Role;
  roleInTeam: (orgId: string | undefined, teamId: string | undefined) => Role;
  teamsIn: (orgId: string | undefined) => TeamAccess[];
  writableTeams: (orgId: string | undefined) => TeamAccess[];
  activeProject: Project | null;
  setActiveProject: (project: Project | null) => void;
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
  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [needsLogin, setNeedsLogin] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [currentOrgId, setCurrentOrgId] = useState<string | null>(readOrg);
  const [activeProject, setActiveProject] = useState<Project | null>(null);

  const refreshProjects = useCallback(async () => {
    setProjects(await api.projects());
  }, []);

  const refreshAiStatus = useCallback(async () => {
    setAiStatus(await api.aiStatus().catch(() => null));
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
      const [serverInfo, list, ai] = await Promise.all([api.info(), api.projects(), api.aiStatus().catch(() => null)]);
      setInfo(serverInfo);
      setProjects(list);
      setAiStatus(ai);
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

  const teamsIn = useCallback((orgId: string | undefined) => orgs.find((o) => o.id === orgId)?.teams ?? [], [orgs]);

  const roleInTeam = useCallback(
    (orgId: string | undefined, teamId: string | undefined): Role => {
      if (session?.isAdmin) return 'admin';
      const org = orgs.find((o) => o.id === orgId);
      if (!org) return 'none';
      return highest([org.role, org.teams.find((t) => t.id === teamId)?.role ?? 'none']);
    },
    [session, orgs],
  );

  const writableTeams = useCallback(
    (orgId: string | undefined) => teamsIn(orgId).filter((t) => atLeast(roleInTeam(orgId, t.id), 'manager')),
    [teamsIn, roleInTeam],
  );

  const orgRole = roleIn(currentOrg?.id);
  const role = activeProject
    ? roleInTeam(activeProject.orgId, activeProject.teamId)
    : highest([orgRole, ...teamsIn(currentOrg?.id).map((t) => roleInTeam(currentOrg?.id, t.id))]);
  const orgProjects = useMemo(() => projects.filter((p) => p.orgId === currentOrg?.id), [projects, currentOrg]);

  const value: AppState = {
    info,
    authInfo,
    aiStatus,
    refreshAiStatus,
    session,
    projects,
    orgProjects,
    orgs,
    currentOrg,
    role,
    needsLogin,
    loginError,
    canEdit: atLeast(role, 'editor'),
    canManage: atLeast(role, 'manager'),
    isManager: atLeast(orgRole, 'manager'),
    isAdmin: Boolean(session?.isAdmin),
    roleIn,
    roleInTeam,
    teamsIn,
    writableTeams,
    activeProject,
    setActiveProject,
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
