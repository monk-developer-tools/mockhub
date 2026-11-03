import { ArrowDown, ArrowUp, DotsSixVertical } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Project, Stub } from '../../api/types';
import { StubRoute } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import { moveItem, sameOrder } from '../../model/order';
import { isDisabled } from '../../model/stubForm';

interface Props {
  project: Project;
  stubs: Stub[];
  canEdit: boolean;
  selectedId?: string;
  onSelect: (id: string) => void;
  onSaved: () => void;
}

export function MatchingOrder({ project, stubs, canEdit, selectedId, onSelect, onSaved }: Props) {
  const toast = useToast();
  const [saved, setSaved] = useState<string[] | null>(null);
  const [order, setOrder] = useState<string[]>([]);
  const [dragging, setDragging] = useState<number | null>(null);
  const [over, setOver] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let live = true;
    api.matchingOrder(project.id).then((ids) => {
      if (!live) return;
      setSaved(ids);
      setOrder(ids);
    }).catch(toast.fail);
    return () => {
      live = false;
    };
  }, [project.id, stubs]);

  if (!saved) return <p className="muted small order-note">Loading the matching order…</p>;

  const byId = new Map(stubs.map((s) => [String(s.id), s]));
  const changed = !sameOrder(order, saved);
  const fallback = project.runtime?.defaultResponse;

  const save = async () => {
    setBusy(true);
    try {
      await api.reorderStubs(project.id, order);
      setSaved(order);
      toast.notify('Matching order saved');
      onSaved();
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const drop = (to: number) => {
    if (dragging !== null) setOrder(moveItem(order, dragging, to));
    setDragging(null);
    setOver(null);
  };

  return (
    <div className="matching-order">
      <p className="muted small order-note">
        WireMock tries stubs from the top and the first match answers.{canEdit && ' Drag a stub, or use the arrows, to change who wins.'}
      </p>
      <ol aria-label="Matching order">
        {order.map((id, i) => {
          const stub = byId.get(id);
          if (!stub) return null;
          return (
            <li
              key={id}
              className={`order-row${over === i && dragging !== i ? ' drop-target' : ''}${dragging === i ? ' dragging' : ''}`}
              draggable={canEdit}
              onDragStart={(e) => {
                setDragging(i);
                e.dataTransfer.effectAllowed = 'move';
              }}
              onDragOver={(e) => {
                if (dragging === null) return;
                e.preventDefault();
                setOver(i);
              }}
              onDragEnd={() => {
                setDragging(null);
                setOver(null);
              }}
              onDrop={(e) => {
                e.preventDefault();
                drop(i);
              }}
            >
              <span className="order-num">{i + 1}</span>
              {canEdit && <DotsSixVertical size={14} className="order-grip" aria-hidden="true" />}
              <button type="button" className={`stub-item${isDisabled(stub) ? ' disabled' : ''}`} aria-current={stub.id === selectedId} onClick={() => onSelect(id)}>
                <StubRoute stub={stub} basePath={project.basePath} />
                <span className="stub-name">{isDisabled(stub) && <span className="tag off">off</span>}{String(stub.name ?? '')}</span>
              </button>
              {canEdit && (
                <span className="order-arrows">
                  <button type="button" className="btn icon small" aria-label={`Move ${String(stub.name ?? 'stub')} up`} disabled={i === 0} onClick={() => setOrder(moveItem(order, i, i - 1))}>
                    <ArrowUp size={12} />
                  </button>
                  <button type="button" className="btn icon small" aria-label={`Move ${String(stub.name ?? 'stub')} down`} disabled={i === order.length - 1} onClick={() => setOrder(moveItem(order, i, i + 1))}>
                    <ArrowDown size={12} />
                  </button>
                </span>
              )}
            </li>
          );
        })}
      </ol>
      <div className="order-fallback">
        <span className="order-num">·</span>
        <span>
          Nothing matched: {fallback ? <>the <strong>default response</strong> answers {fallback.status}</> : <>WireMock answers <strong>404</strong></>}
        </span>
      </div>
      {canEdit && changed && (
        <div className="order-save">
          <span className="muted small">Priorities become 10, 20, 30…</span>
          <button type="button" className="btn small" disabled={busy} onClick={() => setOrder(saved)}>Discard</button>
          <button type="button" className="btn small primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save order'}</button>
        </div>
      )}
    </div>
  );
}
