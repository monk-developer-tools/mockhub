import type { ReactNode } from 'react';

interface Props {
  title: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

export function PageHeader({ title, description, icon, meta, actions, className }: Props) {
  return (
    <header className={`page-header${className ? ` ${className}` : ''}`}>
      <div className="page-header-main">
        <div className="page-title">
          {icon && <span className="page-title-icon">{icon}</span>}
          <h1>{title}</h1>
        </div>
        {meta && <div className="page-meta">{meta}</div>}
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}
