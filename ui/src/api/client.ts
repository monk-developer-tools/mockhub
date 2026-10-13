import type { Example, ImportResult, Project, Scenario, ServeEvent, ServerInfo, Session, Stub } from './types';

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
  set(username: string, password: string) {
    const token = 'Basic ' + btoa(unescape(encodeURIComponent(`${username}:${password}`)));
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

async function request<T>(path: string, init: RequestInit = {}, token = credentials.get()): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', token);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...init, headers });
  if (response.status === 401) onUnauthorized();
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

async function download(path: string) {
  const headers = new Headers();
  const token = credentials.get();
  if (token) headers.set('Authorization', token);
  const response = await fetch(path, { headers });
  if (!response.ok) throw new ApiError(response.status, `Download failed (${response.status})`);
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
  authInfo: () => request<{ authEnabled: boolean; loginNote?: string }>(`${API}/auth/info`),
  login: (token: string) => request<Session>(`${API}/auth/login`, { method: 'POST' }, token),

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
  installExample: (id: string) => request<Project>(`${API}/examples/${id}/install`, { method: 'POST' }),

  backup: () => download(`${API}/backup`),
  restore: (body: string) => request<{ projects: number; stubs: number }>(`${API}/restore`, { method: 'POST', body }),

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
