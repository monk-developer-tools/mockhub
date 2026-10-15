import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { Logo } from '../components/Logo';
import { useApp } from '../state/AppContext';

export function MicrosoftMark() {
  return (
    <svg width="16" height="16" viewBox="0 0 21 21" aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#f25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
      <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
      <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
    </svg>
  );
}

export function LoginPage() {
  const { signIn, authInfo, loginError } = useApp();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const sso = authInfo?.sso;
  const passwordFirst = !sso?.enabled || authInfo?.passwordLogin;
  const passwordVisible = passwordFirst || showPassword;
  const note = authInfo?.loginNote ?? '';
  const shownError = error || loginError;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await signIn(username, password);
    } catch (err) {
      setError(err instanceof ApiError && err.status === 403 ? err.message : 'Wrong username or password.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <div className="panel panel-body login-card">
        <div className="brand">
          <Logo size={28} /> MockHub
        </div>
        {shownError && <div className="callout danger" role="alert">{shownError}</div>}
        {sso?.enabled && sso.startUrl && (
          <a className="btn sso-btn" href={sso.startUrl}>
            <MicrosoftMark />
            {sso.label ?? 'Sign in with Microsoft'}
          </a>
        )}
        {sso?.enabled && passwordVisible && <div className="divider-label"><span>or use a MockHub password</span></div>}
        {passwordVisible ? (
          <form className="stack" onSubmit={submit}>
            <label className="field">
              <span>Username</span>
              <input type="text" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
            </label>
            <label className="field">
              <span>Password</span>
              <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </label>
            {note && <div className="callout">{note}</div>}
            <button className={`btn${sso?.enabled ? '' : ' primary'}`} type="submit" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        ) : (
          <button className="link-button small" onClick={() => setShowPassword(true)}>
            Administrator? Sign in with a password
          </button>
        )}
        <p className="muted small">Mock endpoints stay open; signing in is only needed to manage them.</p>
      </div>
    </div>
  );
}
