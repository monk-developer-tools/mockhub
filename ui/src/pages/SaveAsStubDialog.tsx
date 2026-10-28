import { FloppyDisk } from '@phosphor-icons/react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { PlaygroundEntry } from '../api/types';
import { Modal } from '../components/Modal';
import { useToast } from '../components/Toasts';
import { projectFor, relativePath, stubFromEntry } from '../model/playground';
import { useApp } from '../state/AppContext';

export function SaveAsStubDialog({ entry, onClose }: { entry: PlaygroundEntry; onClose: () => void }) {
  const { orgProjects, roleInTeam, refreshProjects } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const writable = orgProjects.filter((p) => ['editor', 'owner', 'admin'].includes(roleInTeam(p.orgId, p.teamId)));
  const guessed = projectFor(entry.request.url, window.location.origin, writable);
  const [projectId, setProjectId] = useState(guessed?.id ?? writable[0]?.id ?? '');
  const project = writable.find((p) => p.id === projectId);
  const [path, setPath] = useState(relativePath(entry.request.url, guessed));
  const [name, setName] = useState(`${entry.request.method} ${relativePath(entry.request.url, guessed)}`);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (!project) return;
    setBusy(true);
    try {
      const stub = await api.createStub(project.id, stubFromEntry(entry, path.startsWith('/') ? path : `/${path}`, name.trim()));
      toast.notify(`Stub saved in ${project.name}`);
      await refreshProjects();
      onClose();
      navigate(`/projects/${project.id}/stubs/${stub.id}`);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title="Save as stub"
      description={`The stub answers ${entry.request.method} requests on this path with the ${entry.response?.status ?? ''} response you just got.`}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" onClick={save} disabled={busy || !project || !path.trim()}>
            <FloppyDisk size={14} /> {busy ? 'Saving…' : 'Save stub'}
          </button>
        </>
      }
    >
      {writable.length === 0 ? (
        <p className="muted">You need the editor role in a project to save stubs. Ask a team owner to add you.</p>
      ) : (
        <div className="stack">
          <label className="field">
            <span>Project</span>
            <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
              {writable.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.basePath || '/'})</option>)}
            </select>
          </label>
          <label className="field">
            <span>Path <span className="hint">relative to {project?.basePath || (project ? '/' : 'the project')}</span></span>
            <input type="text" className="mono" value={path} onChange={(e) => setPath(e.target.value)} />
          </label>
          <label className="field">
            <span>Name</span>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          {project && <p className="muted small mono">{project.mockUrl}{path.startsWith('/') ? path : `/${path}`}</p>}
        </div>
      )}
    </Modal>
  );
}
