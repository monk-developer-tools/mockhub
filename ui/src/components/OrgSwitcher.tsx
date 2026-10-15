import { Building2, Check, ChevronsUpDown, Plus, Settings } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../state/AppContext';
import { RoleBadge } from './RoleBadge';

export function OrgSwitcher({ onNewOrg }: { onNewOrg: () => void }) {
  const { orgs, currentOrg, setCurrentOrg, isAdmin, roleIn } = useApp();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', esc);
    };
  }, [open]);

  const choose = (id: string) => {
    setCurrentOrg(id);
    setOpen(false);
    navigate('/');
  };

  return (
    <div className="org-switcher" ref={ref}>
      <button className="org-trigger" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className="org-avatar" aria-hidden="true">{(currentOrg?.name ?? '?').slice(0, 1).toUpperCase()}</span>
        <span className="org-name">
          <span className="org-label">Organisation</span>
          <strong>{currentOrg?.name ?? 'No organisation'}</strong>
        </span>
        <ChevronsUpDown size={14} />
      </button>
      {open && (
        <div className="org-menu" role="menu" aria-label="Organisations">
          {orgs.length === 0 && <p className="muted small" style={{ padding: '8px 10px' }}>You aren't in any organisation yet.</p>}
          {orgs.map((o) => (
            <button key={o.id} role="menuitemradio" aria-checked={o.id === currentOrg?.id} className="org-option" onClick={() => choose(o.id)}>
              <Building2 size={14} />
              <span>{o.name}</span>
              <RoleBadge role={roleIn(o.id)} />
              {o.id === currentOrg?.id && <Check size={14} />}
            </button>
          ))}
          <div className="org-menu-sep" />
          {currentOrg && (
            <button role="menuitem" className="org-option" onClick={() => { setOpen(false); navigate('/org'); }}>
              <Settings size={14} /> <span>Organisation settings</span>
            </button>
          )}
          {isAdmin && (
            <button role="menuitem" className="org-option" onClick={() => { setOpen(false); onNewOrg(); }}>
              <Plus size={14} /> <span>New organisation</span>
            </button>
          )}
        </div>
      )}
    </div>
  );
}
