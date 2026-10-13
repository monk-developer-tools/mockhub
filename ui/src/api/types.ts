export interface Project {
  id: string;
  name: string;
  slug: string;
  description: string;
  color: string;
  createdAt: string;
  updatedAt: string;
  basePath: string;
  mockUrl: string;
  stubCount: number;
  soapStubCount: number;
  requestCount: number;
}

export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export type JsonObject = { [key: string]: Json };

export interface Stub extends JsonObject {}

export interface ServerInfo {
  version: string;
  wiremockVersion: string;
  baseUrl: string;
  dataDir: string;
  authEnabled: boolean;
  journalMaxEntries: number;
}

export interface Session {
  username: string;
  role: 'admin' | 'editor' | 'viewer';
}

export interface Example {
  id: string;
  name: string;
  slug: string;
  description: string;
  tags: string[];
}

export interface LoggedRequest {
  url: string;
  absoluteUrl: string;
  method: string;
  headers: Record<string, string | string[]>;
  body: string;
  loggedDate: number;
  loggedDateString: string;
  clientIp: string;
}

export interface ServeEvent {
  id: string;
  request: LoggedRequest;
  responseDefinition?: JsonObject;
  response?: { status: number; headers?: Record<string, string | string[]>; body?: string };
  wasMatched: boolean;
  stubMapping?: Stub;
  timing?: { totalTime: number; serveTime: number };
}

export interface Scenario {
  id: string;
  name: string;
  state: string;
  possibleStates: string[];
}

export interface ImportResult {
  imported: number;
  [key: string]: Json;
}
