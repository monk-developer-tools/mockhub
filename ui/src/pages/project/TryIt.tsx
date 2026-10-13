import { Play } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { Project } from '../../api/types';
import { CodeEditor } from '../../components/CodeEditor';
import { sampleRequest } from '../../model/sampleRequest';
import { METHODS, type StubForm } from '../../model/stubForm';

interface TryResult {
  status: number;
  statusText: string;
  ms: number;
  headers: [string, string][];
  body: string;
}

export function TryIt({ project, form }: { project: Project; form: StubForm }) {
  const [method, setMethod] = useState('GET');
  const [path, setPath] = useState('/');
  const [headers, setHeaders] = useState('');
  const [body, setBody] = useState('');
  const [result, setResult] = useState<TryResult | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const reset = () => {
    const sample = sampleRequest(form);
    setMethod(sample.method);
    setPath(sample.path);
    setHeaders(sample.headers.map(([k, v]) => `${k}: ${v}`).join('\n'));
    setBody(sample.body);
    setResult(null);
    setError('');
  };

  useEffect(reset, [form.urlMatch, form.url, form.method, form.kind]);

  const send = async () => {
    setBusy(true);
    setError('');
    setResult(null);
    const requestHeaders = new Headers();
    headers
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean)
      .forEach((line) => {
        const i = line.indexOf(':');
        if (i > 0) requestHeaders.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
      });
    const started = performance.now();
    try {
      const response = await fetch(project.basePath + path, {
        method,
        headers: requestHeaders,
        body: method === 'GET' || method === 'HEAD' ? undefined : body,
      });
      const text = await response.text();
      setResult({
        status: response.status,
        statusText: response.statusText,
        ms: Math.round(performance.now() - started),
        headers: Array.from(response.headers.entries()),
        body: prettify(text, response.headers.get('Content-Type') ?? ''),
      });
    } catch {
      setError('The connection failed. That is expected for fault stubs (connection reset, empty or garbage responses).');
    } finally {
      setBusy(false);
    }
  };

  const bodyLanguage = form.kind === 'soap' || body.trimStart().startsWith('<') ? 'xml' : 'json';
  return (
    <div className="section try">
      <h3>
        Try it <span className="muted small">sends a real request to this project's mock URL; save first to test your latest changes</span>
      </h3>
      <div className="rows">
        <div className="row" style={{ gridTemplateColumns: '120px 1fr auto auto' }}>
          <select aria-label="Method" value={method} onChange={(e) => setMethod(e.target.value)}>
            {METHODS.filter((m) => m !== 'ANY').map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
          <input type="text" className="mono" aria-label="Path" value={path} onChange={(e) => setPath(e.target.value)} />
          <button className="btn ghost" onClick={reset}>Reset</button>
          <button className="btn primary" onClick={send} disabled={busy}>
            <Play size={14} /> {busy ? 'Sending…' : 'Send'}
          </button>
        </div>
        <label className="field">
          <span>Headers <span className="hint">one per line, Name: value</span></span>
          <textarea className="mono" rows={2} value={headers} onChange={(e) => setHeaders(e.target.value)} />
        </label>
        {method !== 'GET' && method !== 'HEAD' && (
          <div className="field">
            <span>Body</span>
            <CodeEditor label="Request body" value={body} onChange={setBody} language={bodyLanguage} minHeight="80px" maxHeight="240px" />
          </div>
        )}
        {error && <div className="callout warn">{error}</div>}
        {result && (
          <div className="try-result">
            <div className="try-meta">
              <span className={`status-plug s${String(result.status)[0]}`}>{result.status}</span>
              <span>{result.statusText}</span>
              <span className="num">{result.ms} ms</span>
              {result.status === 404 && <span>No stub matched. Check the Requests tab for the closest match.</span>}
            </div>
            <details>
              <summary className="muted small">Response headers ({result.headers.length})</summary>
              <table className="kv-table">
                <tbody>
                  {result.headers.map(([k, v]) => (
                    <tr key={k}>
                      <td>{k}</td>
                      <td>{v}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
            <pre className="block">{result.body || '(empty body)'}</pre>
          </div>
        )}
      </div>
    </div>
  );
}

function prettify(text: string, contentType: string) {
  if (contentType.includes('json')) {
    try {
      return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      return text;
    }
  }
  return text;
}
