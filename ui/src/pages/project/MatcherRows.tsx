import { Plus, X } from '@phosphor-icons/react';
import {
  BODY_OPERATORS,
  VALUE_OPERATORS,
  type BodyMatcher,
  type KeyValue,
  type ValueMatcher,
} from '../../model/stubForm';

const OPERATOR_LABELS: Record<string, string> = {
  equalTo: 'equals',
  contains: 'contains',
  matches: 'matches regex',
  doesNotMatch: 'does not match regex',
  doesNotContain: 'does not contain',
  absent: 'is absent',
  present: 'is present',
  equalToJson: 'JSON equals',
  matchesJsonPath: 'JSONPath',
  equalToXml: 'XML equals',
  matchesXPath: 'XPath',
};

export function ValueMatcherRows({
  label,
  rows,
  onChange,
  keyPlaceholder,
  disabled,
}: {
  label: string;
  rows: ValueMatcher[];
  onChange: (rows: ValueMatcher[]) => void;
  keyPlaceholder: string;
  disabled?: boolean;
}) {
  const update = (i: number, patch: Partial<ValueMatcher>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  return (
    <div className="rows">
      {rows.map((row, i) => (
        <div className="row matcher" key={i}>
          <input type="text" className="mono" aria-label={`${label} name`} placeholder={keyPlaceholder} value={row.key} disabled={disabled} onChange={(e) => update(i, { key: e.target.value })} />
          <select aria-label={`${label} operator`} value={row.operator} disabled={disabled} onChange={(e) => update(i, { operator: e.target.value as ValueMatcher['operator'] })}>
            {VALUE_OPERATORS.map((op) => (
              <option key={op} value={op}>{OPERATOR_LABELS[op]}</option>
            ))}
          </select>
          <input
            type="text"
            className="mono"
            aria-label={`${label} value`}
            value={row.value}
            disabled={disabled || row.operator === 'absent' || row.operator === 'present'}
            onChange={(e) => update(i, { value: e.target.value })}
          />
          <button className="btn ghost icon small" aria-label={`Remove ${label}`} disabled={disabled} onClick={() => onChange(rows.filter((_, j) => j !== i))}>
            <X size={14} />
          </button>
        </div>
      ))}
      {!disabled && (
        <div>
          <button className="btn small" onClick={() => onChange([...rows, { key: '', operator: 'equalTo', value: '', caseInsensitive: false }])}>
            <Plus size={14} /> Add {label.toLowerCase()}
          </button>
        </div>
      )}
    </div>
  );
}

export function BodyMatcherRows({ rows, onChange, disabled }: { rows: BodyMatcher[]; onChange: (rows: BodyMatcher[]) => void; disabled?: boolean }) {
  const update = (i: number, patch: Partial<BodyMatcher>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  return (
    <div className="rows">
      {rows.map((row, i) => {
        const multiline = row.operator === 'equalToJson' || row.operator === 'equalToXml';
        const hasExpected = row.operator === 'matchesJsonPath' || row.operator === 'matchesXPath';
        return (
          <div className="row body-matcher" key={i}>
            <select aria-label="Body matcher type" value={row.operator} disabled={disabled} onChange={(e) => update(i, { operator: e.target.value as BodyMatcher['operator'] })}>
              {BODY_OPERATORS.map((op) => (
                <option key={op} value={op}>{OPERATOR_LABELS[op]}</option>
              ))}
            </select>
            <div className="rows">
              {multiline ? (
                <textarea className="mono" rows={4} aria-label="Expected body" value={row.value} disabled={disabled} onChange={(e) => update(i, { value: e.target.value })} />
              ) : (
                <input
                  type="text"
                  className="mono"
                  aria-label="Body expression"
                  placeholder={row.operator === 'matchesJsonPath' ? '$.customer.id' : row.operator === 'matchesXPath' ? "//*[local-name()='id']/text()" : ''}
                  value={row.value}
                  disabled={disabled}
                  onChange={(e) => update(i, { value: e.target.value })}
                />
              )}
              {hasExpected && (
                <input
                  type="text"
                  className="mono"
                  aria-label="Expected value"
                  placeholder="optional: value it must equal"
                  value={row.expectedValue}
                  disabled={disabled}
                  onChange={(e) => update(i, { expectedValue: e.target.value })}
                />
              )}
              {row.operator === 'equalToJson' && (
                <div className="btn-row">
                  <label className="check">
                    <input type="checkbox" checked={row.ignoreExtraElements} disabled={disabled} onChange={(e) => update(i, { ignoreExtraElements: e.target.checked })} />
                    Ignore extra fields
                  </label>
                  <label className="check">
                    <input type="checkbox" checked={row.ignoreArrayOrder} disabled={disabled} onChange={(e) => update(i, { ignoreArrayOrder: e.target.checked })} />
                    Ignore array order
                  </label>
                </div>
              )}
            </div>
            <button className="btn ghost icon small" aria-label="Remove body matcher" disabled={disabled} onClick={() => onChange(rows.filter((_, j) => j !== i))}>
              <X size={14} />
            </button>
          </div>
        );
      })}
      {!disabled && (
        <div>
          <button
            className="btn small"
            onClick={() => onChange([...rows, { operator: 'equalToJson', value: '', ignoreArrayOrder: false, ignoreExtraElements: false, expectedValue: '' }])}
          >
            <Plus size={14} /> Add body matcher
          </button>
        </div>
      )}
    </div>
  );
}

export function KeyValueRows({ rows, onChange, disabled }: { rows: KeyValue[]; onChange: (rows: KeyValue[]) => void; disabled?: boolean }) {
  const update = (i: number, patch: Partial<KeyValue>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  return (
    <div className="rows">
      {rows.map((row, i) => (
        <div className="row kv" key={i}>
          <input type="text" className="mono" aria-label="Header name" placeholder="Header" value={row.key} disabled={disabled} onChange={(e) => update(i, { key: e.target.value })} />
          <input type="text" className="mono" aria-label="Header value" value={row.value} disabled={disabled} onChange={(e) => update(i, { value: e.target.value })} />
          <button className="btn ghost icon small" aria-label="Remove header" disabled={disabled} onClick={() => onChange(rows.filter((_, j) => j !== i))}>
            <X size={14} />
          </button>
        </div>
      ))}
      {!disabled && (
        <div>
          <button className="btn small" onClick={() => onChange([...rows, { key: '', value: '' }])}>
            <Plus size={14} /> Add header
          </button>
        </div>
      )}
    </div>
  );
}
