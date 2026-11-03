import {
  ArrowBendDownLeft,
  BracketsCurly,
  CalendarBlank,
  CaretDown,
  Database,
  GitBranch,
  ListBullets,
  MagnifyingGlass,
  PlusMinus,
  Scales,
  Shuffle,
  Sparkle,
  Tag,
  TextAa,
  type Icon,
} from '@phosphor-icons/react';
import { useCallback, useMemo, useRef, useState } from 'react';
import { dynamicValues, searchDynamicValues, type DynamicScope } from '../model/dynamicValues';
import { useDismiss } from '../state/useDismiss';

const ICONS: Record<string, Icon> = {
  request: Tag,
  response: ArrowBendDownLeft,
  control: GitBranch,
  data: BracketsCurly,
  state: Database,
  compare: Scales,
  string: TextAa,
  math: PlusMinus,
  date: CalendarBlank,
  array: ListBullets,
  random: Shuffle,
  faker: Sparkle,
};

interface Props {
  scope: DynamicScope;
  onInsert: (expression: string) => void;
  label?: string;
}

export function DynamicValueMenu({ scope, onInsert, label = 'Insert dynamic value' }: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const groups = useMemo(() => dynamicValues(scope), [scope]);
  const shown = useMemo(() => searchDynamicValues(groups, query), [groups, query]);
  const searching = query.trim().length > 0;

  const close = useCallback(() => setOpen(false), []);
  useDismiss(ref, open, close);

  const pick = (expression: string) => {
    setOpen(false);
    setQuery('');
    onInsert(expression);
  };

  return (
    <div className="dyn-menu" ref={ref}>
      <button type="button" className="btn small" aria-expanded={open} aria-haspopup="dialog" onClick={() => setOpen(!open)}>
        <Tag size={14} /> {label} <CaretDown size={12} />
      </button>
      {open && (
        <div className="dyn-pop" role="dialog" aria-label="Dynamic values">
          <label className="dyn-search">
            <MagnifyingGlass size={14} aria-hidden="true" />
            <input
              type="search"
              autoFocus
              placeholder="Search, e.g. header, uuid, date"
              aria-label="Search dynamic values"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
          <div className="dyn-groups">
            {shown.length === 0 && <p className="muted small dyn-empty">Nothing matches “{query}”.</p>}
            {shown.map((g) => {
              const Icon = ICONS[g.id] ?? BracketsCurly;
              const isOpen = searching || expanded === g.id;
              return (
                <div key={g.id} className="dyn-group">
                  <button
                    type="button"
                    className="dyn-group-head"
                    aria-expanded={isOpen}
                    onClick={() => setExpanded(expanded === g.id ? null : g.id)}
                  >
                    <Icon size={15} aria-hidden="true" />
                    <span>{g.name}</span>
                    <CaretDown size={13} className="dyn-caret" aria-hidden="true" />
                  </button>
                  {isOpen && (
                    <ul className="dyn-items" aria-label={g.name}>
                      {g.items.map((i) => (
                        <li key={i.label}>
                          <button type="button" className="dyn-item" onClick={() => pick(i.expression)} title={i.hint ?? i.expression}>
                            <span>{i.label}</span>
                            <code>{i.expression}</code>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>
          <p className="muted small dyn-foot">
            Inserted at the cursor. Any <a href="https://wiremock.org/docs/response-templating/" target="_blank" rel="noreferrer">WireMock helper</a> and
            Datafaker expression works.
          </p>
        </div>
      )}
    </div>
  );
}
