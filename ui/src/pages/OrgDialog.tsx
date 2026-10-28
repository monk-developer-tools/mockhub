import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { Modal } from '../components/Modal';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';

export function OrgDialog({ onClose }: { onClose: () => void }) {
  const { refreshSession, setCurrentOrg } = useApp();
  const toast = useToast();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const org = await api.createOrg({ name, description });
      await refreshSession();
      setCurrentOrg(org.id);
      toast.notify(`Organisation ${org.name} created`);
      onClose();
      navigate('/org');
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="New organisation" description="Your company or department. It starts with a General team; add more teams, and each team owns its projects." onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <label className="field">
          <span>Name</span>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Acme" required />
        </label>
        <label className="field">
          <span>Description <span className="hint">optional</span></span>
          <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy || !name.trim()}>Create organisation</button>
        </div>
      </form>
    </Modal>
  );
}
