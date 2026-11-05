import { ArrowsLeftRight, DownloadSimple } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { CopyButton } from '../components/CopyButton';
import { useToast } from '../components/Toasts';
import { CA_PATH, proxyAddress, setupCommands, type ProxyView } from '../model/proxy';
import { useApp } from '../state/AppContext';

export function ProxyPanel() {
  const toast = useToast();
  const { projects } = useApp();
  const [view, setView] = useState<ProxyView | null>(null);
  const [projectId, setProjectId] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.proxy().then((v) => {
      setView(v);
      setProjectId(v.project?.id ?? '');
    }).catch(toast.fail);
  }, []);

  const save = async () => {
    setBusy(true);
    try {
      const next = await api.setProxyProject(projectId || null);
      setView(next);
      toast.notify(next.project ? `Proxied traffic goes to ${next.project.name}` : 'Proxied traffic is refused until you choose a project');
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  if (!view) return null;
  const address = proxyAddress(window.location);

  return (
    <div className="panel">
      <div className="section">
        <h3><ArrowsLeftRight size={16} /> Forward proxy</h3>
        {!view.enabled ? (
          <p className="muted small">
            The proxy is off. Start MockHub with <span className="mono">MOCKHUB_PROXY=true</span> to let browsers, apps and phones use it as an
            HTTP and HTTPS proxy. Calls that match a stub in the chosen project get the mock; the rest go to the real service and are
            recorded in that project.
          </p>
        ) : (
          <div className="rows">
            <p className="muted small">
              Point clients at <span className="mono">{address}</span>. Calls that match a stub in the project below get the mock answer; the
              rest go on to the real service. Every call lands in the project's request log, where <strong>Create stub from this request</strong> turns it into
              a mock. Accepted from <span className="mono">{view.allowedNetworks.join(', ')}</span>.
            </p>
            <div className="btn-row">
              <label className="field" style={{ minWidth: 260 }}>
                <span>Project for proxied traffic</span>
                <select value={projectId} aria-label="Project for proxied traffic" onChange={(e) => setProjectId(e.target.value)}>
                  <option value="">None: refuse proxied calls</option>
                  {projects.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.basePath || '/'})</option>)}
                </select>
              </label>
              <button className="btn primary" style={{ alignSelf: 'flex-end' }} disabled={busy || projectId === (view.project?.id ?? '')} onClick={save}>
                {busy ? 'Saving…' : 'Save'}
              </button>
            </div>
            <div>
              <a className={`btn small${view.caReady ? '' : ' disabled'}`} href={CA_PATH} download="mockhub-proxy-ca.pem" aria-disabled={!view.caReady}>
                <DownloadSimple size={14} /> Download CA certificate
              </a>
              <span className="muted small" style={{ marginLeft: 8 }}>
                Trust it on the client so MockHub can read HTTPS calls. Only on test devices.
              </span>
            </div>
            {setupCommands(address).map((c) => (
              <div className="field" key={c.label}>
                <span>{c.label} <CopyButton text={c.command} label={`Copy ${c.label}`} /></span>
                <pre className="block" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{c.command}</pre>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
