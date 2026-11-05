import { Plugs, X } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { CopyButton } from '../../components/CopyButton';
import { useToast } from '../../components/Toasts';
import { formatBytes, tunnelCommand, type TunnelView } from '../../model/tunnels';
import { useApp } from '../../state/AppContext';

export function Tunnels() {
  const toast = useToast();
  const { session } = useApp();
  const [tunnels, setTunnels] = useState<TunnelView[] | null>(null);

  const load = () => api.tunnels().then(setTunnels).catch(toast.fail);
  useEffect(() => {
    load();
    const timer = window.setInterval(load, 5000);
    return () => window.clearInterval(timer);
  }, []);

  const close = async (tunnel: TunnelView) => {
    try {
      await api.closeTunnel(tunnel.name);
      toast.notify(`Tunnel ${tunnel.name} closed`);
      load();
    } catch (e) {
      toast.fail(e);
    }
  };

  const command = tunnelCommand(window.location.origin, 'laptop', 'http://localhost:3000');
  return (
    <div className="stack">
      <p className="muted small">
        A tunnel lets proxy stubs and callouts reach a service on your own machine. Start it with an access token, then use
        {' '}<span className="mono">tunnel://laptop/path</span> as the proxy target or callout URL.
      </p>
      <div className="snippet">
        <pre className="block">{command}</pre>
        <CopyButton text={command} label="Copy command" showLabel />
      </div>
      {tunnels && tunnels.length === 0 && (
        <div className="token-empty">
          <Plugs size={22} aria-hidden="true" />
          <span>No tunnels connected.</span>
        </div>
      )}
      {tunnels && tunnels.length > 0 && (
        <ul className="token-list" aria-label="Tunnels">
          {tunnels.map((t) => (
            <li key={t.name}>
              <div className="token-main">
                <strong className="mono">{t.url}</strong>
                {session?.isAdmin && <span className="muted small">{t.owner}</span>}
              </div>
              <div className="token-meta small">
                <span className={t.online ? 'tag ok' : 'tag'}>{t.online ? 'online' : 'offline'}</span>
                <span className="muted">{t.requests} requests{t.failed > 0 ? `, ${t.failed} failed` : ''}</span>
              </div>
              <div className="token-times small muted">
                <span>{formatBytes(t.bytesOut)} sent</span>
                <span>{formatBytes(t.bytesIn)} received</span>
              </div>
              <button type="button" className="btn ghost icon" aria-label={`Close ${t.name}`} title="Close" onClick={() => close(t)}>
                <X size={15} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
