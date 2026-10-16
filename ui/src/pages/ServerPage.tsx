import { DownloadSimple, Record, Stop } from '@phosphor-icons/react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Stub } from '../api/types';
import { StubRoute } from '../components/StubRoute';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';

export function ServerPage() {
  const { info, projects, canEdit, isAdmin, refreshProjects } = useApp();
  const toast = useToast();
  const [unassigned, setUnassigned] = useState<Stub[]>([]);
  const [target, setTarget] = useState('');
  const [delay, setDelay] = useState('');
  const [recordTarget, setRecordTarget] = useState('');
  const [recordProject, setRecordProject] = useState('');
  const [recording, setRecording] = useState(false);

  const load = useCallback(async () => {
    try {
      const [stubs, settings, status] = await Promise.all([api.unassignedStubs(), api.globalSettings(), api.recordingStatus()]);
      setUnassigned(stubs);
      setDelay(settings.settings.fixedDelay ? String(settings.settings.fixedDelay) : '');
      setRecording(status.status === 'Recording');
    } catch (e) {
      toast.fail(e);
    }
  }, [toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!target && projects[0]) setTarget(projects[0].id);
    if (!recordProject && projects[0]) setRecordProject(projects[0].id);
  }, [projects, target, recordProject]);

  const adopt = async (stub: Stub, prefix: boolean) => {
    try {
      await api.adoptStub(target, String(stub.id), prefix);
      toast.notify('Stub added to project');
      await Promise.all([load(), refreshProjects()]);
    } catch (e) {
      toast.fail(e);
    }
  };

  const saveDelay = async () => {
    try {
      await api.updateGlobalSettings({ fixedDelay: delay ? Number(delay) : null });
      toast.notify(delay ? `Every response now waits at least ${delay} ms` : 'Global delay removed');
    } catch (e) {
      toast.fail(e);
    }
  };

  const startRecording = async () => {
    try {
      await api.startRecording({ targetBaseUrl: recordTarget, persist: true, repeatsAsScenarios: false });
      setRecording(true);
      toast.notify('Recording. Send requests to the server root to capture them.');
    } catch (e) {
      toast.fail(e);
    }
  };

  const stopRecording = async () => {
    try {
      const result = await api.stopRecording();
      setRecording(false);
      for (const m of result.mappings) await api.adoptStub(recordProject, String(m.id), true);
      toast.notify(`Captured ${result.mappings.length} stubs`);
      await Promise.all([load(), refreshProjects()]);
    } catch (e) {
      toast.fail(e);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Server</h1>
          <p className="muted">Settings that apply to every project on this MockHub.</p>
        </div>
      </div>
      <div className="split">
        <div className="stack">
          <div className="panel">
            <div className="section">
              <h3>About</h3>
              <table className="kv-table">
                <tbody>
                  <tr><td>MockHub</td><td>{info?.version}</td></tr>
                  <tr><td>WireMock</td><td>{info?.wiremockVersion}</td></tr>
                  <tr><td>Public URL</td><td>{info?.baseUrl}</td></tr>
                  <tr><td>Data directory</td><td>{info?.dataDir}</td></tr>
                  <tr><td>Request log size</td><td>{info?.journalMaxEntries} requests</td></tr>
                  <tr><td>Sign-in</td><td>{info?.authEnabled ? 'required' : 'off (create the first admin under Users)'}</td></tr>
                  <tr><td>Native admin API</td><td><a href="/__admin/docs" target="_blank" rel="noreferrer">/__admin</a></td></tr>
                </tbody>
              </table>
            </div>
            <div className="section">
              <h3>Global delay</h3>
              <p className="muted small" style={{ marginBottom: 8 }}>Adds a fixed delay to every response from every project, e.g. to simulate a slow network.</p>
              <div className="btn-row">
                <input type="number" min={0} placeholder="ms" value={delay} disabled={!isAdmin} onChange={(e) => setDelay(e.target.value)} style={{ width: 120 }} />
                {isAdmin && <button className="btn" onClick={saveDelay}>Apply</button>}
              </div>
            </div>
          </div>

          {isAdmin && (
            <div className="panel">
              <div className="section">
                <h3>Backup</h3>
                <p className="muted small" style={{ marginBottom: 10 }}>
                  Download everything as one file, or choose organisations and projects to back up and restore under Backup &amp; restore.
                </p>
                <div className="btn-row">
                  <button className="btn" onClick={() => api.backup({ everything: true, organizations: [], projects: [] }).catch(toast.fail)}>
                    <DownloadSimple size={14} /> Download full backup
                  </button>
                  <Link className="btn ghost" to="/admin/backup">Choose what to back up or restore</Link>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="stack">
          {canEdit && (
            <div className="panel">
              <div className="section">
                <h3>
                  Record from a real service
                  {recording && <span className="tag scenario"><Record size={11} /> recording</span>}
                </h3>
                <p className="muted small" style={{ marginBottom: 10 }}>
                  MockHub forwards requests sent to {info?.baseUrl}/… to the target and saves each answer as a stub in the chosen project.
                </p>
                <div className="stack" style={{ gap: 10 }}>
                  <label className="field">
                    <span>Target URL</span>
                    <input type="url" className="mono" placeholder="https://api.example.com" value={recordTarget} disabled={recording} onChange={(e) => setRecordTarget(e.target.value)} />
                  </label>
                  <label className="field">
                    <span>Save into</span>
                    <select value={recordProject} onChange={(e) => setRecordProject(e.target.value)}>
                      {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                    </select>
                  </label>
                  <div>
                    {recording ? (
                      <button className="btn primary" onClick={stopRecording}><Stop size={14} /> Stop and save stubs</button>
                    ) : (
                      <button className="btn" onClick={startRecording} disabled={!/^https?:\/\//.test(recordTarget) || !recordProject}>
                        <Record size={14} /> Start recording
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className="panel">
            <div className="section">
              <h3>Stubs outside any project <span className="muted small">{unassigned.length}</span></h3>
              <p className="muted small" style={{ marginBottom: 10 }}>
                Created through the native WireMock API, mapping files or recordings. They work, but don't show up in a project or its exports.
              </p>
              {unassigned.length > 0 && canEdit && projects.length > 0 && (
                <label className="field" style={{ marginBottom: 10 }}>
                  <span>Add to project</span>
                  <select value={target} onChange={(e) => setTarget(e.target.value)}>
                    {projects.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.basePath})</option>)}
                  </select>
                </label>
              )}
              <div className="rows">
                {unassigned.map((s) => (
                  <div key={String(s.id)} className="stack" style={{ gap: 6, paddingBottom: 8, borderBottom: '1px solid var(--line)' }}>
                    <StubRoute stub={s} />
                    {canEdit && projects.length > 0 && (
                      <div className="btn-row">
                        <button className="btn small" onClick={() => adopt(s, true)} title="Serve it under the project's base path">Move under base path</button>
                        <button className="btn small ghost" onClick={() => adopt(s, false)} title="Keep the URL exactly as it is">Keep URL</button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
