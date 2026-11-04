import { FloppyDisk, Play } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Project, Stub } from '../../api/types';
import { CodeEditor } from '../../components/CodeEditor';
import { operationNameOf, operationStub, parseVariables, requestBody, type GraphqlOperation } from '../../model/graphql';
import type { StubForm } from '../../model/stubForm';

interface Answer {
  status: number;
  ms: number;
  body: string;
}

export function GraphQlConsole({ project, form, canEdit, onCreate }: {
  project: Project;
  form: StubForm;
  canEdit: boolean;
  onCreate: (stub: Stub) => void;
}) {
  const [operations, setOperations] = useState<GraphqlOperation[]>([]);
  const [schemaError, setSchemaError] = useState('');
  const [query, setQuery] = useState(() => (form.graphqlOperation ? `query ${form.graphqlOperation} {\n  __typename\n}\n` : '{\n  __typename\n}\n'));
  const [variables, setVariables] = useState(form.graphqlVariables || '{}');
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const schema = form.responseMode === 'graphql' ? form.graphqlSchema : '';
  const path = form.urlMatch === 'any' ? '/graphql' : form.url;

  useEffect(() => {
    if (!schema.trim()) return;
    let live = true;
    api.graphqlOperations(project.id, schema)
      .then((r) => {
        if (!live) return;
        setOperations(r.operations);
        setSchemaError('');
      })
      .catch((e) => live && setSchemaError(e instanceof Error ? e.message : String(e)));
    return () => {
      live = false;
    };
  }, [project.id, schema]);

  const pick = (name: string) => {
    const op = operations.find((o) => o.operationName === name);
    if (!op) return;
    setQuery(op.query);
    setVariables(JSON.stringify(op.variables, null, 2));
    setAnswer(null);
  };

  const run = async () => {
    const vars = parseVariables(variables);
    if (!vars.ok) return setError(vars.error);
    setBusy(true);
    setError('');
    const headers = new Headers({ 'Content-Type': 'application/json' });
    if (project.apiKey?.value) headers.set(project.apiKey.header, project.apiKey.value);
    const started = performance.now();
    try {
      const response = await fetch(project.basePath + path, { method: 'POST', headers, body: requestBody(query, vars.value) });
      const text = await response.text();
      let body = text;
      try {
        body = JSON.stringify(JSON.parse(text), null, 2);
      } catch {
        body = text;
      }
      setAnswer({ status: response.status, ms: Math.round(performance.now() - started), body });
    } catch {
      setError('The request failed. Save the stub first, then try again.');
    } finally {
      setBusy(false);
    }
  };

  const name = operationNameOf(query);
  const save = () => {
    const vars = parseVariables(variables);
    if (!answer || !name || !vars.ok) return;
    onCreate(operationStub(path, name, vars.value, answer.body));
  };

  return (
    <div className="section try">
      <h3>
        GraphQL console <span className="muted small">runs against this project's mock URL; save first to test your latest changes</span>
      </h3>
      <div className="rows">
        {operations.length > 0 && (
          <label className="field">
            <span>Example operation <span className="hint">from the schema</span></span>
            <select defaultValue="" onChange={(e) => pick(e.target.value)}>
              <option value="" disabled>Choose a query or mutation…</option>
              {operations.map((o) => <option key={o.operationName} value={o.operationName}>{o.kind} {o.operationName}{o.description ? ` — ${o.description}` : ''}</option>)}
            </select>
          </label>
        )}
        {schemaError && <div className="callout warn">{schemaError}</div>}
        <div className="grid two graphql-console">
          <div className="field">
            <span>Query</span>
            <CodeEditor label="GraphQL query" value={query} onChange={setQuery} language="text" minHeight="160px" maxHeight="360px" />
          </div>
          <div className="field">
            <span>Variables</span>
            <CodeEditor label="GraphQL variables" value={variables} onChange={setVariables} language="json" minHeight="160px" maxHeight="360px" />
          </div>
        </div>
        <div className="btn-row">
          <button className="btn primary" onClick={run} disabled={busy}><Play size={14} /> {busy ? 'Running…' : `POST ${project.basePath}${path}`}</button>
          {canEdit && answer && answer.status === 200 && (
            <button className="btn" onClick={save} disabled={!name} title={name ? undefined : 'Name the operation, e.g. query GetUser { … }'}>
              <FloppyDisk size={14} /> Save answer as a stub for {name ?? 'a named operation'}
            </button>
          )}
        </div>
        {error && <div className="callout warn" role="alert">{error}</div>}
        {answer && (
          <div className="try-result">
            <div className="try-meta">
              <span className={`status-plug s${String(answer.status)[0]}`}>{answer.status}</span>
              <span className="num">{answer.ms} ms</span>
            </div>
            <pre className="block">{answer.body || '(empty body)'}</pre>
          </div>
        )}
      </div>
    </div>
  );
}
