import type {
  AiSettings,
  AiStatus,
  AiTestResult,
  ApplyResult,
  AssistantJob,
  BackupSelection,
  BodyFile,
  CrudRequest,
  DataResource,
  RestorePreview,
  RestoreRequest,
  RestoreResult,
  AuthInfo,
  ChatMessage,
  ProposedChange,
  Example,
  ImportResult,
  LoginResult,
  Member,
  Team,
  TeamMember,
  Organization,
  Project,
  Scenario,
  ServeEvent,
  ServerInfo,
  Session,
  SsoSettings,
  Stub,
  UserRecord,
} from './types';

const API = '/__admin/mockhub/api';
const ADMIN = '/__admin';
const TOKEN_KEY = 'mockhub.auth';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export const credentials = {
  get(): string | null {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(bearer: string) {
    const token = `Bearer ${bearer}`;
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      return token;
    }
    return token;
  },
  clear() {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      return;
    }
  },
};

let onUnauthorized: () => void = () => undefined;
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(path: string, init: RequestInit = {}, token: string | null = credentials.get()): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', token);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...init, headers });
  if (response.status === 401 && token) onUnauthorized();
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    message = await errorMessage(response, message);
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  const type = response.headers.get('Content-Type') ?? '';
  return (type.includes('json') ? response.json() : response.text()) as Promise<T>;
}

const json = (body: unknown) => JSON.stringify(body);

async function errorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json();
    return body.error ?? body.errors?.[0]?.title ?? fallback;
  } catch {
    return fallback;
  }
}

