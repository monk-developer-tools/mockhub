import { useState } from 'react';
import type { Stub } from '../../api/types';
import { Modal } from '../../components/Modal';
import { StubRoute } from '../../components/StubRoute';
import { TEMPLATE_CATEGORIES, TEMPLATES } from '../../model/templates';

export function TemplateGallery({ onPick, onClose }: { onPick: (stub: Stub) => void; onClose: () => void }) {
  const [category, setCategory] = useState(TEMPLATE_CATEGORIES[0]);
  return (
    <Modal title="Start from a template" description="Pick a pattern, then adjust it in the editor before saving." onClose={onClose} wide>
      <div className="gallery panel">
        <nav aria-label="Template categories">
          {TEMPLATE_CATEGORIES.map((c) => (
            <button key={c} aria-pressed={c === category} onClick={() => setCategory(c)}>
              {c}
            </button>
          ))}
        </nav>
        <div className="gallery-items">
          {TEMPLATES.filter((t) => t.category === category).map((t) => (
            <div className="gallery-item" key={t.id}>
              <div style={{ minWidth: 0, flex: 1 }}>
                <h3>{t.name}</h3>
                <p>{t.description}</p>
                <div style={{ marginTop: 6 }}>
                  <StubRoute stub={t.stub} />
                </div>
              </div>
              <button className="btn small" onClick={() => onPick(structuredClone(t.stub))}>
                Use template
              </button>
            </div>
          ))}
        </div>
      </div>
    </Modal>
  );
}
