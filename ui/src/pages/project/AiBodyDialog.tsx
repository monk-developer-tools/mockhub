import { Sparkle } from '@phosphor-icons/react';
import { useState } from 'react';
import { api } from '../../api/client';
import { CodeEditor } from '../../components/CodeEditor';
import { Modal } from '../../components/Modal';

export type BodyFormat = 'json' | 'xml' | 'text';

const IDEAS: Record<BodyFormat, string[]> = {
  json: ['A page of 3 orders with customer, items and totals', 'A validation error for a missing email field', 'A user profile with address and preferences'],
  xml: ['A SOAP response with an account balance and currency', 'A SOAP fault for an unknown customer id'],
  text: ['A CSV with 5 products: id, name, price', 'A plain-text maintenance notice'],
};

interface Props {
  projectId: string;
  format: BodyFormat;
  method: string;
  path: string;
  status: number;
  current: string;
  onUse: (body: string) => void;
  onClose: () => void;
}

export function AiBodyDialog({ projectId, format, method, path, status, current, onUse, onClose }: Props) {
  const [description, setDescription] = useState('');
  const [buildOn, setBuildOn] = useState(false);
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const generate = async () => {
    setBusy(true);
    setError('');
    try {
      const { body } = await api.generateBody(projectId, { description, format, method, path, status, current: buildOn ? current : '' });
      setResult(body);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title="Generate a body with AI"
      description={`Describe the ${format.toUpperCase()} this stub should return. You can edit the result before using it.`}
      onClose={onClose}
      wide
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          {result ? (
            <>
              <button className="btn" onClick={generate} disabled={busy || !description.trim()}>{busy ? 'Generating…' : 'Try again'}</button>
              <button className="btn primary" onClick={() => onUse(result)}>Use this body</button>
            </>
          ) : (
            <button className="btn primary" onClick={generate} disabled={busy || !description.trim()}>
              <Sparkle size={14} /> {busy ? 'Generating…' : 'Generate'}
            </button>
          )}
        </>
      }
    >
      <div className="stack">
        <label className="field">
          <span>What should it return?</span>
          <textarea
            rows={3}
            autoFocus
            value={description}
            placeholder={IDEAS[format][0]}
            onChange={(e) => setDescription(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.metaKey || e.ctrlKey) && description.trim() && !busy) generate();
            }}
          />
        </label>
        {!description && (
          <div className="chips" role="group" aria-label="Ideas">
            {IDEAS[format].map((idea) => (
              <button key={idea} type="button" className="chip" onClick={() => setDescription(idea)}>{idea}</button>
            ))}
          </div>
        )}
        {current.trim() && (
          <label className="check">
            <input type="checkbox" checked={buildOn} onChange={(e) => setBuildOn(e.target.checked)} />
            Change the current body instead of starting from scratch
          </label>
        )}
        {error && <div className="callout danger" role="alert">{error}</div>}
        {result && <CodeEditor label="Generated body" value={result} language={format} minHeight="200px" onChange={setResult} />}
      </div>
    </Modal>
  );
}
