export function Skeleton({ rows = 6, label = 'Loading' }: { rows?: number; label?: string }) {
  return (
    <div className="skeleton" role="status" aria-label={label}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton-row" style={{ width: `${92 - ((i * 17) % 35)}%` }} />
      ))}
    </div>
  );
}
