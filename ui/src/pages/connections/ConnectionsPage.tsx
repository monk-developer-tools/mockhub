import { CheckCircle, GearSix, Plug, Plus, Trash, WarningCircle } from '@phosphor-icons/react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Connection } from '../../api/types';
import { EmptyArt } from '../../components/Illustrations';
import { Confirm } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { PageHeader } from '../../components/ui/PageHeader';
import { calloutExample, describeTest, proxyExample } from '../../model/connections';
import { timeAgo } from '../../model/time';
import { atLeast, useApp } from '../../state/AppContext';
import { ConnectionDialog } from './ConnectionDialog';

type TestState = { busy: true } | { ok: true; text: string } | { ok: false; text: string };

export function ConnectionsPage() {
  const { currentOrg, roleIn } = useApp();
  const toast = useToast();
  const [connections, setConnections] = useState<Connection[] | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Connection | null>(null);
  const [deleting, setDeleting] = useState<Connection | null>(null);
  const [tests, setTests] = useState<Record<string, TestState>>({});

  const orgId = currentOrg?.id;
  const canManage = atLeast(roleIn(orgId), 'manager');

  const load = useCallback(async () => {
    if (!orgId) return;
    try {
      setConnections(await api.connections(orgId));
    } catch (e) {
      toast.fail(e);
    }
  }, [orgId, toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  const test = async (c: Connection) => {
    if (!orgId) return;
    setTests((t) => ({ ...t, [c.key]: { busy: true } }));
    try {
      const result = await api.testConnection(orgId, c.key);
      setTests((t) => ({ ...t, [c.key]: { ok: true, text: describeTest(result) } }));
    } catch (e) {
      setTests((t) => ({ ...t, [c.key]: { ok: false, text: e instanceof Error ? e.message : String(e) } }));
    }
  };

  const remove = async () => {
    if (!orgId || !deleting) return;
    try {
      await api.deleteConnection(orgId, deleting.key);
      toast.notify(`Connection ${deleting.key} deleted`);
      setDeleting(null);
      load();
    } catch (e) {
      toast.fail(e);
    }
  };

  if (!orgId) return null;

  return (
    <div className="page">
      <PageHeader
        title="Connections"
        icon={<Plug size={20} weight="duotone" />}
        description={`Credentials for the real services that ${currentOrg?.name ?? 'this organisation'}'s callouts and proxies call. MockHub fetches and refreshes OAuth tokens and adds the header for you.`}
        actions={canManage && connections?.length ? <button className="btn primary" onClick={() => setCreating(true)}><Plus size={14} /> New connection</button> : undefined}
      />

      {connections !== null && connections.length === 0 ? (
        <div className="panel empty">
          <EmptyArt kind="projects" />
          <h2>No connections yet</h2>
          <p>
            A connection holds a client secret or API key once, encrypted. A webhook, a wait-for-callout or a proxy stub names it
            with <code>"connection": "bank"</code> and MockHub sends the right <span className="mono">Authorization</span> header.
          </p>
          {canManage ? (
            <button className="btn primary" onClick={() => setCreating(true)}><Plus size={14} /> New connection</button>
          ) : (
            <p className="muted small">Managers of the organisation can add connections.</p>
          )}
        </div>
      ) : (
        <div className="panel">
          <table className="data-table">
            <thead>
              <tr>
                <th>Connection</th>
                <th>Type</th>
                <th>Sends</th>
                <th>Changed</th>
                <th><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {(connections ?? []).map((c) => {
                const t = tests[c.key];
                return (
                  <tr key={c.key}>
                    <td>
                      <strong>{c.name}</strong>
                      <div className="mono muted small">{c.key}</div>
                    </td>
                    <td>
                      {c.type === 'oauth2' ? 'OAuth2' : 'API key'}
                      {c.type === 'oauth2' && <div className="mono muted small">{c.tokenUrl}</div>}
                    </td>
                    <td>
                      <span className="mono small">{c.headerName}: {c.headerPrefix}{c.type === 'oauth2' ? '<token>' : '••••'}</span>
                      {!c.hasSecret && <div className="small"><WarningCircle size={13} /> No secret yet</div>}
                      {t && 'busy' in t && <div className="muted small">Testing…</div>}
                      {t && !('busy' in t) && (
                        <div className={`small connection-test ${t.ok ? 'ok' : 'failed'}`} role="status">
                          {t.ok ? <CheckCircle size={13} /> : <WarningCircle size={13} />} {t.text}
                        </div>
                      )}
                    </td>
                    <td className="muted small">{timeAgo(c.updatedAt)}</td>
                    <td className="actions">
                      {canManage && (
                        <span className="btn-row">
                          <button className="btn small" onClick={() => test(c)}>Test</button>
                          <button className="btn ghost icon small" aria-label={`Settings for ${c.key}`} title="Settings" onClick={() => setEditing(c)}><GearSix size={14} /></button>
                          <button className="btn ghost icon small" aria-label={`Delete ${c.key}`} title="Delete" onClick={() => setDeleting(c)}><Trash size={14} /></button>
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {connections && connections.length > 0 && (
            <div className="panel-body stack small connection-examples">
              <p className="muted">Use a connection in a stub's webhook or wait-for-callout (with up to 5 retries), or on a proxy:</p>
              <code>{calloutExample(connections[0].key)}</code>
              <code>{proxyExample(connections[0].key)}</code>
            </div>
          )}
        </div>
      )}

      {creating && <ConnectionDialog orgId={orgId} onClose={() => setCreating(false)} onSaved={() => { setCreating(false); load(); }} />}
      {editing && <ConnectionDialog orgId={orgId} connection={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />}
      {deleting && (
        <Confirm title={`Delete ${deleting.key}?`} confirmLabel="Delete connection"
          message={`Callouts and proxies that use ${deleting.key} fail with an error until you add it again.`}
          onConfirm={remove} onCancel={() => setDeleting(null)} />
      )}
    </div>
  );
}
