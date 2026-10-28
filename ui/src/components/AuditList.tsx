import { ClockCounterClockwise, MagnifyingGlass } from '@phosphor-icons/react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { AuditEntry } from '../api/types';
import { groupByDay, matches, whatHappened } from '../model/audit';
import { timeAgo } from '../model/time';
import { Avatar } from './Avatar';

interface Props {
  entries: AuditEntry[];
  retentionDays: number;
  showProject?: boolean;
}

export function AuditList({ entries, retentionDays, showProject = false }: Props) {
  const [query, setQuery] = useState('');
  const visible = useMemo(() => entries.filter((e) => matches(e, query)), [entries, query]);
  const groups = useMemo(() => groupByDay(visible), [visible]);

  return (
    <div className="audit">
      <div className="audit-bar">
        <div className="search-field">
          <MagnifyingGlass size={14} />
          <input type="search" aria-label="Search activity" placeholder="Search by person, stub or project" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <span className="muted small">Kept for {retentionDays} days</span>
      </div>
      {entries.length === 0 ? (
        <div className="empty slim">
          <ClockCounterClockwise size={28} weight="duotone" />
          <p>Nothing has changed yet. Every create, change and delete will show up here with who did it.</p>
        </div>
      ) : visible.length === 0 ? (
        <p className="muted small">Nothing matches “{query}”.</p>
      ) : (
        groups.map((g) => (
          <section key={g.label} className="audit-day">
            <h3>{g.label}</h3>
            <ol className="audit-list">
              {g.entries.map((e) => (
                <li key={e.id} className={`audit-row action-${e.action.split(' ')[0]}`}>
                  <Avatar name={e.actor} seed={e.actorId} size={28} />
                  <div className="audit-text">
                    <span>
                      <strong>{e.actor}</strong> {whatHappened(e)}
                      {e.details && <span className="muted"> · {e.details}</span>}
                    </span>
                    {showProject && e.projectId && e.projectName && (
                      <Link to={`/projects/${e.projectId}/activity`} className="audit-project small">in {e.projectName}</Link>
                    )}
                  </div>
                  <time dateTime={e.at} title={new Date(e.at).toLocaleString()} className="muted small">{timeAgo(e.at)}</time>
                </li>
              ))}
            </ol>
          </section>
        ))
      )}
    </div>
  );
}
