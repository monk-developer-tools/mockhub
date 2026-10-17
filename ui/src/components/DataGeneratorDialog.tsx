import { Plus, Table, X } from '@phosphor-icons/react';
import { useState } from 'react';
import { api } from '../api/client';
import type { GeneratorField } from '../api/types';
import { fieldNameFor, GENERATOR_TYPES, isKnownType, STARTER_FIELDS, wrapRecords } from '../model/generator';
import { CodeEditor } from './CodeEditor';
import { Modal } from './Modal';

const CUSTOM = '__custom';

interface Props {
  title?: string;
  useLabel?: string;
  allowWrap?: boolean;
  initialCount?: number;
  onUse: (json: string) => void;
  onClose: () => void;
}

export function DataGeneratorDialog({ title = 'Generate data', useLabel = 'Use this data', allowWrap = true, initialCount = 10, onUse, onClose }: Props) {
  const [fields, setFields] = useState<GeneratorField[]>(STARTER_FIELDS);
  const [custom, setCustom] = useState<Record<number, boolean>>({});
  const [count, setCount] = useState(String(initialCount));
  const [locale, setLocale] = useState('');
  const [wrap, setWrap] = useState('');
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (i: number, patch: Partial<GeneratorField>) => setFields((f) => f.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  const generate = async () => {
    setBusy(true);
    setError('');
    try {
      const { records } = await api.generateData({ fields, count: Number(count) || 10, locale: locale.trim() || undefined });
      setResult(wrapRecords(records, allowWrap ? wrap : ''));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={title}
      description="Pick the fields and how many records you want. Dots in a field name nest it, e.g. address.city."
      onClose={onClose}
      wide
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn" onClick={generate} disabled={busy || fields.length === 0}>
            <Table size={14} /> {busy ? 'Generating…' : result ? 'Generate again' : 'Generate'}
          </button>
          <button className="btn primary" disabled={!result} onClick={() => onUse(result)}>{useLabel}</button>
        </>
      }
    >
      <div className="stack">
        <div className="rows generator-fields">
          {fields.map((f, i) => {
            const isCustom = custom[i] || !isKnownType(f.expression);
            return (
              <div className="row generator-row" key={i}>
                <input type="text" className="mono" aria-label={`Field ${i + 1} name`} value={f.name} onChange={(e) => set(i, { name: e.target.value })} />
                <select
                  aria-label={`Field ${i + 1} type`}
                  value={isCustom ? CUSTOM : f.expression}
                  onChange={(e) => {
                    if (e.target.value === CUSTOM) {
                      setCustom((c) => ({ ...c, [i]: true }));
                      return;
                    }
                    setCustom((c) => ({ ...c, [i]: false }));
                    set(i, { expression: e.target.value });
                  }}
                >
                  {GENERATOR_TYPES.map((g) => (
                    <optgroup key={g.group} label={g.group}>
                      {g.items.map((t) => <option key={t.expression} value={t.expression}>{t.label}</option>)}
                    </optgroup>
                  ))}
                  <option value={CUSTOM}>Custom Datafaker expression…</option>
                </select>
                {isCustom ? (
                  <input type="text" className="mono" aria-label={`Field ${i + 1} expression`} placeholder="Animal.name" value={isKnownType(f.expression) ? '' : f.expression}
                    onChange={(e) => set(i, { expression: e.target.value })} />
                ) : <span />}
                <button type="button" className="btn ghost icon small" aria-label={`Remove field ${i + 1}`} onClick={() => setFields((x) => x.filter((_, j) => j !== i))}>
                  <X size={14} />
                </button>
              </div>
            );
          })}
          <div>
            <button type="button" className="btn small" onClick={() => setFields((x) => [...x, { name: fieldNameFor('Name.fullName', x.map((f) => f.name)), expression: 'Name.fullName' }])}>
              <Plus size={13} /> Add field
            </button>
          </div>
        </div>
        <div className={`grid ${allowWrap ? 'three' : 'two'}`}>
          <label className="field">
            <span>Records</span>
            <input type="number" min={1} max={1000} value={count} onChange={(e) => setCount(e.target.value)} />
          </label>
          <label className="field">
            <span>Language <span className="hint">optional, e.g. de, fr, ja</span></span>
            <input type="text" value={locale} placeholder="server default" onChange={(e) => setLocale(e.target.value)} />
          </label>
          {allowWrap && (
            <label className="field">
              <span>Wrap in <span className="hint">optional key, e.g. items</span></span>
              <input type="text" className="mono" value={wrap} placeholder="plain list" onChange={(e) => setWrap(e.target.value)} />
            </label>
          )}
        </div>
        {error && <div className="callout danger" role="alert">{error}</div>}
        {result && <CodeEditor label="Generated data" value={result} onChange={setResult} minHeight="180px" maxHeight="320px" />}
      </div>
    </Modal>
  );
}
