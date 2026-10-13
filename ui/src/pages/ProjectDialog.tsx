import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Project } from '../api/types';
import { Modal } from '../components/Modal';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';

export function slugify(name: string) {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64);
}

const COLORS = ['#6366f1', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6'];

export function ProjectDialog({ project, onClose }: { project?: Project; onClose: () => void }) {
  const { info, refreshProjects } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [name, setName] = useState(project?.name ?? '');
  const [slug, setSlug] = useState(project?.slug ?? '');
  const [slugTouched, setSlugTouched] = useState(Boolean(project));
  const [description, setDescription] = useState(project?.description ?? '');
  const [color, setColor] = useState(project?.color ?? COLORS[0]);
  const [busy, setBusy] = useState(false);
  const effectiveSlug = slugTouched ? slug : slugify(name);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const body = { name, slug: effectiveSlug, description, color };
      const saved = project ? await api.updateProject(project.id, body) : await api.createProject(body);
      await refreshProjects();
      toast.notify(project ? 'Project saved' : 'Project created');
      onClose();
      if (!project) navigate(`/projects/${saved.id}/stubs`);
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={project ? 'Edit project' : 'New project'}
      description="A project groups the mocks of one service. Each project gets its own base URL."
      onClose={onClose}
    >
      <form onSubmit={submit} className="stack">
        <label className="field">
          <span>Name</span>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Payment service" required />
        </label>
        <label className="field">
          <span>
            Base path <span className="hint">lowercase letters, digits and dashes</span>
          </span>
          <input
            type="text"
            className="mono"
            value={effectiveSlug}
            onChange={(e) => {
              setSlugTouched(true);
              setSlug(e.target.value);
            }}
            pattern="[a-z0-9]([a-z0-9\-]*[a-z0-9])?"
            required
          />
          <span className="hint mono">
            {info?.baseUrl ?? ''}/{effectiveSlug || '…'}/
          </span>
        </label>
        {project && project.slug !== effectiveSlug && (
          <div className="callout warn">Changing the base path moves every mock in this project to the new URL.</div>
        )}
        <label className="field">
          <span>Description</span>
          <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What does this service do?" />
        </label>
        <div className="field">
          <span>Colour</span>
          <div className="btn-row" role="radiogroup" aria-label="Colour">
            {COLORS.map((c) => (
              <button
                key={c}
                type="button"
                role="radio"
                aria-checked={color === c}
                aria-label={c}
                onClick={() => setColor(c)}
                style={{
                  width: 24,
                  height: 24,
                  borderRadius: '50%',
                  background: c,
                  border: color === c ? '3px solid var(--ink)' : '2px solid transparent',
                  cursor: 'pointer',
                }}
              />
            ))}
          </div>
        </div>
        <div className="modal-foot" style={{ padding: 0 }}>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy}>
            {project ? 'Save project' : 'Create project'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
