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

export function ProjectDialog({ project, teamId: presetTeam, onClose }: { project?: Project; teamId?: string; onClose: () => void }) {
  const { info, refreshProjects, orgs, currentOrg, writableTeams, setCurrentOrg, aiStatus } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [name, setName] = useState(project?.name ?? '');
  const [slug, setSlug] = useState(project?.slug ?? '');
  const [slugTouched, setSlugTouched] = useState(Boolean(project));
  const [description, setDescription] = useState(project?.description ?? '');
  const [color, setColor] = useState(project?.color ?? COLORS[0]);
  const [busy, setBusy] = useState(false);
  const [brief, setBrief] = useState('');
  const writableOrgs = orgs.filter((o) => writableTeams(o.id).length > 0);
  const startOrg = project?.orgId ?? (currentOrg && writableTeams(currentOrg.id).length ? currentOrg.id : writableOrgs[0]?.id ?? '');
  const [orgId, setOrgId] = useState(startOrg);
  const teams = writableTeams(orgId);
  const [teamId, setTeamId] = useState(project?.teamId ?? presetTeam ?? writableTeams(startOrg)[0]?.id ?? '');
  const chooseOrg = (id: string) => {
    setOrgId(id);
    setTeamId(writableTeams(id)[0]?.id ?? '');
  };
  const effectiveSlug = slugTouched ? slug : slugify(name);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const body = { name, slug: effectiveSlug, description, color, orgId, teamId };
      const saved = project ? await api.updateProject(project.id, body) : await api.createProject(body);
      await refreshProjects();
      if (orgId && orgId !== currentOrg?.id) setCurrentOrg(orgId);
      toast.notify(project ? 'Project saved' : 'Project created');
      onClose();
      if (!project && brief.trim()) navigate(`/projects/${saved.id}/assistant`, { state: { prompt: brief.trim() } });
      else if (!project) navigate(`/projects/${saved.id}/stubs`);
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
        <div className="grid two">
          {writableOrgs.length > 1 && (
            <label className="field">
              <span>Organisation</span>
              <select value={orgId} onChange={(e) => chooseOrg(e.target.value)}>
                {writableOrgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
              </select>
            </label>
          )}
          <label className="field">
            <span>Team</span>
            <select value={teamId} onChange={(e) => setTeamId(e.target.value)} required>
              {teams.length === 0 && <option value="">No team you can edit</option>}
              {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
            <span className="hint">The team's people see it, plus anyone with access to the whole organisation.</span>
          </label>
        </div>
        {project && (project.orgId !== orgId || project.teamId !== teamId) && (
          <span className="hint">Moves the project. Its mock URL stays the same.</span>
        )}
        <label className="field">
          <span>Description</span>
          <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What does this service do?" />
        </label>
        {!project && aiStatus?.enabled && (
          <label className="field">
            <span>Describe the API <span className="hint">optional, the AI assistant drafts the stubs</span></span>
            <textarea
              rows={3}
              value={brief}
              onChange={(e) => setBrief(e.target.value)}
              placeholder="e.g. Payments API: POST /payments creates a payment, GET /payments/{id} returns it, 404 for unknown ids. Or paste an OpenAPI spec."
            />
          </label>
        )}
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
          <button type="submit" className="btn primary" disabled={busy || !teamId}>
            {project ? 'Save project' : 'Create project'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
