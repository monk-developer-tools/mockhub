import { CaretDown, CaretRight } from '@phosphor-icons/react';
import { useState } from 'react';
import type { SnapshotDiff } from '../../../api/types';
import { StubRoute } from '../../../components/StubRoute';
import { runtimeSummary } from '../../../model/runtime';
import { diffHeadline, STATUS_LABEL } from '../../../model/snapshots';
import { Diff } from '../ChangeCard';

type StubChange = SnapshotDiff['stubs'][number];

function json(value: unknown): string {
  return value ? JSON.stringify(value, null, 2) : '';
}

function StubRow({ change, basePath }: { change: StubChange; basePath: string }) {
  const [open, setOpen] = useState(false);
  const stub = change.after ?? change.before;
  return (
    <li className={`snap-change ${change.status}`}>
      <div className="snap-change-head">
        <span className={`snap-status ${change.status}`}>{STATUS_LABEL[change.status]}</span>
        {stub ? <StubRoute stub={stub} basePath={basePath} /> : <span className="mono">{change.method} {change.url}</span>}
        {change.name && <span className="muted small snap-name">{change.name}</span>}
        <button type="button" className="btn ghost small" aria-expanded={open} aria-label={`${open ? 'Hide' : 'Show'} changes to ${change.name || change.url}`}
          onClick={() => setOpen(!open)}>
          {open ? <CaretDown size={13} /> : <CaretRight size={13} />}
        </button>
      </div>
      {open && <Diff before={json(change.before)} after={json(change.after)} />}
    </li>
  );
}

export function SnapshotDiffView({ diff, basePath }: { diff: SnapshotDiff; basePath: string }) {
  const runtimeBefore = diff.runtime.before ? runtimeSummary(diff.runtime.before).join(' · ') || 'nothing set' : 'nothing set';
  const runtimeAfter = diff.runtime.after ? runtimeSummary(diff.runtime.after).join(' · ') || 'nothing set' : 'nothing set';
  const files = [
    ...diff.files.added.map((f) => ({ name: f, status: 'added' as const })),
    ...diff.files.changed.map((f) => ({ name: f, status: 'changed' as const })),
    ...diff.files.removed.map((f) => ({ name: f, status: 'removed' as const })),
  ];
  return (
    <div className="snap-diff stack">
      <p className="snap-headline" role="status">{diffHeadline(diff).join(' · ')}</p>
      {diff.stubs.length > 0 && (
        <ul className="snap-changes" aria-label="Stub changes">
          {diff.stubs.map((c) => <StubRow key={c.id} change={c} basePath={basePath} />)}
        </ul>
      )}
      {files.length > 0 && (
        <div>
          <h3>Response files</h3>
          <ul className="snap-files">
            {files.map((f) => (
              <li key={f.name}><span className={`snap-status ${f.status}`}>{STATUS_LABEL[f.status]}</span> <span className="mono">{f.name}</span></li>
            ))}
          </ul>
        </div>
      )}
      {diff.runtime.changed && (
        <div>
          <h3>Runtime behaviour</h3>
          <p className="small"><span className="muted">{diff.from}:</span> {runtimeBefore}</p>
          <p className="small"><span className="muted">{diff.to}:</span> {runtimeAfter}</p>
        </div>
      )}
      {diff.apiKeyHeader.changed && (
        <p className="small">
          API key header: <span className="mono">{diff.apiKeyHeader.before ?? 'none'}</span> in {diff.from},{' '}
          <span className="mono">{diff.apiKeyHeader.after ?? 'none'}</span> in {diff.to}. Restoring doesn't change the API key.
        </p>
      )}
    </div>
  );
}
