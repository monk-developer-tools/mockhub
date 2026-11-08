import { CaretDown, CaretRight, Check, Trash, X } from '@phosphor-icons/react';
import { useState } from 'react';
import type { ProposedChange, RuntimeSettings, Stub } from '../../api/types';
import { StubRoute } from '../../components/StubRoute';
import { collapse, diffLines } from '../../model/diff';
import { runtimeSummary } from '../../model/runtime';

const ACTION_LABEL = {
  create: 'New',
  update: 'Change',
  delete: 'Delete',
  runtime: 'Runtime',
  snapshot: 'Snapshot',
  restore: 'Restore',
  contract: 'Contract',
  grpc: 'gRPC',
  crud: 'CRUD',
};

const SETUP = new Set(['contract', 'grpc', 'crud']);

export function setupLine(change: ProposedChange): string {
  const info = (change.stub ?? {}) as { title?: string; version?: string; name?: string; resource?: string; idField?: string; seed?: unknown[] };
  if (change.action === 'contract') return `Compare traffic with “${info.title ?? ''}”${info.version ? ` ${info.version}` : ''}`;
  if (change.action === 'grpc') return `Serve the services in ${info.name ?? ''}`;
  const seeded = info.seed?.length ?? 0;
  return `/${info.resource ?? ''}: list, get, create, update and delete by ${info.idField ?? 'id'}${seeded ? `, starting with ${seeded} record${seeded === 1 ? '' : 's'}` : ''}`;
}

function setupBody(change: ProposedChange): string {
  const info = (change.stub ?? {}) as { proto?: string; seed?: unknown[]; spec?: unknown };
  if (change.action === 'grpc') return info.proto ?? '';
  if (change.action === 'contract') return JSON.stringify(info.spec ?? {}, null, 2);
  return JSON.stringify(info.seed ?? [], null, 2);
}

function snapshotLine(change: ProposedChange): string {
  const info = (change.stub ?? {}) as { name?: string; purpose?: string; stubCount?: number };
  if (change.action === 'snapshot') return `Save the current state as “${info.name ?? ''}”${info.purpose ? `: ${info.purpose}` : ''}`;
  const count = info.stubCount === undefined ? '' : ` (${info.stubCount} stub${info.stubCount === 1 ? '' : 's'})`;
  return `Go back to “${info.name ?? change.stubId ?? ''}”${count}; the current state is saved first`;
}

export function ChangeCard({ change, basePath, canApply, meta, onApply, onDiscard }: {
  change: ProposedChange;
  basePath: string;
  canApply: boolean;
  meta?: string;
  onApply: () => void;
  onDiscard: () => void;
}) {
  const [open, setOpen] = useState(change.action === 'update' || change.action === 'runtime');
  const snapshotish = change.action === 'snapshot' || change.action === 'restore';
  const shown: Stub = (change.stub ?? change.before ?? {}) as Stub;
  const before = change.before ? JSON.stringify(change.before, null, 2) : '';
  const after = change.stub ? JSON.stringify(change.stub, null, 2) : '';
  return (
    <li className={`change ${change.action}`}>
      <div className="change-head">
        <span className={`change-kind ${change.action}`}>{ACTION_LABEL[change.action]}</span>
        {change.action === 'snapshot' || change.action === 'restore' ? (
          <span className="change-runtime">{snapshotLine(change)}</span>
        ) : SETUP.has(change.action) ? (
          <span className="change-runtime">{setupLine(change)}</span>
        ) : change.action === 'runtime' ? (
          <span className="change-runtime">{runtimeSummary(change.stub as unknown as RuntimeSettings).join(' · ') || 'Turn off every runtime setting'}</span>
        ) : (
          <StubRoute stub={shown} basePath={basePath} />
        )}
      </div>
      {change.summary && <p className="change-summary">{change.summary}</p>}
      {meta && <p className="change-meta muted small">{meta}</p>}
      <div className="btn-row change-actions">
        {!snapshotish && (
          <button className="btn ghost small" aria-expanded={open} onClick={() => setOpen(!open)}>
            {open ? <CaretDown size={13} /> : <CaretRight size={13} />} {change.action === 'update' || change.action === 'runtime' ? 'Diff' : change.action === 'grpc' ? '.proto' : change.action === 'crud' ? 'Records' : 'JSON'}
          </button>
        )}
        <span style={{ flex: 1 }} />
        <button className="btn ghost small" onClick={onDiscard} aria-label={`Discard: ${change.summary}`}>
          {change.action === 'delete' ? <X size={13} /> : <Trash size={13} />} Discard
        </button>
        <button className="btn small" disabled={!canApply} onClick={onApply} aria-label={`Apply: ${change.summary}`}>
          <Check size={13} /> Apply
        </button>
      </div>
      {open && !snapshotish && (SETUP.has(change.action) ? <pre className="diff" aria-label="Details">{setupBody(change)}</pre> : <Diff before={before} after={after} />)}
    </li>
  );
}

export function Diff({ before, after }: { before: string; after: string }) {
  const lines = collapse(diffLines(before, after), before && after ? 3 : 1000);
  return (
    <pre className="diff" aria-label="Changes">
      {lines.map((l, i) =>
        l.kind === 'gap' ? (
          <span key={i} className="diff-gap">⋯ {l.count} unchanged line{l.count === 1 ? '' : 's'}</span>
        ) : (
          <span key={i} className={`diff-${l.kind}`}>{l.kind === 'add' ? '+ ' : l.kind === 'remove' ? '- ' : '  '}{l.text}</span>
        ),
      )}
    </pre>
  );
}
