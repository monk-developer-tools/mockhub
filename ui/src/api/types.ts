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
  teamId: string;
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
  teamMemberships: TeamMembershipView[];
  groupTeamMemberships: TeamMembershipView[];
}

export interface TeamMembershipView {
  teamId: string;
  orgId: string;
  role: OrgRole;
  teamName?: string;
  orgName?: string;
}

export interface TeamAccess {
  id: string;
  name: string;
  slug: string;
  role: Role;
  member: boolean;
}

export interface Team {
  id: string;
  orgId: string;
  name: string;
  slug: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  projectCount: number;
  memberCount: number;
  role: Role;
}

export interface TeamMember extends UserSummary {
  role: OrgRole | '';
  groupRole: OrgRole | '';
  orgRole: Role | '';
  effectiveRole: Role;
  member: boolean;
  disabled: boolean;
}

export interface OrgAccess {
  id: string;
  name: string;
  slug: string;
  role: Role;
  teams: TeamAccess[];
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
  teamCount: number;
  role: Role;
}

export interface Member extends UserSummary {
  role: OrgRole | '';
  groupRole: OrgRole | '';
  effectiveRole: Role;
  disabled: boolean;
  teams: { id: string; name: string; role: OrgRole | '' }[];
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
  teamId?: string;
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

export interface AiStatus {
  enabled: boolean;
  provider: string;
  providerLabel: string;
  model: string;
}

export interface AiProviderInfo {
  id: string;
  label: string;
  defaultBaseUrl: string;
  defaultModel: string;
  requiresApiKey: boolean;
}

export interface AiSettings {
  enabled: boolean;
  provider: string;
  baseUrl: string;
  apiKey?: string;
  clearApiKey?: boolean;
  model: string;
  temperature: number;
  maxToolRounds: number;
  apiKeySet: boolean;
  apiKeyFromEnvironment: boolean;
  providers: AiProviderInfo[];
}

export interface AiTestResult {
  ok: boolean;
  error?: string;
  modelCount?: number;
  modelFound?: boolean;
  models?: string[];
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  steps?: AssistantStep[];
}

export interface AssistantStep {
  tool: string;
  detail: string;
}

export interface ProposedChange {
  id: string;
  action: 'create' | 'update' | 'delete';
  stubId: string | null;
  stub: Stub | null;
  before: Stub | null;
  summary: string;
}

export interface AssistantReply {
  reply: string;
  changes: ProposedChange[];
  steps: AssistantStep[];
  promptTokens: number;
  completionTokens: number;
}

export interface AssistantJob {
  id: string;
  status: 'running' | 'done' | 'failed';
  steps: AssistantStep[];
  reply: AssistantReply | null;
  error: string | null;
}

export interface ApplyResult {
  applied: number;
  results: { changeId: string; ok: boolean; stubId: string | null; error: string | null }[];
}

export interface BackupSelection {
  everything?: boolean;
  organizations: string[];
  projects: string[];
}

export interface PreviewProject {
  id: string;
  name: string;
  slug: string;
  stubCount: number;
  status: 'new' | 'replace' | 'slug-taken';
  newSlug?: string;
  teamName?: string;
}

export interface PreviewOrg {
  id: string;
  name: string;
  slug: string;
  exists: boolean;
  projects: PreviewProject[];
}

export interface RestorePreview {
  createdAt: string;
  mockhubVersion: string;
  scope: 'full' | 'selection';
  organizations: PreviewOrg[];
  unassignedStubs: number;
}

export interface RestoreRequest extends BackupSelection {
  targetOrgId?: string;
  targetTeamId?: string;
  mode: 'replace' | 'copy';
}

export interface RestoreResult {
  organizations: number;
  projects: number;
  stubs: number;
  renamed: { project: string; from: string; to: string }[];
}
