import { BookOpen, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CopyButton } from '../components/CopyButton';
import { useApp } from '../state/AppContext';
import { ExampleGrid } from './ExamplesPage';
import { ProjectDialog } from './ProjectDialog';

export function DashboardPage() {
  const { projects, canEdit, info } = useApp();
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Projects</h1>
          <p className="muted">
            Each project serves its mocks under its own base path on <span className="mono">{info?.baseUrl}</span>. Point your service
            or test at that URL instead of the real dependency.
          </p>
        </div>
        {canEdit && (
          <button className="btn primary" onClick={() => setCreating(true)}>
            <Plus size={16} /> New project
          </button>
        )}
      </div>

      {projects.length === 0 ? (
        <div className="panel">
          <div className="empty">
            <h2>Create your first mock project</h2>
            <p>
              Start empty, import an OpenAPI spec, WSDL or WireMock mappings, or install an example below to see how REST, SOAP,
              templating and stateful mocks look.
            </p>
            {canEdit && (
              <button className="btn primary" onClick={() => setCreating(true)}>
                <Plus size={16} /> New project
              </button>
            )}
          </div>
          <ExampleGrid compact />
        </div>
      ) : (
        <div className="stack">
          <div className="panel">
            <table className="project-table">
              <thead>
                <tr>
                  <th>Project</th>
                  <th>Mock URL</th>
                  <th className="hide-sm">Stubs</th>
                  <th className="hide-sm">Requests</th>
                </tr>
              </thead>
              <tbody>
                {projects.map((p) => (
                  <tr key={p.id} className="clickable" onClick={() => navigate(`/projects/${p.id}/stubs`)}>
                    <td>
                      <div className="project-title">
                        <span className="swatch" style={{ background: p.color }} />
                        <Link to={`/projects/${p.id}/stubs`} onClick={(e) => e.stopPropagation()}>
                          {p.name}
                        </Link>
                      </div>
                      {p.description && <p className="muted small">{p.description}</p>}
                    </td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <span className="btn-row" style={{ flexWrap: 'nowrap' }}>
                        <span className="mono small">{p.mockUrl}</span>
                        <CopyButton text={p.mockUrl} label="Copy mock URL" />
                      </span>
                    </td>
                    <td className="hide-sm num">
                      {p.stubCount}
                      {p.soapStubCount > 0 && <span className="tag soap" style={{ marginLeft: 8 }}>{p.soapStubCount} SOAP</span>}
                    </td>
                    <td className="hide-sm num">{p.requestCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="muted small">
            <BookOpen size={13} style={{ verticalAlign: '-2px' }} /> New to mocking with WireMock? <Link to="/examples">Install an example project</Link> and copy what you need.
          </p>
        </div>
      )}
      {creating && <ProjectDialog onClose={() => setCreating(false)} />}
    </div>
  );
}
