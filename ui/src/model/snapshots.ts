import type { SnapshotDiff, SnapshotStubStatus, ProjectEnvironment } from '../api/types';

export const STATUS_LABEL: Record<SnapshotStubStatus, string> = {
  added: 'Added',
  removed: 'Removed',
  changed: 'Changed',
  moved: 'Moved',
};

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;

export function diffHeadline(diff: SnapshotDiff): string[] {
  if (diff.identical) return ['No differences'];
  const parts: string[] = [];
  const moved = diff.stubs.filter((s) => s.status === 'moved').length;
  const changed = diff.counts.changed - moved;
  if (diff.counts.added) parts.push(`${plural(diff.counts.added, 'stub')} added`);
  if (changed) parts.push(`${plural(changed, 'stub')} changed`);
  if (moved) parts.push(`${plural(moved, 'stub')} moved in the matching order`);
  if (diff.counts.removed) parts.push(`${plural(diff.counts.removed, 'stub')} removed`);
  const files = diff.files.added.length + diff.files.removed.length + diff.files.changed.length;
  if (files) parts.push(`${plural(files, 'file')} different`);
  if (diff.runtime.changed) parts.push('runtime behaviour different');
  if (diff.apiKeyHeader.changed) parts.push('API key header different');
  if (diff.environments?.changed) parts.push('environments different');
  return parts;
}

export function environmentChanges(before: ProjectEnvironment[] | null | undefined, after: ProjectEnvironment[] | null | undefined): string[] {
  const was = new Map((before ?? []).map((e) => [e.name, e]));
  const now = new Map((after ?? []).map((e) => [e.name, e]));
  const lines: string[] = [];
  for (const [name, e] of now) {
    const old = was.get(name);
    if (!old) {
      lines.push(`${name}: added`);
      continue;
    }
    const keys = [...new Set([...Object.keys(old.variables), ...Object.keys(e.variables)])].filter((k) => old.variables[k] !== e.variables[k]).sort();
    if (keys.length) lines.push(`${name}: ${keys.join(', ')} different`);
  }
  for (const name of was.keys()) if (!now.has(name)) lines.push(`${name}: removed`);
  return lines;
}

export function contentsLabel(stubCount: number, fileCount: number, crud: string[]): string {
  const parts = [plural(stubCount, 'stub')];
  if (fileCount) parts.push(plural(fileCount, 'file'));
  if (crud.length) parts.push(`CRUD: ${crud.join(', ')}`);
  return parts.join(' · ');
}
