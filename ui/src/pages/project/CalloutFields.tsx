import { Trash } from '@phosphor-icons/react';
import type { EditorView } from '@codemirror/view';
import { useRef } from 'react';
import { CodeEditor, insertAtCursor } from '../../components/CodeEditor';
import { DynamicValueMenu } from '../../components/DynamicValueMenu';
import { MAX_RETRIES, WEBHOOK_METHODS, type Webhook } from '../../model/stubForm';
import { ConnectionSelect } from '../connections/ConnectionSelect';
import { KeyValueRows } from './MatcherRows';

interface Props {
  label: string;
  value: Webhook;
  onChange: (patch: Partial<Webhook>) => void;
  onRemove?: () => void;
  readOnly: boolean;
  withDelay: boolean;
  connections?: string[];
}

export function CalloutFields({ label, value: w, onChange, onRemove, readOnly, withDelay, connections = [] }: Props) {
  const bodyRef = useRef<EditorView | null>(null);
  const urlRef = useRef<HTMLInputElement>(null);

  const insertIntoBody = (expression: string) => {
    if (!insertAtCursor(bodyRef.current, expression)) onChange({ body: w.body + expression });
  };

  const insertIntoUrl = (expression: string) => {
    const input = urlRef.current;
    const start = input?.selectionStart ?? w.url.length;
    const end = input?.selectionEnd ?? w.url.length;
    onChange({ url: w.url.slice(0, start) + expression + w.url.slice(end) });
  };

  return (
    <div className="webhook">
      <div className="webhook-head">
        {w.forward ? (
          <span className="tag mono" title="The incoming request's method is reused">same method</span>
        ) : (
          <select aria-label={`${label} method`} value={w.method} disabled={readOnly} onChange={(e) => onChange({ method: e.target.value })}>
            {WEBHOOK_METHODS.map((m) => <option key={m}>{m}</option>)}
          </select>
        )}
        <input
          ref={urlRef}
          type="text"
          className="mono"
          aria-label={`${label} URL`}
          placeholder="https://partner.example.com/callbacks or {{jsonPath originalRequest.body '$.callbackUrl'}}"
          value={w.url}
          disabled={readOnly}
          onChange={(e) => onChange({ url: e.target.value })}
        />
        {withDelay && (
          <span className="input-suffix">
            <input type="number" min={0} aria-label={`${label} delay`} placeholder="0" value={w.delay} disabled={readOnly} onChange={(e) => onChange({ delay: e.target.value })} />
            <span>ms</span>
          </span>
        )}
        {onRemove && !readOnly && (
          <button type="button" className="btn ghost icon small" aria-label={`Remove ${label.toLowerCase()}`} onClick={onRemove}>
            <Trash size={14} />
          </button>
        )}
      </div>
      <div className="webhook-tools">
        <div className="segmented" role="group" aria-label={`${label} payload`}>
          <button type="button" aria-pressed={!w.forward} disabled={readOnly} onClick={() => onChange({ forward: false })}>Custom request</button>
          <button type="button" aria-pressed={w.forward} disabled={readOnly} onClick={() => onChange({ forward: true })}>Forward incoming request</button>
        </div>
        {!readOnly && <DynamicValueMenu scope="callout" label="Insert in URL" onInsert={insertIntoUrl} />}
      </div>
      <div className="grid three">
        <ConnectionSelect label="Sign with" value={w.connection ?? ''} keys={connections} disabled={readOnly}
          onChange={(connection) => onChange({ connection })} />
        <label className="field">
          <span>Retries <span className="hint">0–{MAX_RETRIES}</span></span>
          <input type="number" min={0} max={MAX_RETRIES} aria-label={`${label} retries`} placeholder="0" value={w.retries ?? ''} disabled={readOnly}
            onChange={(e) => onChange({ retries: e.target.value })} />
        </label>
        <label className="field">
          <span>First wait <span className="hint">ms, doubles each time</span></span>
          <input type="number" min={0} aria-label={`${label} retry wait`} placeholder="1000" value={w.retryBackoff ?? ''}
            disabled={readOnly || !(Number(w.retries) > 0)} onChange={(e) => onChange({ retryBackoff: e.target.value })} />
        </label>
      </div>
      {Number(w.retries) > 0 && (
        <p className="muted small">
          Tries again on {w.retryStatuses?.trim() || '429 and 5xx'} answers and when the target can't be reached. Every attempt shows in the Callouts tab.
        </p>
      )}
      <div className="field">
        <span>Headers <span className="hint">{w.forward ? 'added to the forwarded headers' : 'templating works here too'}</span></span>
        <KeyValueRows rows={w.headers} onChange={(headers) => onChange({ headers })} disabled={readOnly} />
      </div>
      {w.forward ? (
        <p className="muted small">The incoming method, headers and body are sent as they arrived. Host and hop-by-hop headers are dropped.</p>
      ) : (
        w.method !== 'GET' && (
          <div className="field">
            <span className="body-label">
              Body
              {!readOnly && <DynamicValueMenu scope="callout" onInsert={insertIntoBody} />}
            </span>
            <CodeEditor
              label={`${label} body`}
              value={w.body}
              language="json"
              readOnly={readOnly}
              minHeight="80px"
              maxHeight="260px"
              editorRef={bodyRef}
              onChange={(body) => onChange({ body })}
            />
          </div>
        )
      )}
    </div>
  );
}
