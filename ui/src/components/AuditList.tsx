import { CaretDown, ClockCounterClockwise, DownloadSimple, MagnifyingGlass, PencilSimple, Plus, Trash } from '@phosphor-icons/react';
import { useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { AuditEntry } from '../api/types';
import {
  NO_FILTER,
  actionKind,
  applyFilter,
  clockTime,
  countByKind,
  fullTimestamp,
  groupByDay,
  localTimeZone,
  people,
  toCsv,
  whatHappened,
  type ActionKind,
  type AuditFilter,
  type TimeRange,
} from '../model/audit';
import { timeAgo } from '../model/time';
import { Avatar } from './Avatar';
import { CopyButton } from './CopyButton';

interface Props {
  entries: AuditEntry[];
  retentionDays: number;
  showProject?: boolean;
  scope?: ReactNode;
}

const KINDS: { value: ActionKind | 'all'; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'create', label: 'Created' },
  { value: 'change', label: 'Changed' },
  { value: 'delete', label: 'Deleted' },
];

const KIND_ICONS: Record<ActionKind, typeof Plus> = { create: Plus, change: PencilSimple, delete: Trash };

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;

function download(entries: AuditEntry[]) {
  const url = URL.createObjectURL(new Blob([toCsv(entries)], { type: 'text/csv' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `mockhub-audit-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

export function AuditList({ entries, retentionDays, showProject = false, scope }: Props) {
  const [filter, setFilter] = useState<AuditFilter>(NO_FILTER);
  const [open, setOpen] = useState<string | null>(null);
  const visible = useMemo(() => applyFilter(entries, filter), [entries, filter]);
  const groups = useMemo(() => groupByDay(visible), [visible]);
  const who = useMemo(() => people(entries), [entries]);
  const counts = useMemo(() => countByKind(visible), [visible]);
  const visiblePeople = useMemo(() => new Set(visible.map((e) => e.actorId)).size, [visible]);
  const update = (patch: Partial<AuditFilter>) => setFilter({ ...filter, ...patch });
  const filtered = filter.query !== '' || filter.kind !== 'all' || filter.actorId !== '' || filter.range !== 'all';
  const ranges: { value: TimeRange; label: string }[] = [
    { value: 'day', label: 'Last 24 hours' },
    { value: 'week', label: 'Last 7 days' },
    { value: 'month', label: 'Last 30 days' },
    { value: 'all', label: `All ${retentionDays} days` },
  ];

  if (entries.length === 0) {
    return (
      <div className="audit">
        {scope && <div className="audit-toolbar">{scope}</div>}
        <div className="empty slim">
          <ClockCounterClockwise size={28} weight="duotone" />
          <p>Nothing has changed yet. Every create, change and delete will show up here with who did it and when. Entries are kept for {retentionDays} days.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="audit">
      <div className="audit-toolbar">
        {scope}
        <div className="search-field">
          <MagnifyingGlass size={14} />
          <input type="search" aria-label="Search activity" placeholder="Search by person, stub or project" value={filter.query} onChange={(e) => update({ query: e.target.value })} />
        </div>
        <div className="segmented" role="group" aria-label="Kind of change">
          {KINDS.map((k) => (
            <button key={k.value} type="button" aria-pressed={filter.kind === k.value} onClick={() => update({ kind: k.value })}>
              {k.label}
            </button>
          ))}
        </div>
        <select aria-label="Person" value={filter.actorId} onChange={(e) => update({ actorId: e.target.value })}>
          <option value="">Everyone</option>
          {who.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.count})</option>)}
        </select>
        <select aria-label="Time range" value={filter.range} onChange={(e) => update({ range: e.target.value as TimeRange })}>
          {ranges.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
        </select>
        <button type="button" className="btn small" onClick={() => download(visible)} disabled={visible.length === 0}>
          <DownloadSimple size={14} /> CSV
        </button>
      </div>

      <div className="audit-summary" aria-live="polite">
        <span><strong>{plural(visible.length, 'change')}</strong> by {plural(visiblePeople, 'person').replace('persons', 'people')}</span>
        <span className="audit-count kind-create"><Plus size={12} weight="bold" /> {counts.create} created</span>
        <span className="audit-count kind-change"><PencilSimple size={12} weight="bold" /> {counts.change} changed</span>
        <span className="audit-count kind-delete"><Trash size={12} weight="bold" /> {counts.delete} deleted</span>
        <span className="audit-zone muted">Times in {localTimeZone()} · kept for {retentionDays} days</span>
        {filtered && <button type="button" className="link-button small" onClick={() => setFilter(NO_FILTER)}>Clear filters</button>}
      </div>

      {visible.length === 0 ? (
        <p className="muted small audit-none">Nothing matches these filters.</p>
      ) : (
        groups.map((g) => (
          <section key={g.label} className="audit-day" aria-label={`${g.label}, ${g.date}`}>
            <h3>
              <span className="audit-day-label">{g.label}</span>
              {g.label !== g.date && <span className="audit-day-date">{g.date}</span>}
              <span className="audit-day-count">{plural(g.entries.length, 'change')}</span>
            </h3>
            <ol className="audit-list">
              {g.entries.map((e) => {
                const kind = actionKind(e.action);
                const Icon = KIND_ICONS[kind];
                const expanded = open === e.id;
                const projectTag = showProject && e.projectName && e.projectName !== e.targetName ? e.projectName : '';
                return (
                  <li key={e.id} className={`audit-row kind-${kind}${expanded ? ' open' : ''}`}>
                    <button type="button" className="audit-line" aria-expanded={expanded} onClick={() => setOpen(expanded ? null : e.id)}>
                      <time dateTime={e.at} className="audit-clock mono">{clockTime(e.at)}</time>
                      <span className="audit-dot" aria-hidden="true"><Icon size={11} weight="bold" /></span>
                      <Avatar name={e.actor} seed={e.actorId} size={26} />
                      <span className="audit-text">
                        <span>
                          <strong>{e.actor}</strong> {whatHappened(e)}
                        </span>
                        {(e.details || projectTag) && (
                          <span className="audit-meta small">
                            {projectTag && <span className="audit-project-name">{projectTag}</span>}
                            {e.details && <span className="muted">{e.details}</span>}
                          </span>
                        )}
                      </span>
                      <span className="audit-ago muted small" title={fullTimestamp(e.at)}>{timeAgo(e.at)}</span>
                      <CaretDown size={13} className="audit-caret" aria-hidden="true" />
                    </button>
                    {expanded && (
                      <dl className="audit-details">
                        <dt>When</dt>
                        <dd>{fullTimestamp(e.at)}</dd>
                        <dt>UTC</dt>
                        <dd className="mono">{e.at}</dd>
                        <dt>Who</dt>
                        <dd>{e.actor} <span className="muted mono">{e.actorId}</span></dd>
                        <dt>What</dt>
                        <dd>{e.action} {e.target}{e.targetName && <> · <span className="mono">{e.targetName}</span></>}</dd>
                        {e.details && (
                          <>
                            <dt>Details</dt>
                            <dd>{e.details}</dd>
                          </>
                        )}
                        {e.projectId && e.projectName && (
                          <>
                            <dt>Project</dt>
                            <dd><Link to={`/projects/${e.projectId}/activity`}>{e.projectName}</Link></dd>
                          </>
                        )}
                        <dt>Entry</dt>
                        <dd className="audit-entry-id"><span className="mono muted">{e.id}</span><CopyButton text={e.id} label="Copy entry ID" /></dd>
                      </dl>
                    )}
                  </li>
                );
              })}
            </ol>
          </section>
        ))
      )}
    </div>
  );
}
