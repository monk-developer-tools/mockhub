import { BookOpenText } from '@phosphor-icons/react';
import type { ReactNode } from 'react';

interface Props {
  title: ReactNode;
  art?: ReactNode;
  children?: ReactNode;
  actions?: ReactNode;
  docs?: string;
}

export function EmptyState({ title, art, children, actions, docs }: Props) {
  return (
    <div className="empty">
      {art}
      <h2>{title}</h2>
      {typeof children === 'string' ? <p>{children}</p> : children}
      {(actions || docs) && (
        <div className="btn-row empty-actions">
          {actions}
          {docs && (
            <a className="btn ghost" href={docs} target="_blank" rel="noreferrer">
              <BookOpenText size={15} /> Read the docs
            </a>
          )}
        </div>
      )}
    </div>
  );
}
