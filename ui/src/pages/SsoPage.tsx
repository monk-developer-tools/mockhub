import { ArrowSquareOut, CheckCircle, Plus, Trash, XCircle } from '@phosphor-icons/react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Organization, Role, RoleMapping, SsoSettings } from '../api/types';
import { CopyButton } from '../components/CopyButton';
import { Skeleton } from '../components/Skeleton';
import { useToast } from '../components/Toasts';
import { MicrosoftMark } from './LoginPage';

type TestResult = { ok: boolean; error?: string; issuer?: string } | null;

export function ssoStatus(s: Pick<SsoSettings, 'enabled' | 'active' | 'tenantId' | 'clientId' | 'clientSecretSet'>) {
  if (s.active) return { tone: 'ok', label: 'On' } as const;
  if (s.enabled) return { tone: 'warn', label: 'Incomplete' } as const;
  return { tone: 'off', label: 'Off' } as const;
}

export function SsoPage() {
  const toast = useToast();
  const [settings, setSettings] = useState<SsoSettings | null>(null);
  const [orgs, setOrgs] = useState<Organization[]>([]);
  const [secret, setSecret] = useState('');
  const [domains, setDomains] = useState('');
  const [test, setTest] = useState<TestResult>(null);
  const [testing, setTesting] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [s, o] = await Promise.all([api.sso(), api.orgs()]);
      setSettings(s);
      setDomains(s.allowedDomains.join(', '));
      setOrgs(o);
    } catch (e) {
      toast.fail(e);
    }
  }, [toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  if (!settings) return <div className="page"><Skeleton rows={6} label="Loading single sign-on settings" /></div>;

  const set = <K extends keyof SsoSettings>(key: K, value: SsoSettings[K]) => setSettings({ ...settings, [key]: value });
  const setMapping = (i: number, patch: Partial<RoleMapping>) =>
    set('roleMappings', settings.roleMappings.map((m, j) => (j === i ? { ...m, ...patch } : m)));

  const body = (): Partial<SsoSettings> => ({
    ...settings,
    clientSecret: secret || undefined,
    allowedDomains: domains.split(/[,\s]+/).map((d) => d.trim()).filter(Boolean),
  });

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const saved = await api.saveSso(body());
      setSettings(saved);
      setSecret('');
      toast.notify(saved.active ? 'Single sign-on is on. The login page now shows the Microsoft button.' : 'Settings saved');
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  const runTest = async () => {
    setTesting(true);
    try {
      setTest(await api.testSso(body()));
    } catch (err) {
      setTest({ ok: false, error: err instanceof Error ? err.message : String(err) });
    } finally {
      setTesting(false);
    }
  };

  const status = ssoStatus(settings);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Single sign-on</h1>
          <p className="muted">Let people sign in with their work account from Microsoft Entra ID (formerly Azure AD).</p>
        </div>
        <span className={`status-pill ${status.tone}`}>{status.label}</span>
      </div>

      <form className="split sso" onSubmit={save}>
        <div className="stack">
          <div className="panel">
            <div className="panel-head">
              <h2><MicrosoftMark /> Microsoft Entra ID</h2>
              <label className="switch">
                <input type="checkbox" checked={settings.enabled} onChange={(e) => set('enabled', e.target.checked)} />
                <span>Enabled</span>
              </label>
            </div>
            <div className="panel-body stack">
              <label className="field">
                <span>Redirect URI <span className="hint">paste this into the app registration</span></span>
                <div className="copy-field">
                  <code>{settings.redirectUri}</code>
                  <CopyButton text={settings.redirectUri} />
                </div>
              </label>
              <div className="grid two">
                <label className="field">
                  <span>Directory (tenant) ID</span>
                  <input type="text" className="mono" placeholder="00000000-0000-0000-0000-000000000000" value={settings.tenantId} onChange={(e) => set('tenantId', e.target.value)} />
                  <span className="hint">or contoso.onmicrosoft.com, or <code>organizations</code> for any work account</span>
                </label>
                <label className="field">
                  <span>Application (client) ID</span>
                  <input type="text" className="mono" placeholder="00000000-0000-0000-0000-000000000000" value={settings.clientId} onChange={(e) => set('clientId', e.target.value)} />
                </label>
                <label className="field">
                  <span>Client secret value</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    placeholder={settings.clientSecretFromEnvironment ? 'Set by MOCKHUB_SSO_CLIENT_SECRET' : settings.clientSecretSet ? 'Saved. Type to replace it.' : 'Paste the secret value'}
                    disabled={settings.clientSecretFromEnvironment}
                    value={secret}
                    onChange={(e) => setSecret(e.target.value)}
                  />
                  <span className="hint">Stored on the server and never shown again.</span>
                </label>
                <label className="field">
                  <span>Cloud</span>
                  <select value={settings.cloud} onChange={(e) => set('cloud', e.target.value as SsoSettings['cloud'])}>
                    <option value="global">Microsoft global (login.microsoftonline.com)</option>
                    <option value="usgov">US Government (login.microsoftonline.us)</option>
                    <option value="china">China, 21Vianet (login.chinacloudapi.cn)</option>
                  </select>
                </label>
              </div>
              <div className="btn-row">
                <button type="button" className="btn" onClick={runTest} disabled={testing || !settings.tenantId}>
                  {testing ? 'Checking…' : 'Test connection'}
                </button>
                {test && (
                  <span className={`test-result ${test.ok ? 'ok' : 'fail'}`} role="status">
                    {test.ok ? <CheckCircle size={15} /> : <XCircle size={15} />}
                    {test.ok ? `Tenant found: ${test.issuer}` : test.error}
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="panel">
            <div className="panel-head"><h2>Who can sign in</h2></div>
            <div className="panel-body stack">
              <label className="field">
                <span>Allowed e-mail domains <span className="hint">optional, comma-separated</span></span>
                <input type="text" placeholder="contoso.com, contoso.co.uk" value={domains} onChange={(e) => setDomains(e.target.value)} />
                <span className="hint">Leave empty to accept anyone your tenant lets in.</span>
              </label>
              <label className="check-row">
                <input type="checkbox" checked={settings.allowPasswordLogin} onChange={(e) => set('allowPasswordLogin', e.target.checked)} />
                <span>Also allow MockHub passwords <span className="muted">(admins can always use one, so you can't lock yourself out)</span></span>
              </label>
              <label className="field">
                <span>Button text</span>
                <input type="text" value={settings.buttonLabel} onChange={(e) => set('buttonLabel', e.target.value)} />
              </label>
            </div>
          </div>

          <div className="panel">
            <div className="panel-head"><h2>What people can do after signing in</h2></div>
            <div className="panel-body stack">
              <div className="grid three">
                <label className="field">
                  <span>Everyone joins</span>
                  <select value={settings.defaultOrgId} onChange={(e) => set('defaultOrgId', e.target.value)}>
                    <option value="">No organisation</option>
                    {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
                  </select>
                </label>
                <label className="field">
                  <span>As</span>
                  <select value={settings.defaultOrgRole} disabled={!settings.defaultOrgId} onChange={(e) => set('defaultOrgRole', e.target.value as SsoSettings['defaultOrgRole'])}>
                    <option value="none">No role</option>
                    <option value="viewer">Viewer</option>
                    <option value="editor">Editor</option>
                    <option value="owner">Owner</option>
                  </select>
                </label>
                <label className="field">
                  <span>Server role</span>
                  <select value={settings.defaultServerRole} onChange={(e) => set('defaultServerRole', e.target.value as SsoSettings['defaultServerRole'])}>
                    <option value="none">None</option>
                    <option value="viewer">Viewer everywhere</option>
                    <option value="editor">Editor everywhere</option>
                  </select>
                </label>
              </div>

              <div>
                <h3>Map Entra groups and app roles</h3>
                <p className="muted small">
                  Use a group's object ID or an app role value. Roles from groups are refreshed at every sign-in. Roles you set by hand stay as they are.
                </p>
              </div>
              <table className="data-table compact mappings">
                <thead>
                  <tr><th>Group object ID or app role</th><th>Gives access to</th><th>Role</th><th><span className="sr-only">Remove</span></th></tr>
                </thead>
                <tbody>
                  {settings.roleMappings.length === 0 && (
                    <tr><td colSpan={4} className="muted small">No mappings yet. Everyone gets the defaults above.</td></tr>
                  )}
                  {settings.roleMappings.map((m, i) => (
                    <tr key={i}>
                      <td>
                        <input type="text" className="mono" aria-label="Group object ID or app role" placeholder="e.g. 5f2c… or MockHub.Editor" value={m.claim} onChange={(e) => setMapping(i, { claim: e.target.value })} />
                      </td>
                      <td>
                        <select aria-label="Gives access to" value={m.orgId} onChange={(e) => setMapping(i, { orgId: e.target.value, role: e.target.value ? 'editor' : 'admin' })}>
                          <option value="">The whole server</option>
                          {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
                        </select>
                      </td>
                      <td>
                        <select aria-label="Role" value={m.role} onChange={(e) => setMapping(i, { role: e.target.value as Role })}>
                          {m.orgId ? (
                            <>
                              <option value="viewer">Viewer</option>
                              <option value="editor">Editor</option>
                              <option value="owner">Owner</option>
                            </>
                          ) : (
                            <>
                              <option value="viewer">Viewer everywhere</option>
                              <option value="editor">Editor everywhere</option>
                              <option value="admin">Admin</option>
                            </>
                          )}
                        </select>
                      </td>
                      <td className="actions">
                        <button type="button" className="btn ghost icon small" aria-label="Remove mapping" onClick={() => set('roleMappings', settings.roleMappings.filter((_, j) => j !== i))}>
                          <Trash size={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div>
                <button type="button" className="btn small" onClick={() => set('roleMappings', [...settings.roleMappings, { claim: '', orgId: orgs[0]?.id ?? '', role: 'editor' }])}>
                  <Plus size={14} /> Add mapping
                </button>
              </div>
            </div>
          </div>

          <div className="save-bar">
            <button className="btn primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save settings'}</button>
          </div>
        </div>

        <aside className="panel guide">
          <div className="panel-head"><h2>Set it up in Entra ID</h2></div>
          <ol className="guide-steps">
            <li>
              In the <a href="https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade" target="_blank" rel="noreferrer">Microsoft Entra admin centre <ArrowSquareOut size={12} /></a>,
              open <strong>App registrations</strong> and choose <strong>New registration</strong>.
            </li>
            <li>Name it <em>MockHub</em>. Under <strong>Redirect URI</strong> pick <strong>Web</strong> and paste the redirect URI shown on this page.</li>
            <li>From <strong>Overview</strong>, copy the <strong>Application (client) ID</strong> and the <strong>Directory (tenant) ID</strong> into the form.</li>
            <li>In <strong>Certificates &amp; secrets</strong>, add a client secret and paste its <strong>Value</strong>, not its ID. Note when it expires.</li>
            <li>
              Optional, for roles from groups: in <strong>Token configuration</strong>, add a <strong>groups claim</strong> (security groups, ID token). People in
              more than 200 groups won't get group roles; use <strong>App roles</strong> instead and map their values.
            </li>
            <li>Choose <strong>Test connection</strong>, turn on <strong>Enabled</strong> and save. Then sign in from a private window to try it.</li>
          </ol>
        </aside>
      </form>
    </div>
  );
}
