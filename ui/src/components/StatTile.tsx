import type { ReactNode } from 'react';

interface Props {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  tone?: 'default' | 'warn';
  icon?: ReactNode;
}

export function StatTile({ label, value, detail, tone = 'default', icon }: Props) {
  return (
    <div className={`stat-tile${tone === 'warn' ? ' warn' : ''}`}>
      <span className="stat-label">
        {icon}
        {label}
      </span>
      <span className="stat-value num">{value}</span>
      {detail && <span className="stat-detail">{detail}</span>}
    </div>
  );
}
