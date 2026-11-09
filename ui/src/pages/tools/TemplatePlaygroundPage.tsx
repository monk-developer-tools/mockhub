import { ArrowRight } from '@phosphor-icons/react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { CopyButton } from '../../components/CopyButton';
import { DynamicValueMenu } from '../../components/DynamicValueMenu';
import { PageHeader } from '../../components/ui/PageHeader';
import { TEMPLATE_EXAMPLES, headerLines, sampleOf, urlPathOf } from '../../model/templatePlayground';
import { useApp } from '../../state/AppContext';

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

export function TemplatePlaygroundPage() {
  const { canEdit } = useApp();
  const navigate = useNavigate();
  const first = TEMPLATE_EXAMPLES[0];
  const [template, setTemplate] = useState(first.template);
  const [method, setMethod] = useState(first.method);
  const [url, setUrl] = useState(first.url);
  const [headers, setHeaders] = useState(first.headers);
  const [body, setBody] = useState(first.body);
  const [result, setResult] = useState<{ output?: string; error?: string; ms?: number } | null>(null);
  const area = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    let live = true;
    const timer = setTimeout(async () => {
      const started = performance.now();
      try {
        const r = await api.renderTemplate(template, sampleOf(method, url, headers, body));
        if (live) setResult({ output: r.output, error: r.error ?? r.validationError, ms: Math.round(performance.now() - started) });
      } catch (e) {
        if (live) setResult({ error: e instanceof Error ? e.message : String(e) });
      }
    }, 350);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [template, method, url, headers, body]);

  const load = (title: string) => {
    const ex = TEMPLATE_EXAMPLES.find((e) => e.title === title);
    if (!ex) return;
    setTemplate(ex.template);
    setMethod(ex.method);
    setUrl(ex.url);
    setHeaders(ex.headers);
    setBody(ex.body);
  };
  const insert = (expression: string) => {
    const el = area.current;
    const at = el?.selectionStart ?? template.length;
    const end = el?.selectionEnd ?? at;
    setTemplate(template.slice(0, at) + expression + template.slice(end));
  };
  const useAsStub = () => navigate('/new/json', { state: { text: template, method, path: urlPathOf(url), templated: true } });

  return (
    <div className="page">
      <PageHeader title="Template playground" description="Write a response template, change the sample request and see the answer as you type. It uses the same template engine as your stubs." />
      <div className="tool-grid">
        <section className="panel" aria-label="Template">
          <div className="panel-head">
            <h2>Template</h2>
            <select className="table-select" aria-label="Load an example" value="" onChange={(e) => load(e.target.value)}>
              <option value="">Load an example…</option>
              {TEMPLATE_EXAMPLES.map((e) => <option key={e.title}>{e.title}</option>)}
            </select>
          </div>
          <div className="panel-body stack">
            <textarea ref={area} className="mono" rows={12} spellCheck={false} aria-label="Template text" value={template} onChange={(e) => setTemplate(e.target.value)} />
            <DynamicValueMenu scope="response" onInsert={insert} label="Insert a dynamic value" />
          </div>
        </section>
        <section className="panel" aria-label="Sample request">
          <div className="panel-head"><h2>Sample request</h2></div>
          <div className="panel-body stack">
            <div className="field-row">
              <label className="field"><span>Method</span>
                <select value={method} onChange={(e) => setMethod(e.target.value)}>{METHODS.map((m) => <option key={m}>{m}</option>)}</select>
              </label>
              <label className="field grow"><span>URL</span><input type="text" className="mono" value={url} onChange={(e) => setUrl(e.target.value)} /></label>
            </div>
            <label className="field"><span>Headers, one per line</span>
              <textarea className="mono" rows={3} spellCheck={false} value={headers} onChange={(e) => setHeaders(e.target.value)} />
            </label>
            <label className="field"><span>Body</span>
              <textarea className="mono" rows={4} spellCheck={false} value={body} onChange={(e) => setBody(e.target.value)} />
            </label>
          </div>
        </section>
      </div>
      <section className="panel" aria-label="Rendered response">
        <div className="panel-head">
          <h2>Rendered response</h2>
          <span className="btn-row">
            {result?.ms !== undefined && !result.error && <span className="muted small live-dot">{result.ms} ms</span>}
            {result?.output !== undefined && <CopyButton text={result.output} showLabel />}
            {canEdit && <button className="btn small primary" onClick={useAsStub} disabled={Boolean(result?.error)}>Use as a stub <ArrowRight size={14} /></button>}
          </span>
        </div>
        <div className="panel-body">
          {result?.error ? (
            <div className="callout danger" role="alert">{result.error}</div>
          ) : (
            <pre className="block" aria-label="Output">{result?.output ?? 'Rendering…'}</pre>
          )}
          <p className="muted small">Headers are read as {headerLines.example}. Use as a stub opens a new JSON answer with templating on, in a new project or one you already edit.</p>
        </div>
      </section>
    </div>
  );
}
