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
  apiKey?: ApiKeyRule | null;
  root?: boolean;
  runtime?: RuntimeSettings | null;
}

export interface ApiKeyRule {
  header: string;
  value?: string;
  set?: boolean;
}

export interface StubUsage {
  count: number;
  lastCalledAt: string;
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
export type OrgRole = 'viewer' | 'editor' | 'manager';
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
  action: 'create' | 'update' | 'delete' | 'runtime' | 'snapshot' | 'restore';
  stubId: string | null;
  stub: Stub | null;
  before: Stub | null;
  summary: string;
}

export interface StagedChange {
  change: ProposedChange;
  by: string;
  at: string;
}

export type TokenRole = 'viewer' | 'editor' | 'manager' | 'admin';
export type TokenMode = 'direct' | 'review';

export interface AccessToken {
  id: string;
  name: string;
  prefix: string;
  role: TokenRole;
  mode: TokenMode;
  projects: { id: string; name: string }[];
  createdAt: string;
  expiresAt: string | null;
  lastUsedAt: string | null;
  expired: boolean;
}

export interface NewAccessToken {
  name: string;
  role: TokenRole;
  mode: TokenMode;
  projectIds: string[];
  expiresInDays: number;
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

export interface DataResource {
  name: string;
  count: number;
  idField: string;
  idType: 'uuid' | 'number' | 'short';
  crud: boolean;
}

export type DatasetColumnType = 'string' | 'number' | 'boolean' | 'json';
export type DatasetFormat = 'csv' | 'json' | 'ndjson';
export type DatasetMode = 'replace' | 'upsert' | 'add';

export interface Dataset {
  orgId: string;
  key: string;
  name: string;
  description: string;
  identity: string[];
  indexes: string[];
  columns: { name: string; type: DatasetColumnType }[];
  recordCount: number;
  bytes: number;
  createdAt: string;
  updatedAt: string;
}

export interface DatasetLimits {
  maxUploadBytes: number;
  maxRecords: number;
  maxRecordBytes: number;
  maxDatasets: number;
  maxIdentity: number;
  maxIndexes: number;
}

export interface DatasetUpload {
  added: number;
  updated: number;
  skipped: number;
  dataset: Dataset;
}

export interface StateContext {
  name: string;
  properties: Record<string, string>;
  list: Record<string, string>[];
  updateCount: number;
  updatedAt: string;
}

export interface CrudRequest {
  resource: string;
  idField: string;
  idType: 'uuid' | 'number' | 'short';
  seed?: unknown[];
}

export interface BodyFile {
  name: string;
  size: number;
  contentType: string;
  modified: string;
  path: string;
  usedBy: number;
}

export interface HeaderPair {
  name: string;
  value: string;
}

export interface PlaygroundRequest {
  method: string;
  url: string;
  headers: HeaderPair[];
  body: string;
}

export interface PlaygroundResponse {
  status: number;
  headers: HeaderPair[];
  body: string;
  size: number;
  truncated: boolean;
  durationMs: number;
}

export interface PlaygroundEntry {
  id: string;
  at: string;
  request: PlaygroundRequest;
  response: PlaygroundResponse | null;
  error: string | null;
  local: boolean;
}

export interface CalloutEntry {
  id: string;
  at: string;
  projectId: string | null;
  stubId: string | null;
  stubName: string | null;
  mode: 'async' | 'sync';
  trigger: { method: string; url: string } | null;
  request: { method: string; url: string; headers: HeaderPair[]; body: string } | null;
  response: { status: number; headers: HeaderPair[]; body: string; truncated: boolean } | null;
  error: string | null;
  durationMs: number;
  requestId: string | null;
}

export interface HistoryEntry {
  id: string;
  at: number;
  projectId: string;
  method: string;
  url: string;
  absoluteUrl: string;
  clientIp: string;
  requestHeaders: HeaderPair[];
  requestBody: string;
  requestBodyTruncated: boolean;
  status: number;
  responseHeaders: HeaderPair[];
  responseBody: string;
  responseBodyTruncated: boolean;
  matched: boolean;
  stubId: string | null;
  stubName: string | null;
  durationMs: number;
}

export interface HistoryPage {
  items: HistoryEntry[];
  total: number;
  truncated: boolean;
  facets: {
    methods: Record<string, number>;
    statuses: Record<string, number>;
    matched: number;
    unmatched: number;
    stubs: { id: string; name: string | null; count: number }[];
  };
}

export interface HistoryDetail {
  entry: HistoryEntry;
  callouts: CalloutEntry[];
}

export type TrafficWindow = '5m' | '15m' | '1h' | '6h' | '24h';

export interface TrafficView {
  window: TrafficWindow;
  bucketSeconds: number;
  points: { at: number; requests: number; unmatched: number }[];
  total: number;
  unmatched: number;
  averageRps: number;
  peakRps: number;
  firstAt: number | null;
  lastAt: number | null;
}

export interface TrafficDay {
  date: string;
  requests: number;
  unmatched: number;
}

export interface GeneratorField {
  name: string;
  expression: string;
}

export interface AuditEntry {
  id: string;
  at: string;
  actorId: string;
  actor: string;
  action: string;
  target: string;
  targetName: string;
  details: string;
  orgId: string | null;
  teamId: string | null;
  projectId: string | null;
  projectName: string | null;
}

export interface AuditPage {
  retentionDays: number;
  entries: AuditEntry[];
}

export type RatePeriod = 'second' | 'minute' | 'hour';
export type LatencyMode = 'none' | 'fixed' | 'uniform' | 'lognormal';

export interface RateLimitRule {
  requests: number;
  per: RatePeriod;
}

export interface DefaultResponseRule {
  status: number;
  headers: Record<string, string>;
  body: string;
  templated: boolean;
}

export interface LatencyRule {
  mode: LatencyMode;
  fixedMs?: number;
  minMs?: number;
  maxMs?: number;
  medianMs?: number;
  sigma?: number;
  load?: { afterRps: number; msPerRequest: number } | null;
}

export interface CorsRule {
  origins: string[];
  methods: string[];
  headers: string[];
  credentials: boolean;
  simulateFailure: boolean;
}

export interface RuntimeSettings {
  defaultResponse: DefaultResponseRule | null;
  rateLimit: RateLimitRule | null;
  latency: LatencyRule | null;
  cors: CorsRule | null;
  ipAllowList: string[];
}

export interface SnapshotSummary {
  id: string;
  name: string;
  purpose: string;
  createdAt: string;
  createdBy: string;
  safety: boolean;
  sourceProjectName: string;
  stubCount: number;
  fileCount: number;
  crudResources: string[];
}

export type SnapshotStubStatus = 'added' | 'removed' | 'changed' | 'moved';

export interface SnapshotDiff {
  from: string;
  to: string;
  identical: boolean;
  counts: { added: number; removed: number; changed: number; unchanged: number };
  stubs: { id: string; status: SnapshotStubStatus; name: string; method: string; url: string; before: Stub | null; after: Stub | null }[];
  runtime: { changed: boolean; before: RuntimeSettings | null; after: RuntimeSettings | null };
  apiKeyHeader: { changed: boolean; before: string | null; after: string | null };
  files: { added: string[]; removed: string[]; changed: string[] };
}

export interface SnapshotRestoreResult {
  name: string;
  safety: SnapshotSummary;
  stubs: number;
  files: number;
}
