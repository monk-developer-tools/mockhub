import { CaretDown, CaretRight, Check, Trash, X } from '@phosphor-icons/react';
import { useState } from 'react';
import type { ProposedChange, RuntimeSettings, Stub } from '../../api/types';
import { StubRoute } from '../../components/StubRoute';
import { collapse, diffLines } from '../../model/diff';
import { runtimeSummary } from '../../model/runtime';

const ACTION_LABEL = { create: 'New', update: 'Change', delete: 'Delete', runtime: 'Runtime' };

export function ChangeCard({ change, basePath, canApply, meta, onApply, onDiscard }: {
  change: ProposedChange;
  basePath: string;
  canApply: boolean;
  meta?: string;
  onApply: () => void;
  onDiscard: () => void;
}) {
  const [open, setOpen] = useState(change.action === 'update' || change.action === 'runtime');
  const shown: Stub = (change.stub ?? change.before ?? {}) as Stub;
  const before = change.before ? JSON.stringify(change.before, null, 2) : '';
  const after = change.stub ? JSON.stringify(change.stub, null, 2) : '';
  return (
    <li className={`change ${change.action}`}>
      <div className="change-head">
        <span className={`change-kind ${change.action}`}>{ACTION_LABEL[change.action]}</span>
        {change.action === 'runtime' ? (
          <span className="change-runtime">{runtimeSummary(change.stub as unknown as RuntimeSettings).join(' · ') || 'Turn off every runtime setting'}</span>
        ) : (
          <StubRoute stub={shown} basePath={basePath} />
        )}
      </div>
      {change.summary && <p className="change-summary">{change.summary}</p>}
      {meta && <p className="change-meta muted small">{meta}</p>}
      <div className="btn-row change-actions">
        <button className="btn ghost small" aria-expanded={open} onClick={() => setOpen(!open)}>
          {open ? <CaretDown size={13} /> : <CaretRight size={13} />} {change.action === 'update' || change.action === 'runtime' ? 'Diff' : 'JSON'}
        </button>
        <span style={{ flex: 1 }} />
        <button className="btn ghost small" onClick={onDiscard} aria-label={`Discard: ${change.summary}`}>
          {change.action === 'delete' ? <X size={13} /> : <Trash size={13} />} Discard
        </button>
        <button className="btn small" disabled={!canApply} onClick={onApply} aria-label={`Apply: ${change.summary}`}>
          <Check size={13} /> Apply
        </button>
      </div>
      {open && <Diff before={before} after={after} />}
    </li>
  );
}

function Diff({ before, after }: { before: string; after: string }) {
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
