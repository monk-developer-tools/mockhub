import { useState, type FormEvent } from 'react';
import { api, credentials } from '../api/client';
import { Modal } from '../components/Modal';
import { RoleBadge } from '../components/RoleBadge';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';

const SOURCE_LABEL = { env: 'Server configuration (MOCKHUB_USERS)', local: 'MockHub password', azure: 'Microsoft Entra ID' };

export function AccountDialog({ onClose }: { onClose: () => void }) {
  const { session, orgs, roleIn, signOut } = useApp();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [busy, setBusy] = useState(false);
  if (!session) return null;

  const change = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const result = await api.changePassword(current, next);
      if (result.token) credentials.set(result.token);
      setCurrent('');
      setNext('');
      toast.notify('Password changed. Other sessions are signed out.');
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title={session.displayName || session.username} description={session.email || session.username} onClose={onClose}>
      <div className="stack">
        <table className="kv-table">
          <tbody>
            <tr><td>Username</td><td className="mono">{session.username}</td></tr>
            <tr><td>Signs in with</td><td>{SOURCE_LABEL[session.source]}</td></tr>
            <tr><td>Server role</td><td><RoleBadge role={session.effectiveServerRole} /></td></tr>
            <tr>
              <td>Organisations</td>
              <td>
                {orgs.length === 0 ? <span className="muted">none</span> : (
                  <span className="badge-list">
                    {orgs.map((o) => <span key={o.id}>{o.name} <RoleBadge role={roleIn(o.id)} /></span>)}
                  </span>
                )}
              </td>
            </tr>
          </tbody>
        </table>
        {session.source === 'local' && (
          <form className="stack" onSubmit={change}>
            <h3>Change password</h3>
            <label className="field">
              <span>Current password</span>
              <input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
            </label>
            <label className="field">
              <span>New password <span className="hint">at least 8 characters</span></span>
              <input type="password" autoComplete="new-password" minLength={8} value={next} onChange={(e) => setNext(e.target.value)} required />
            </label>
            <div><button className="btn" type="submit" disabled={busy}>Change password</button></div>
          </form>
        )}
        {session.source === 'azure' && <p className="muted small">Your password and profile are managed in Microsoft Entra ID.</p>}
        <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
          <button className="btn" onClick={signOut}>Sign out</button>
        </div>
      </div>
    </Modal>
  );
}
