import type { ReactNode } from 'react';

interface Props {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  tone?: 'default' | 'warn' | 'ok';
  icon?: ReactNode;
}

export function StatTile({ label, value, detail, tone = 'default', icon }: Props) {
  return (
    <div className={`stat-tile${tone === 'default' ? '' : ` ${tone}`}`}>
      <div className="stat-top">
        <span className="stat-label">{label}</span>
        {icon && <span className="stat-icon" aria-hidden="true">{icon}</span>}
      </div>
      <span className="stat-value num">{value}</span>
      {detail && <span className="stat-detail">{detail}</span>}
    </div>
  );
}
