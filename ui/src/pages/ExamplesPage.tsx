import { DownloadSimple } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Example } from '../api/types';
import { Loading } from '../components/Loading';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';

export function ExampleGrid({ compact }: { compact?: boolean }) {
  const { canEdit, refreshProjects, currentOrg } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [examples, setExamples] = useState<Example[] | null>(null);
  const [installing, setInstalling] = useState<string | null>(null);

  useEffect(() => {
    api.examples().then(setExamples).catch(toast.fail);
  }, [toast.fail]);

  const install = async (example: Example) => {
    setInstalling(example.id);
    try {
      const project = await api.installExample(example.id, currentOrg?.id);
      await refreshProjects();
      toast.notify(`Installed “${project.name}”`);
      navigate(`/projects/${project.id}/stubs`);
    } catch (e) {
      toast.fail(e);
    } finally {
      setInstalling(null);
    }
  };

  if (!examples) return <Loading />;
  return (
    <div className="example-list" style={compact ? undefined : { borderTop: 0 }}>
      {examples.map((ex) => (
        <div className="example" key={ex.id}>
          <h3>{ex.name}</h3>
          <div className="btn-row">
            {ex.tags.map((t) => (
              <span key={t} className={`tag${t === 'SOAP' ? ' soap' : ''}`}>{t}</span>
            ))}
          </div>
          <p>{ex.description}</p>
          {canEdit && (
            <div>
              <button className="btn small" onClick={() => install(ex)} disabled={installing !== null}>
                <DownloadSimple size={14} /> {installing === ex.id ? 'Installing…' : 'Install as project'}
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

export function ExamplesPage() {
  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Examples</h1>
          <p className="muted">
            Ready-made projects that show what MockHub can do. Installing one creates a normal project you can edit, call, export to
            Postman or Bruno, and delete when you're done. Each stub has a description of the technique it shows.
          </p>
        </div>
      </div>
      <div className="panel">
        <ExampleGrid />
      </div>
    </div>
  );
}
