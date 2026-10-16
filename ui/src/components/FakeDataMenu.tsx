import { CaretDown, MagicWand } from '@phosphor-icons/react';
import { useEffect, useRef, useState } from 'react';
import { useToast } from './Toasts';

export const FAKE_HELPERS: { group: string; items: { label: string; expression: string }[] }[] = [
  {
    group: 'People',
    items: [
      { label: 'Full name', expression: "{{fake 'Name.fullName'}}" },
      { label: 'First name', expression: "{{fake 'Name.firstName'}}" },
      { label: 'E-mail', expression: "{{fake 'Internet.emailAddress'}}" },
      { label: 'Phone', expression: "{{fake 'PhoneNumber.phoneNumber'}}" },
      { label: 'Username', expression: "{{fake 'Internet.username'}}" },
    ],
  },
  {
    group: 'Places',
    items: [
      { label: 'Street address', expression: "{{fake 'Address.streetAddress'}}" },
      { label: 'City', expression: "{{fake 'Address.city'}}" },
      { label: 'Country', expression: "{{fake 'Address.country'}}" },
      { label: 'Postcode', expression: "{{fake 'Address.zipCode'}}" },
    ],
  },
  {
    group: 'Business',
    items: [
      { label: 'Company', expression: "{{fake 'Company.name'}}" },
      { label: 'Product', expression: "{{fake 'Commerce.productName'}}" },
      { label: 'Price', expression: "{{fake 'Commerce.price'}}" },
      { label: 'IBAN', expression: "{{fake 'Finance.iban'}}" },
    ],
  },
  {
    group: 'Other',
    items: [
      { label: 'Number 1–100', expression: "{{fake 'Number.numberBetween' 1 100}}" },
      { label: 'Sentence', expression: "{{fake 'Lorem.sentence'}}" },
      { label: 'UUID', expression: "{{randomValue type='UUID'}}" },
      { label: 'In German', expression: "{{fake 'Name.fullName' locale='de'}}" },
    ],
  },
];

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export function FakeDataMenu({ onPick }: { onPick: () => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const toast = useToast();

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => {
      document.removeEventListener('mousedown', close);
    };
  }, [open]);

  const pick = async (expression: string) => {
    setOpen(false);
    onPick();
    const copied = await copy(expression);
    toast.notify(copied ? `Copied ${expression}. Paste it into the body.` : `Use ${expression} in the body.`);
  };

  return (
    <div className="fake-menu" ref={ref}>
      <button type="button" className="btn small" aria-expanded={open} aria-haspopup="menu" onClick={() => setOpen(!open)}>
        <MagicWand size={14} /> Fake data <CaretDown size={12} />
      </button>
      {open && (
        <div className="fake-menu-pop" role="menu" aria-label="Fake data">
          {FAKE_HELPERS.map((g) => (
            <div key={g.group} className="fake-group">
              <span className="fake-group-name">{g.group}</span>
              {g.items.map((i) => (
                <button key={i.label} type="button" role="menuitem" className="fake-item" onClick={() => pick(i.expression)} title={i.expression}>
                  {i.label}
                </button>
              ))}
            </div>
          ))}
          <p className="muted small fake-foot">Any Datafaker expression works, e.g. <span className="mono">{"{{fake 'Book.title'}}"}</span>.</p>
        </div>
      )}
    </div>
  );
}
