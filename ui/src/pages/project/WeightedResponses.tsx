import { Plus, Trash } from '@phosphor-icons/react';
import { CodeEditor } from '../../components/CodeEditor';
import { emptyAlternative, weightShares, type Alternative, type StubForm } from '../../model/stubForm';
import { KeyValueRows } from './MatcherRows';

interface Props {
  form: StubForm;
  update: (patch: Partial<StubForm>) => void;
  readOnly: boolean;
  soap: boolean;
}

export function WeightedResponses({ form, update, readOnly, soap }: Props) {
  const shares = weightShares(form);
  const change = (i: number, patch: Partial<Alternative>) =>
    update({ alternatives: form.alternatives.map((a, j) => (j === i ? { ...a, ...patch } : a)) });
  const remove = (i: number) => update({ alternatives: form.alternatives.filter((_, j) => j !== i) });
  const add = () => update({ alternatives: [...form.alternatives, emptyAlternative()] });

  return (
    <div className="section">
      <h3>Other responses <span className="muted small">optional: answer with one of several responses, picked at random by weight</span></h3>
      {form.alternatives.length === 0 ? (
        !readOnly && (
          <div>
            <button type="button" className="btn small" onClick={add}><Plus size={14} /> Add another response</button>
          </div>
        )
      ) : (
        <div className="rows weighted">
          <div className="weighted-row">
            <label className="field">
              <span>Weight</span>
              <input type="number" min={0} step="any" aria-label="Weight of the response above" value={form.mainWeight} disabled={readOnly} onChange={(e) => update({ mainWeight: e.target.value })} />
            </label>
            <p className="weighted-label"><strong>The response above</strong> <span className="muted small">status {form.status || 200} · about {shares[0]}% of calls</span></p>
          </div>
          {form.alternatives.map((a, i) => (
            <div className="weighted-alt" key={i} role="group" aria-label={`Other response ${i + 1}`}>
              <div className="weighted-row">
                <label className="field">
                  <span>Weight</span>
                  <input type="number" min={0} step="any" aria-label={`Weight of other response ${i + 1}`} value={a.weight} disabled={readOnly} onChange={(e) => change(i, { weight: e.target.value })} />
                </label>
                <label className="field">
                  <span>Status</span>
                  <input type="number" className="mono" aria-label={`Status of other response ${i + 1}`} value={a.status} disabled={readOnly} onChange={(e) => change(i, { status: e.target.value })} />
                </label>
                <p className="weighted-label muted small">about {shares[i + 1]}% of calls</p>
                {!readOnly && (
                  <button type="button" className="btn ghost icon small" aria-label={`Remove other response ${i + 1}`} onClick={() => remove(i)}>
                    <Trash size={15} />
                  </button>
                )}
              </div>
              <div className="field">
                <span>Headers <span className="hint">added to or replacing the headers above</span></span>
                <KeyValueRows rows={a.headers} disabled={readOnly} onChange={(headers) => change(i, { headers })} />
              </div>
              <div className="field">
                <span>Body <span className="hint">leave empty to keep the body above</span></span>
                <CodeEditor
                  label={`Body of other response ${i + 1}`}
                  value={a.body}
                  language={soap ? 'xml' : 'json'}
                  readOnly={readOnly}
                  minHeight="60px"
                  maxHeight="220px"
                  onChange={(body) => change(i, { body })}
                />
              </div>
            </div>
          ))}
          {!readOnly && (
            <div>
              <button type="button" className="btn small" onClick={add}><Plus size={14} /> Add another response</button>
            </div>
          )}
          <p className="muted small">
            {form.templating ? 'Templating applies to whichever response is picked. ' : ''}
            The answer carries an X-MockHub-Weighted-Choice header with the number of the response picked, 1 being the response above.
          </p>
        </div>
      )}
    </div>
  );
}
