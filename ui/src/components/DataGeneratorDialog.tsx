import { Table } from '@phosphor-icons/react';
import { useState } from 'react';
import { CodeEditor } from './CodeEditor';
import { GeneratorForm, useGenerator } from './GeneratorForm';
import { Modal } from './Modal';
import { wrapRecords } from '../model/generator';

interface Props {
  title?: string;
  useLabel?: string;
  allowWrap?: boolean;
  initialCount?: number;
  onUse: (json: string) => void;
  onClose: () => void;
}

export function DataGeneratorDialog({ title = 'Generate data', useLabel = 'Use this data', allowWrap = true, initialCount = 10, onUse, onClose }: Props) {
  const g = useGenerator(initialCount);
  const [result, setResult] = useState('');
  const generate = async () => {
    const made = await g.generate();
    if (made) setResult(allowWrap ? g.wrapped(made) : wrapRecords(made, ''));
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
          <button className="btn" onClick={generate} disabled={g.busy || g.fields.length === 0}>
            <Table size={14} /> {g.busy ? 'Generating…' : result ? 'Generate again' : 'Generate'}
          </button>
          <button className="btn primary" disabled={!result} onClick={() => onUse(result)}>{useLabel}</button>
        </>
      }
    >
      <div className="stack">
        <GeneratorForm g={g} allowWrap={allowWrap} />
        {g.error && <div className="callout danger" role="alert">{g.error}</div>}
        {result && <CodeEditor label="Generated data" value={result} onChange={setResult} minHeight="180px" maxHeight="320px" />}
      </div>
    </Modal>
  );
}
