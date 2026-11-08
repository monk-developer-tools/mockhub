import { CheckCircle } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { Project, Stub } from '../../api/types';
import { CopyButton } from '../../components/CopyButton';
import { readyCalls } from '../../model/ready';
import { tunnelCommand } from '../../model/starts';
import { useApp } from '../../state/AppContext';

export function ReadyPanel({ project }: { project: Project }) {
  const { search, pathname } = useLocation();
  const navigate = useNavigate();
  const { info } = useApp();
  const params = new URLSearchParams(search);
  const kind = params.get('ready');
  const notes = (params.get('notes') ?? '').split('\n').filter(Boolean);
  const tunnel = params.get('tunnel');
  const [stubs, setStubs] = useState<Stub[] | null>(null);
  const [tried, setTried] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!kind) return;
    let cancelled = false;
    api.stubs(project.id).then((list) => !cancelled && setStubs(list)).catch(() => !cancelled && setStubs([]));
    return () => {
      cancelled = true;
    };
  }, [kind, project.id]);

  if (!kind) return null;
  const tryIt = async (url: string) => {
    setTried((t) => ({ ...t, [url]: 'Sending…' }));
    try {
      const started = performance.now();
      const res = await fetch(url);
      const text = await res.text();
      const ms = Math.round(performance.now() - started);
      setTried((t) => ({ ...t, [url]: `${res.status} in ${ms} ms · ${text.replace(/\s+/g, ' ').slice(0, 160)}${text.length > 160 ? '…' : ''}` }));
    } catch (e) {
      setTried((t) => ({ ...t, [url]: e instanceof Error ? e.message : String(e) }));
    }
  };
  const calls = stubs ? readyCalls(stubs, project.mockUrl) : [];
  const close = () => navigate(pathname, { replace: true });
  const base = kind === 'grpc' ? new URL(project.mockUrl).host : project.mockUrl;

  return (
    <section className="ready" aria-label="Your mock is ready">
      <div className="ready-head">
        <CheckCircle size={22} weight="duotone" color="var(--ok)" />
        <h2>Your mock is ready</h2>
        <button type="button" className="btn small ghost" onClick={close}>Done</button>
      </div>
      <div className="code-line">
        <code className="mono small">{base}</code>
        <CopyButton text={base} />
      </div>
      {tunnel && (
        <>
          <p className="small">Start the tunnel on your machine so calls reach your service:</p>
          <div className="code-line">
            <code className="mono small">{tunnelCommand(info?.baseUrl ?? window.location.origin, tunnel)}</code>
            <CopyButton text={tunnelCommand(info?.baseUrl ?? window.location.origin, tunnel)} />
          </div>
        </>
      )}
      {stubs === null ? (
        <p className="muted small">Loading the endpoints…</p>
      ) : calls.length > 0 ? (
        <ul className="ready-calls" aria-label="Try it">
          {calls.map((call) => (
            <li key={call.label}>
              <span className="small">
                <strong>{call.label}</strong>
                {call.url && (
                  <>
                    {' · '}<button type="button" className="link-button" onClick={() => tryIt(call.url ?? '')}>Try it</button>
                    {' · '}<a href={call.url} target="_blank" rel="noreferrer">Open</a>
                  </>
                )}
              </span>
              <div className="code-line">
                <code className="mono small">{call.command}</code>
                <CopyButton text={call.command} />
              </div>
              {call.url && tried[call.url] && <output className="ready-answer mono small">{tried[call.url]}</output>}
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted small">Add a stub to give it something to answer.</p>
      )}
      {notes.length > 0 && <div className="callout small">{notes.join(' · ')}</div>}
      <div className="ready-next">
        <Link className="btn small" to={`/projects/${project.id}/stubs`}>Change the answers</Link>
        <Link className="btn small" to={`/projects/${project.id}/requests`}>Watch requests</Link>
        <Link className="btn small" to={`/projects/${project.id}/settings`}>Add delays or failures</Link>
        {kind === 'webhook' && <Link className="btn small" to={`/projects/${project.id}/callouts`}>See callbacks</Link>}
        <Link className="btn small" to="/org">Invite your team</Link>
      </div>
    </section>
  );
}
