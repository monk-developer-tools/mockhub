import { diffLines, type DiffLine } from './diff';

export type ContractSource = 'upload' | 'url' | 'import' | 'editor' | 'ai-patch';

export interface ContractView {
  attached: boolean;
  title?: string;
  version?: string;
  dialect?: string;
  source?: ContractSource;
  fileName?: string | null;
  updatedAt?: string;
  updatedBy?: string | null;
  notifyUrl?: string | null;
  stripPrefix?: string | null;
  prefixes?: string[];
  operations?: { method: string; path: string }[];
}

export interface DriftFinding {
  key: string;
  kind: string;
  label: string;
  side: 'request' | 'response';
  method: string;
  path: string;
  detail: string;
  count: number;
  firstSeen: string;
  lastSeen: string;
  expected: boolean;
  example: { id: string | null; url: string | null; status: number };
}

export interface DriftView {
  attached: boolean;
  lastCheckedAt: string | null;
  scanned: number;
  capped: boolean;
  open: number;
  expected: number;
  lastNotice?: { at: string; findings: number; status: number; error: string | null };
  findings: DriftFinding[];
}

export interface DriftPatch {
  summary: string;
  operations: { op: string; path: string; value?: unknown }[];
  before: string;
  after: string;
}

export type DriftFilter = 'open' | 'expected' | 'all';

export interface FindingGroup {
  kind: string;
  label: string;
  side: 'request' | 'response';
  total: number;
  findings: DriftFinding[];
}

const SOURCE_LABEL: Record<ContractSource, string> = {
  upload: 'uploaded',
  url: 'downloaded from a URL',
  import: 'kept from an OpenAPI import',
  editor: 'edited',
  'ai-patch': 'patched by the assistant',
};

export function sourceLabel(source: ContractSource | undefined): string {
  return source ? SOURCE_LABEL[source] ?? source : '';
}

export function filterFindings(findings: DriftFinding[], filter: DriftFilter): DriftFinding[] {
  if (filter === 'all') return findings;
  return findings.filter((f) => (filter === 'expected' ? f.expected : !f.expected));
}

export function groupFindings(findings: DriftFinding[]): FindingGroup[] {
  const groups = new Map<string, FindingGroup>();
  for (const f of findings) {
    const group = groups.get(f.kind) ?? { kind: f.kind, label: f.label, side: f.side, total: 0, findings: [] };
    group.total += f.count;
    group.findings.push(f);
    groups.set(f.kind, group);
  }
  return [...groups.values()].sort((a, b) => (a.side === b.side ? b.total - a.total : a.side === 'request' ? -1 : 1));
}

export function requestsLink(projectId: string, basePath: string, f: DriftFinding): string {
  const params = new URLSearchParams({ method: f.method });
  const url = f.example.url ?? `${basePath}${f.path}`;
  params.set('q', url.split('?')[0]);
  return `/projects/${projectId}/requests?${params.toString()}`;
}

const MAX_DIFF_LINES = 4000;

export function specDiff(before: string, after: string): DiffLine[] {
  const a = before.split('\n');
  const b = after.split('\n');
  let head = 0;
  while (head < a.length && head < b.length && a[head] === b[head]) head++;
  let tail = 0;
  while (tail < a.length - head && tail < b.length - head && a[a.length - 1 - tail] === b[b.length - 1 - tail]) tail++;
  const midA = a.slice(head, a.length - tail);
  const midB = b.slice(head, b.length - tail);
  const middle: DiffLine[] = midA.length + midB.length > MAX_DIFF_LINES
    ? [...midA.map((text) => ({ kind: 'remove' as const, text })), ...midB.map((text) => ({ kind: 'add' as const, text }))]
    : diffLines(midA.join('\n'), midB.join('\n'));
  return [
    ...a.slice(0, head).map((text) => ({ kind: 'same' as const, text })),
    ...middle,
    ...a.slice(a.length - tail).map((text) => ({ kind: 'same' as const, text })),
  ];
}

export function isSpecFile(name: string): boolean {
  return /\.(json|ya?ml)$/i.test(name);
}
