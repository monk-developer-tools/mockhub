import { useState, type FormEvent } from 'react';
import { Logo } from '../components/Logo';
import { useApp } from '../state/AppContext';

export function LoginPage() {
  const { signIn } = useApp();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await signIn(username, password);
    } catch {
      setError('Wrong username or password.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <form className="panel panel-body" onSubmit={submit}>
        <div className="brand">
          <Logo size={28} /> MockHub
        </div>
        <label className="field">
          <span>Username</span>
          <input type="text" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <div className="callout danger" role="alert">{error}</div>}
        <button className="btn primary" type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="muted small">Mock endpoints stay open; signing in is only needed to manage them.</p>
      </form>
    </div>
  );
}
