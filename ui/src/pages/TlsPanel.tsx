import { Certificate } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { FilePicker } from '../components/FilePicker';
import { Confirm } from '../components/Modal';
import { useToast } from '../components/Toasts';
import { commonName, expiry, looksLikePem, type CertificateInfo, type TlsView } from '../model/tls';

type Removing = 'server' | 'clients' | null;

export function TlsPanel() {
  const toast = useToast();
  const [view, setView] = useState<TlsView | null>(null);
  const [serverPem, setServerPem] = useState('');
  const [clientPem, setClientPem] = useState('');
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<Removing>(null);

  useEffect(() => {
    api.tls().then(setView).catch(toast.fail);
  }, []);

  const run = async (change: () => Promise<TlsView>, done: string, clear?: () => void) => {
    setBusy(true);
    try {
      setView(await change());
      clear?.();
      toast.notify(done);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const remove = () => {
    const what = removing;
    setRemoving(null);
    if (what === 'server') run(api.clearServerCertificate, 'Back to the built-in certificate');
    if (what === 'clients') run(api.clearClientAuthorities, 'Client certificates are no longer requested');
  };

  if (!view) return null;

  return (
    <div className="panel">
      <div className="section">
        <h3><span className="title-with-icon"><Certificate size={16} /> HTTPS and client certificates</span></h3>
        {!view.httpsEnabled ? (
          <p className="muted small">
            The HTTPS port is off. Start MockHub with <span className="mono">MOCKHUB_HTTPS_PORT=8443</span> to serve mocks over HTTPS,
            use your own certificate and accept client certificates (mutual TLS).
          </p>
        ) : (
          <p className="muted small">
            HTTPS is on port <span className="mono">{view.httpsPort}</span>. Changes apply to new connections right away.
          </p>
        )}
      </div>

      {view.httpsEnabled && (
        <>
          <div className="section">
            <h4>Server certificate</h4>
            {view.server ? <CertificateLine cert={view.server} /> : <p className="muted small">WireMock's built-in self-signed certificate. Clients must skip verification.</p>}
            <PemInput label="Certificate and private key" placeholder={'-----BEGIN CERTIFICATE-----\n…\n-----END CERTIFICATE-----\n-----BEGIN PRIVATE KEY-----\n…\n-----END PRIVATE KEY-----'}
              hint="The server certificate first, then any intermediates, then its unencrypted private key (PKCS#8, or RSA in PKCS#1). The key is stored encrypted."
              value={serverPem} onChange={setServerPem} />
            <div className="btn-row">
              <button className="btn" disabled={busy || !looksLikePem(serverPem)}
                onClick={() => run(() => api.setServerCertificate(serverPem), 'Server certificate in use', () => setServerPem(''))}>
                Use this certificate
              </button>
              {view.server && <button className="btn ghost" disabled={busy} onClick={() => setRemoving('server')}>Go back to the built-in certificate</button>}
            </div>
          </div>

          <div className="section">
            <h4>Trusted client CAs</h4>
            {view.clientAuthorities.length ? (
              <>
                <p className="muted small">MockHub asks callers for a certificate signed by one of these. Projects that require one refuse everyone else.</p>
                {view.clientAuthorities.map((c) => <CertificateLine key={c.subject + c.notAfter} cert={c} />)}
              </>
            ) : (
              <p className="muted small">None. Add a CA to accept client certificates, then turn on <strong>Client certificate</strong> in a project's runtime settings.</p>
            )}
            <PemInput label="CA certificates" placeholder={'-----BEGIN CERTIFICATE-----\n…\n-----END CERTIFICATE-----'}
              hint="One or more CA certificates. This replaces the current list." value={clientPem} onChange={setClientPem} />
            <div className="btn-row">
              <button className="btn" disabled={busy || !looksLikePem(clientPem)}
                onClick={() => run(() => api.setClientAuthorities(clientPem), 'Client CAs trusted', () => setClientPem(''))}>
                Trust these CAs
              </button>
              {view.clientAuthorities.length > 0 && <button className="btn ghost" disabled={busy} onClick={() => setRemoving('clients')}>Stop accepting client certificates</button>}
            </div>
          </div>
        </>
      )}

      {removing && (
        <Confirm
          title={removing === 'server' ? 'Use the built-in certificate' : 'Stop accepting client certificates'}
          message={removing === 'server'
            ? 'HTTPS goes back to the self-signed certificate. Clients that verify the server will fail.'
            : 'Projects that require a client certificate will refuse every call until you trust a CA again.'}
          confirmLabel={removing === 'server' ? 'Use built-in' : 'Stop accepting'}
          onConfirm={remove}
          onCancel={() => setRemoving(null)}
        />
      )}
    </div>
  );
}

function CertificateLine({ cert }: { cert: CertificateInfo }) {
  const ends = expiry(cert.notAfter);
  return (
    <div className="cert-line">
      <strong>{commonName(cert.subject)}</strong>
      <span className={`tag ${ends.tone === 'ok' ? '' : ends.tone}`}>{ends.text}</span>
      <span className="muted small">issued by {commonName(cert.issuer)}{cert.names.length ? ` · ${cert.names.join(', ')}` : ''}</span>
    </div>
  );
}

function PemInput({ label, placeholder, hint, value, onChange }: { label: string; placeholder: string; hint: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="field">
      <span>{label} <span className="hint">{hint}</span></span>
      <textarea className="mono" rows={4} aria-label={label} placeholder={placeholder} value={value} onChange={(e) => onChange(e.target.value)} />
      <FilePicker accept=".pem,.crt,.cer,.key,.txt" label="Or drop .pem files here" onText={(text) => onChange(value.trim() ? `${value.trim()}\n${text}` : text)} />
    </div>
  );
}