async function download(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers);
  const token = credentials.get();
  if (token) headers.set('Authorization', token);
  if (init.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) throw new ApiError(response.status, await errorMessage(response, `Download failed (${response.status})`));
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? 'download';
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export const api = {
  info: () => request<ServerInfo>(`${API}/info`),
  authInfo: () => request<AuthInfo>(`${API}/auth/info`),
  login: (username: string, password: string) =>
    request<LoginResult>(`${API}/auth/login`, { method: 'POST', body: json({ username, password }) }, null),
  me: () => request<Session>(`${API}/auth/me`),
  exchangeSsoCode: (code: string) => request<LoginResult>(`${API}/auth/sso/exchange`, { method: 'POST', body: json({ code }) }, null),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<LoginResult>(`${API}/auth/password`, { method: 'POST', body: json({ currentPassword, newPassword }) }),

  orgs: () => request<Organization[]>(`${API}/orgs`),
  createOrg: (body: Partial<Organization>) => request<Organization>(`${API}/orgs`, { method: 'POST', body: json(body) }),
  updateOrg: (id: string, body: Partial<Organization>) => request<Organization>(`${API}/orgs/${id}`, { method: 'PUT', body: json(body) }),
  deleteOrg: (id: string) => request<void>(`${API}/orgs/${id}`, { method: 'DELETE' }),
  members: (orgId: string) => request<Member[]>(`${API}/orgs/${orgId}/members`),
  setMember: (orgId: string, userId: string, role: string) =>
    request<UserRecord>(`${API}/orgs/${orgId}/members/${userId}`, { method: 'PUT', body: json({ role }) }),
  removeMember: (orgId: string, userId: string) => request<void>(`${API}/orgs/${orgId}/members/${userId}`, { method: 'DELETE' }),
  teams: (orgId: string) => request<Team[]>(`${API}/orgs/${orgId}/teams`),
  createTeam: (orgId: string, body: Partial<Team>) => request<Team>(`${API}/orgs/${orgId}/teams`, { method: 'POST', body: json(body) }),
  updateTeam: (orgId: string, teamId: string, body: Partial<Team>) =>
    request<Team>(`${API}/orgs/${orgId}/teams/${teamId}`, { method: 'PUT', body: json(body) }),
  deleteTeam: (orgId: string, teamId: string) => request<void>(`${API}/orgs/${orgId}/teams/${teamId}`, { method: 'DELETE' }),
  teamMembers: (orgId: string, teamId: string) => request<TeamMember[]>(`${API}/orgs/${orgId}/teams/${teamId}/members`),
  setTeamMember: (orgId: string, teamId: string, userId: string, role: string) =>
    request<UserRecord>(`${API}/orgs/${orgId}/teams/${teamId}/members/${userId}`, { method: 'PUT', body: json({ role }) }),
  removeTeamMember: (orgId: string, teamId: string, userId: string) =>
    request<void>(`${API}/orgs/${orgId}/teams/${teamId}/members/${userId}`, { method: 'DELETE' }),

  users: () => request<UserRecord[]>(`${API}/users`),
  createUser: (body: Record<string, unknown>) => request<UserRecord & { token?: string }>(`${API}/users`, { method: 'POST', body: json(body) }),
  updateUser: (id: string, body: Record<string, unknown>) => request<UserRecord>(`${API}/users/${id}`, { method: 'PUT', body: json(body) }),
  resetPassword: (id: string, password: string) =>
    request<UserRecord>(`${API}/users/${id}/password`, { method: 'POST', body: json({ password }) }),
  deleteUser: (id: string) => request<void>(`${API}/users/${id}`, { method: 'DELETE' }),

  sso: () => request<SsoSettings>(`${API}/sso`),
  saveSso: (body: Partial<SsoSettings>) => request<SsoSettings>(`${API}/sso`, { method: 'PUT', body: json(body) }),
  testSso: (body: Partial<SsoSettings>) =>
    request<{ ok: boolean; error?: string; issuer?: string }>(`${API}/sso/test`, { method: 'POST', body: json(body) }),

  aiStatus: () => request<AiStatus>(`${API}/ai/status`),
  aiSettings: () => request<AiSettings>(`${API}/ai/settings`),
  saveAiSettings: (body: Partial<AiSettings>) => request<AiSettings>(`${API}/ai/settings`, { method: 'PUT', body: json(body) }),
  testAi: (body: Partial<AiSettings>) => request<AiTestResult>(`${API}/ai/test`, { method: 'POST', body: json(body) }),
  startAssistant: (projectId: string, messages: ChatMessage[], pending: ProposedChange[]) =>
    request<AssistantJob>(`${API}/projects/${projectId}/ai/jobs`, {
      method: 'POST',
      body: json({ messages: messages.map(({ role, content }) => ({ role, content })), pending }),
    }),
  assistantJob: (projectId: string, jobId: string) => request<AssistantJob>(`${API}/projects/${projectId}/ai/jobs/${jobId}`),
  createCrud: (projectId: string, body: CrudRequest) =>
    request<{ seeded: number; stub: Stub }>(`${API}/projects/${projectId}/crud`, { method: 'POST', body: json(body) }),
  dataResources: (projectId: string) => request<{ resources: DataResource[] }>(`${API}/projects/${projectId}/data`),
  dataRecords: (projectId: string, resource: string) =>
    request<Record<string, unknown>[]>(`${API}/projects/${projectId}/data/records?resource=${encodeURIComponent(resource)}`),
  addRecord: (projectId: string, resource: string, record: unknown) =>
    request<Record<string, unknown>>(`${API}/projects/${projectId}/data/records?resource=${encodeURIComponent(resource)}`, { method: 'POST', body: json(record) }),
  replaceRecord: (projectId: string, resource: string, id: string, record: unknown) =>
    request<Record<string, unknown>>(`${API}/projects/${projectId}/data/records?resource=${encodeURIComponent(resource)}&id=${encodeURIComponent(id)}`, { method: 'PUT', body: json(record) }),
  deleteRecord: (projectId: string, resource: string, id: string) =>
    request<void>(`${API}/projects/${projectId}/data/records?resource=${encodeURIComponent(resource)}&id=${encodeURIComponent(id)}`, { method: 'DELETE' }),
  clearResource: (projectId: string, resource: string) =>
    request<void>(`${API}/projects/${projectId}/data/records?resource=${encodeURIComponent(resource)}`, { method: 'DELETE' }),
  files: (projectId: string) => request<BodyFile[]>(`${API}/projects/${projectId}/files`),
  uploadFile: (projectId: string, name: string, file: Blob) =>
    request<BodyFile>(`${API}/projects/${projectId}/files?name=${encodeURIComponent(name)}`, {
      method: 'POST',
      body: file,
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
    }),
  downloadFile: (projectId: string, name: string) => download(`${API}/projects/${projectId}/files/content?name=${encodeURIComponent(name)}`),
  deleteFile: (projectId: string, name: string) =>
    request<void>(`${API}/projects/${projectId}/files?name=${encodeURIComponent(name)}`, { method: 'DELETE' }),
  resetData: (projectId: string) => request<void>(`${API}/projects/${projectId}/data`, { method: 'DELETE' }),
  applyChanges: (projectId: string, changes: ProposedChange[]) =>
    request<ApplyResult>(`${API}/projects/${projectId}/ai/apply`, { method: 'POST', body: json({ changes }) }),

  projects: () => request<Project[]>(`${API}/projects`),
  project: (id: string) => request<Project>(`${API}/projects/${id}`),
  createProject: (body: Partial<Project>) => request<Project>(`${API}/projects`, { method: 'POST', body: json(body) }),
  updateProject: (id: string, body: Partial<Project>) =>
    request<Project>(`${API}/projects/${id}`, { method: 'PUT', body: json(body) }),
  deleteProject: (id: string) => request<void>(`${API}/projects/${id}`, { method: 'DELETE' }),

  stubs: (projectId: string) => request<Stub[]>(`${API}/projects/${projectId}/stubs`),
  createStub: (projectId: string, stub: Stub) =>
    request<Stub>(`${API}/projects/${projectId}/stubs`, { method: 'POST', body: json(stub) }),
  updateStub: (projectId: string, stubId: string, stub: Stub) =>
    request<Stub>(`${API}/projects/${projectId}/stubs/${stubId}`, { method: 'PUT', body: json(stub) }),
  deleteStub: (projectId: string, stubId: string) =>
    request<void>(`${API}/projects/${projectId}/stubs/${stubId}`, { method: 'DELETE' }),
  cloneStub: (projectId: string, stubId: string, target?: string) =>
    request<Stub>(`${API}/projects/${projectId}/stubs/${stubId}/clone${target ? `?target=${target}` : ''}`, { method: 'POST' }),
  moveStub: (projectId: string, stubId: string, target: string) =>
    request<Stub>(`${API}/projects/${projectId}/stubs/${stubId}/move?target=${target}`, { method: 'POST' }),
  unassignedStubs: () => request<Stub[]>(`${API}/stubs/unassigned`),
  adoptStub: (projectId: string, stubId: string, prefix: boolean) =>
    request<Stub>(`${API}/projects/${projectId}/adopt/${stubId}?prefix=${prefix}`, { method: 'POST' }),

  requests: (projectId: string, unmatched = false, limit = 300) =>
    request<ServeEvent[]>(`${API}/projects/${projectId}/requests?limit=${limit}&unmatched=${unmatched}`),
  clearRequests: (projectId: string) => request<void>(`${API}/projects/${projectId}/requests`, { method: 'DELETE' }),

  importInto: (projectId: string, format: string, body: string, options: Record<string, string> = {}) => {
    const query = new URLSearchParams(options).toString();
    return request<ImportResult>(`${API}/projects/${projectId}/import/${format}${query ? `?${query}` : ''}`, {
      method: 'POST',
      body,
      headers: { 'Content-Type': 'text/plain' },
    });
  },
  exportProject: (projectId: string, format: string) => download(`${API}/projects/${projectId}/export/${format}`),

  examples: () => request<Example[]>(`${API}/examples`),
  installExample: (id: string, orgId?: string) =>
    request<Project>(`${API}/examples/${id}/install${orgId ? `?orgId=${orgId}` : ''}`, { method: 'POST' }),

  backup: (selection: BackupSelection) => download(`${API}/backup`, { method: 'POST', body: json(selection) }),
  previewRestore: (file: string) => request<RestorePreview>(`${API}/restore/preview`, { method: 'POST', body: file }),
  restore: (file: string, options: RestoreRequest) =>
    request<RestoreResult>(`${API}/restore`, { method: 'POST', body: `{"backup":${file.trim()},${json(options).slice(1)}` }),

  scenarios: () => request<{ scenarios: Scenario[] }>(`${ADMIN}/scenarios`).then((r) => r.scenarios),
  resetScenarios: () => request<void>(`${ADMIN}/scenarios/reset`, { method: 'POST' }),
  setScenarioState: (name: string, state: string) =>
    request<void>(`${ADMIN}/scenarios/${encodeURIComponent(name)}/state`, { method: 'PUT', body: json({ state }) }),
  nearMisses: (loggedRequest: unknown) =>
    request<{ nearMisses: { stubMapping: Stub; matchResult: { distance: number } }[] }>(`${ADMIN}/near-misses/request`, {
      method: 'POST',
      body: json(loggedRequest),
    }),
  globalSettings: () => request<{ settings: { fixedDelay?: number } }>(`${ADMIN}/settings`),
  updateGlobalSettings: (settings: unknown) => request<void>(`${ADMIN}/settings`, { method: 'POST', body: json(settings) }),
  recordingStatus: () => request<{ status: 'NeverStarted' | 'Recording' | 'Stopped' }>(`${ADMIN}/recordings/status`),
  startRecording: (spec: unknown) => request<void>(`${ADMIN}/recordings/start`, { method: 'POST', body: json(spec) }),
  stopRecording: () => request<{ mappings: Stub[] }>(`${ADMIN}/recordings/stop`, { method: 'POST' }),
  resetJournal: () => request<void>(`${ADMIN}/requests`, { method: 'DELETE' }),
};
