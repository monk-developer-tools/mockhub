import { FileCode2, LayoutTemplate, Plus, Search } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useHotkeys } from '../../state/hotkeys';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { Stub } from '../../api/types';
import { Confirm } from '../../components/Modal';
import { Skeleton } from '../../components/Skeleton';
import { StubRoute } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import { describeUrl, emptyForm, toStub } from '../../model/stubForm';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';
import { StubEditor } from './StubEditor';
import { TemplateGallery } from './TemplateGallery';

type Meta = { type?: string; folder?: string };
const metaOf = (s: Stub) => ((s.metadata as { mockhub?: Meta } | undefined)?.mockhub ?? {}) as Meta;

const methodOf = (s: Stub) => String((s.request as { method?: string } | undefined)?.method ?? 'ANY');

function folderOf(stub: Stub) {
  const meta = metaOf(stub);
  if (meta.folder) return meta.folder;
  if (meta.type === 'soap') return 'SOAP';
  const first = describeUrl(stub).split('/').find((s) => s && !s.startsWith('{') && !/[.*+?^$()[\]|\\]/.test(s));
  return first ?? 'root';
}

export function StubsTab() {
  const { project, reloadProject } = useProject();
  const { projects, canEdit, refreshProjects } = useApp();
  const { stubId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [stubs, setStubs] = useState<Stub[] | null>(null);
  const [query, setQuery] = useState('');
  const [chip, setChip] = useState('All');
  const [draft, setDraft] = useState<Stub | null>(null);
  const [gallery, setGallery] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [pending, setPending] = useState<(() => void) | null>(null);
  const location = useLocation();
  const searchInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const incoming = (location.state as { draft?: Stub } | null)?.draft;
    if (incoming) {
      setDraft(incoming);
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location.state, location.pathname, navigate]);

  const load = useCallback(async () => {
    try {
      setStubs(await api.stubs(project.id));
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  const guard = (action: () => void) => (dirty ? setPending(() => action) : action());

  const selected = draft ?? stubs?.find((s) => s.id === stubId) ?? null;
  const chips = useMemo(() => {
    const methods = new Set((stubs ?? []).map(methodOf));
    const ordered = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'ANY'].filter((m) => methods.has(m));
    const soap = (stubs ?? []).some((s) => metaOf(s).type === 'soap');
    return ['All', ...ordered, ...(soap ? ['SOAP'] : [])];
  }, [stubs]);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (stubs ?? [])
      .filter((s) => chip === 'All' || (chip === 'SOAP' ? metaOf(s).type === 'soap' : methodOf(s) === chip))
      .filter((s) => !q || JSON.stringify(s).toLowerCase().includes(q));
  }, [stubs, query, chip]);
  const grouped = useMemo(() => {
    const groups = new Map<string, Stub[]>();
    for (const s of filtered) {
      const f = folderOf(s);
      groups.set(f, [...(groups.get(f) ?? []), s]);
    }
    return Array.from(groups.entries());
  }, [filtered]);

  const startDraft = (stub: Stub) =>
    guard(() => {
      setGallery(false);
      setDraft(stub);
      navigate(`/projects/${project.id}/stubs`);
    });

  const select = (id: string) =>
    guard(() => {
      setDraft(null);
      navigate(`/projects/${project.id}/stubs/${id}`);
    });

  const afterSave = async (saved: Stub) => {
    setDraft(null);
    setDirty(false);
    await load();
    reloadProject();
    refreshProjects();
    navigate(`/projects/${project.id}/stubs/${saved.id}`);
  };

  const afterDelete = async () => {
    setDirty(false);
    await load();
    reloadProject();
    refreshProjects();
    navigate(`/projects/${project.id}/stubs`);
  };

  const hotkeys = useMemo(
    () => [
      { key: '/', handler: () => searchInput.current?.focus() },
      ...(canEdit ? [{ key: 'n', handler: () => startDraft(toStub(emptyForm('rest'))) }] : []),
    ],
    [canEdit, dirty],
  );
  useHotkeys(hotkeys);

  if (!stubs) return <div className="panel"><Skeleton rows={8} label="Loading stubs" /></div>;

  return (
    <div className="workspace">
      <div className="panel stub-list">
        <div className="toolbar">
          <div style={{ position: 'relative', flex: 1 }}>
            <Search size={14} style={{ position: 'absolute', left: 9, top: 9, color: 'var(--muted)' }} />
            <input ref={searchInput} type="search" aria-label="Search stubs" placeholder="Search path, name, body…  ( / )" value={query} onChange={(e) => setQuery(e.target.value)} style={{ paddingLeft: 28 }} />
          </div>
          {canEdit && (
            <button className="btn icon" title="Start from a template" aria-label="Start from a template" onClick={() => setGallery(true)}>
              <LayoutTemplate size={15} />
            </button>
          )}
        </div>
        {canEdit && (
          <div className="btn-row" style={{ padding: '8px 10px 0' }}>
            <button className="btn small primary" onClick={() => startDraft(toStub(emptyForm('rest')))}>
              <Plus size={14} /> REST stub
            </button>
            <button className="btn small" onClick={() => startDraft(toStub(emptyForm('soap')))}>
              <Plus size={14} /> SOAP stub
            </button>
          </div>
        )}
        {chips.length > 2 && (
          <div className="chips" role="group" aria-label="Filter stubs">
            {chips.map((c) => (
              <button key={c} className="chip" aria-pressed={chip === c} onClick={() => setChip(c)}>
                {c}
              </button>
            ))}
          </div>
        )}
        <ul aria-label="Stubs">
          {draft && (
            <li>
              <button className="stub-item" aria-current="true">
                <StubRoute stub={draft} />
                <span className="stub-name">New stub, not saved yet</span>
              </button>
            </li>
          )}
          {grouped.map(([folder, list]) => (
            <li key={folder}>
              <div className="folder-label">{folder}</div>
              <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                {list.map((s) => (
                  <li key={String(s.id)}>
                    <button className="stub-item" aria-current={!draft && s.id === stubId} onClick={() => select(String(s.id))}>
                      <StubRoute stub={s} basePath={project.basePath} />
                      <span className="stub-name">
                        {metaOf(s).type === 'soap' && <span className="tag soap">SOAP</span>}
                        {Boolean(s.scenarioName) && <span className="tag scenario">{String(s.scenarioName)}</span>}
                        {s.priority !== undefined && <span className="tag">p{String(s.priority)}</span>}
                        {String(s.name ?? '')}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </li>
          ))}
          {stubs.length > 0 && filtered.length === 0 && <li className="muted small" style={{ padding: 12 }}>No stub matches this filter.</li>}
        </ul>
      </div>

      {selected ? (
        <StubEditor
          key={String(draft ? 'draft' : selected.id)}
          project={project}
          projects={projects}
          stub={selected}
          isNew={Boolean(draft)}
          canEdit={canEdit}
          onSaved={afterSave}
          onDeleted={afterDelete}
          onDirtyChange={setDirty}
        />
      ) : (
        <div className="panel">
          <div className="empty">
            <FileCode2 size={28} className="muted" />
            {stubs.length === 0 ? (
              <>
                <h2>No stubs in this project yet</h2>
                <p>
                  A stub answers requests that match it. Create one from scratch, start from a template, or import an OpenAPI spec,
                  WSDL or WireMock mappings from the Import &amp; export tab.
                </p>
                {canEdit && (
                  <div className="btn-row" style={{ justifyContent: 'center' }}>
                    <button className="btn primary" onClick={() => startDraft(toStub(emptyForm('rest')))}>
                      <Plus size={15} /> REST stub
                    </button>
                    <button className="btn" onClick={() => startDraft(toStub(emptyForm('soap')))}>
                      <Plus size={15} /> SOAP stub
                    </button>
                    <button className="btn" onClick={() => setGallery(true)}>
                      <LayoutTemplate size={15} /> From a template
                    </button>
                    <button className="btn" onClick={() => navigate(`/projects/${project.id}/import-export`)}>
                      Import
                    </button>
                  </div>
                )}
              </>
            ) : (
              <>
                <h2>Select a stub</h2>
                <p>Pick a stub on the left to edit it and try it out.</p>
              </>
            )}
          </div>
        </div>
      )}

      {gallery && <TemplateGallery onPick={startDraft} onClose={() => setGallery(false)} />}
      {pending && (
        <Confirm
          title="Discard unsaved changes?"
          message="The stub you're editing has changes that haven't been saved."
          confirmLabel="Discard changes"
          onConfirm={() => {
            const action = pending;
            setPending(null);
            setDirty(false);
            action();
          }}
          onCancel={() => setPending(null)}
        />
      )}
    </div>
  );
}
