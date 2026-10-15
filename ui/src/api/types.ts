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
  unmatchedCount: number;
  lastRequestAt?: string;
  activity: number[];
  orgId: string;
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

export type ServerRole = 'none' | 'viewer' | 'editor' | 'admin';
export type OrgRole = 'viewer' | 'editor' | 'owner';
export type Role = ServerRole | OrgRole;
export type UserSource = 'env' | 'local' | 'azure';

export interface MembershipView {
  orgId: string;
  role: OrgRole;
  orgName?: string;
}

export interface UserSummary {
  id: string;
  username: string;
  displayName: string;
  email: string;
  source: UserSource;
}

export interface UserRecord extends UserSummary {
  serverRole: ServerRole;
  groupServerRole: ServerRole;
  effectiveServerRole: ServerRole;
  disabled: boolean;
  hasPassword: boolean;
  createdAt?: string;
  lastLoginAt?: string;
  memberships: MembershipView[];
  groupMemberships: MembershipView[];
}

export interface OrgAccess {
  id: string;
  name: string;
  slug: string;
  role: Role;
}

export interface Session extends UserRecord {
  anonymous: boolean;
  isAdmin: boolean;
  authEnabled: boolean;
  orgs: OrgAccess[];
}

export interface Organization {
  id: string;
  name: string;
  slug: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  projectCount: number;
  memberCount: number;
  role: Role;
}

export interface Member extends UserSummary {
  role: OrgRole | '';
  groupRole: OrgRole | '';
  effectiveRole: Role;
  disabled: boolean;
}

export interface AuthInfo {
  authEnabled: boolean;
  loginNote?: string;
  passwordLogin: boolean;
  sso: { enabled: boolean; provider?: string; label?: string; startUrl?: string };
}

export interface LoginResult {
  token?: string;
  expiresIn?: number;
  user: Session;
}

export interface RoleMapping {
  claim: string;
  orgId: string;
  role: Role;
}

export interface SsoSettings {
  enabled: boolean;
  provider: 'azure';
  tenantId: string;
  clientId: string;
  clientSecret?: string;
  clientSecretSet: boolean;
  clientSecretFromEnvironment: boolean;
  cloud: 'global' | 'usgov' | 'china';
  buttonLabel: string;
  allowedDomains: string[];
  defaultServerRole: ServerRole;
  defaultOrgId: string;
  defaultOrgRole: OrgRole | 'none';
  roleMappings: RoleMapping[];
  allowPasswordLogin: boolean;
  redirectUri: string;
  active: boolean;
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
