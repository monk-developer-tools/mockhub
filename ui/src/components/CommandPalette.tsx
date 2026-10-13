import { BookOpen, CornerDownLeft, FileCode2, FolderOpen, LayoutGrid, Plus, Search, Server, Keyboard, SunMoon } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Project, Stub } from '../api/types';
import { fuzzyFilter } from '../model/fuzzy';
import { describeUrl } from '../model/stubForm';
import { MethodJack } from './StubRoute';

export interface Command {
  id: string;
  group: 'Go to' | 'Projects' | 'Stubs' | 'Actions';
  title: string;
  hint?: string;
  icon?: ReactNode;
  keywords?: string;
  run: () => void;
}

interface Props {
  projects: Project[];
  canEdit: boolean;
  onClose: () => void;
  onNewProject: () => void;
  onToggleTheme: () => void;
  onShowShortcuts: () => void;
}

const GROUP_ORDER: Command['group'][] = ['Actions', 'Go to', 'Projects', 'Stubs'];

export function CommandPalette({ projects, canEdit, onClose, onNewProject, onToggleTheme, onShowShortcuts }: Props) {
  const navigate = useNavigate();
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLUListElement>(null);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const [stubs, setStubs] = useState<{ project: Project; stub: Stub }[]>([]);

  useEffect(() => {
    input.current?.focus();
    let cancelled = false;
    Promise.all(projects.map((p) => api.stubs(p.id).then((list) => list.map((stub) => ({ project: p, stub }))).catch(() => [])))
      .then((all) => !cancelled && setStubs(all.flat()));
    return () => {
      cancelled = true;
    };
  }, [projects]);

  const commands = useMemo<Command[]>(() => {
    const go = (path: string) => () => {
      navigate(path);
      onClose();
    };
    const result: Command[] = [
      { id: 'go-projects', group: 'Go to', title: 'Projects', icon: <LayoutGrid size={15} />, run: go('/') },
      { id: 'go-examples', group: 'Go to', title: 'Examples', icon: <BookOpen size={15} />, run: go('/examples') },
      { id: 'go-server', group: 'Go to', title: 'Server settings', icon: <Server size={15} />, run: go('/server') },
      { id: 'theme', group: 'Actions', title: 'Switch theme', icon: <SunMoon size={15} />, run: () => { onToggleTheme(); onClose(); } },
      { id: 'shortcuts', group: 'Actions', title: 'Keyboard shortcuts', icon: <Keyboard size={15} />, run: () => { onClose(); onShowShortcuts(); } },
    ];
    if (canEdit) {
      result.unshift({ id: 'new-project', group: 'Actions', title: 'New project', icon: <Plus size={15} />, run: () => { onClose(); onNewProject(); } });
    }
    for (const p of projects) {
      result.push({
        id: `p-${p.id}`,
        group: 'Projects',
        title: p.name,
        hint: p.basePath,
        icon: <span className="swatch" style={{ background: p.color }} />,
        keywords: p.slug,
        run: go(`/projects/${p.id}`),
      });
      for (const [tab, label] of [['stubs', 'Stubs'], ['requests', 'Requests'], ['import-export', 'Import & export']]) {
        result.push({
          id: `p-${p.id}-${tab}`,
          group: 'Go to',
          title: `${p.name} › ${label}`,
          icon: <FolderOpen size={15} />,
          run: go(`/projects/${p.id}/${tab}`),
        });
      }
    }
    for (const { project, stub } of stubs) {
      const method = String((stub.request as { method?: string } | undefined)?.method ?? 'ANY');
      const url = describeUrl(stub);
      result.push({
        id: `s-${stub.id}`,
        group: 'Stubs',
        title: `${method} ${url}`,
        hint: `${String(stub.name ?? '')} · ${project.name}`,
        icon: <MethodJack method={method} />,
        keywords: `${stub.name ?? ''} ${project.slug}`,
        run: go(`/projects/${project.id}/stubs/${stub.id}`),
      });
    }
    return result;
  }, [projects, stubs, canEdit, navigate, onClose, onNewProject, onToggleTheme, onShowShortcuts]);

  const visible = useMemo(() => {
    const filtered = query.trim()
      ? fuzzyFilter(commands, query, (c) => `${c.title} ${c.hint ?? ''} ${c.keywords ?? ''}`, 40)
      : commands.filter((c) => c.group !== 'Stubs' && !c.title.includes(' › ')).slice(0, 20);
    return query.trim() ? filtered : GROUP_ORDER.flatMap((g) => filtered.filter((c) => c.group === g));
  }, [commands, query]);

  useEffect(() => setActive(0), [query]);
  useEffect(() => {
    list.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [active]);

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((a) => Math.min(visible.length - 1, a + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => Math.max(0, a - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      visible[active]?.run();
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  let lastGroup = '';
  return (
    <div className="backdrop palette-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="palette" role="dialog" aria-modal="true" aria-label="Command palette">
        <div className="palette-input">
          <Search size={16} />
          <input
            ref={input}
            type="text"
            role="combobox"
            aria-expanded="true"
            aria-controls="palette-list"
            aria-activedescendant={visible[active] ? `cmd-${visible[active].id}` : undefined}
            placeholder="Search projects, stubs and actions…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
          />
          <kbd>esc</kbd>
        </div>
        <ul className="palette-list" id="palette-list" role="listbox" ref={list}>
          {visible.length === 0 && <li className="palette-empty">Nothing matches “{query}”.</li>}
          {visible.map((c, i) => {
            const header = !query.trim() && c.group !== lastGroup ? c.group : null;
            lastGroup = c.group;
            return (
              <li key={c.id} role="presentation">
                {header && <div className="palette-group">{header}</div>}
                <div
                  id={`cmd-${c.id}`}
                  role="option"
                  aria-selected={i === active}
                  data-index={i}
                  className="palette-item"
                  onMouseMove={() => setActive(i)}
                  onClick={c.run}
                >
                  <span className="palette-icon">{c.icon ?? <FileCode2 size={15} />}</span>
                  <span className="palette-title">{c.title}</span>
                  {c.hint && <span className="palette-hint">{c.hint}</span>}
                  {i === active && <CornerDownLeft size={14} className="muted" />}
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
