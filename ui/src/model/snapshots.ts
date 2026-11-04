import type { SnapshotDiff, SnapshotStubStatus } from '../api/types';

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
  return parts;
}

export function contentsLabel(stubCount: number, fileCount: number, crud: string[]): string {
  const parts = [plural(stubCount, 'stub')];
  if (fileCount) parts.push(plural(fileCount, 'file'));
  if (crud.length) parts.push(`CRUD: ${crud.join(', ')}`);
  return parts.join(' · ');
}
