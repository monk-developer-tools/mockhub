import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { Confirm } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { useApp } from '../../state/AppContext';
import { ProjectDialog } from '../ProjectDialog';
import { useProject } from './ProjectPage';

export function ProjectSettingsTab() {
  const { project, reloadProject } = useProject();
  const { canEdit, refreshProjects } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const remove = async () => {
    try {
      await api.deleteProject(project.id);
      await refreshProjects();
      toast.notify('Project deleted');
      navigate('/');
    } catch (e) {
      toast.fail(e);
    }
  };

  return (
    <div className="stack" style={{ maxWidth: 760 }}>
      <div className="panel">
        <div className="section">
          <h3>
            Details
            {canEdit && <button className="btn small" onClick={() => setEditing(true)}>Edit</button>}
          </h3>
          <table className="kv-table">
            <tbody>
              <tr><td>Name</td><td>{project.name}</td></tr>
              <tr><td>Base path</td><td>{project.basePath}</td></tr>
              <tr><td>Mock URL</td><td>{project.mockUrl}</td></tr>
              <tr><td>Created</td><td>{new Date(project.createdAt).toLocaleString()}</td></tr>
              <tr><td>Updated</td><td>{new Date(project.updatedAt).toLocaleString()}</td></tr>
            </tbody>
          </table>
        </div>
        <div className="section">
          <h3>Use it from your service</h3>
          <p className="muted small" style={{ marginBottom: 8 }}>
            Replace the real dependency's base URL with the mock URL, for example in your test environment's config:
          </p>
          <pre className="block">{`PAYMENT_API_URL=${project.mockUrl}`}</pre>
        </div>
      </div>
      {canEdit && (
        <div className="panel">
          <div className="section">
            <h3>Delete project</h3>
            <p className="muted small" style={{ marginBottom: 10 }}>
              Deletes the project and its {project.stubCount} stubs. Download a WireMock export first if you might need them again.
            </p>
            <button className="btn danger" onClick={() => setDeleting(true)}>Delete project</button>
          </div>
        </div>
      )}
      {editing && (
        <ProjectDialog
          project={project}
          onClose={() => {
            setEditing(false);
            reloadProject();
          }}
        />
      )}
      {deleting && (
        <Confirm
          title={`Delete ${project.name}?`}
          message={`This removes the project and all ${project.stubCount} stubs under ${project.basePath}. It can't be undone.`}
          confirmLabel="Delete project"
          onConfirm={remove}
          onCancel={() => setDeleting(false)}
        />
      )}
    </div>
  );
}
