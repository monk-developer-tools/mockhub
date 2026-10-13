import { useState } from 'react';

interface Props {
  counts: number[];
  bucketMinutes?: number;
  height?: number;
  label: string;
}

function bucketLabel(index: number, total: number, minutes: number) {
  const end = (total - 1 - index) * minutes;
  const start = end + minutes;
  return end === 0 ? `last ${minutes} min` : `${start}–${end} min ago`;
}

export function ActivityBars({ counts, bucketMinutes = 5, height = 44, label }: Props) {
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(1, ...counts);
  const total = counts.reduce((a, b) => a + b, 0);
  const width = 100 / counts.length;
  return (
    <figure className="activity" aria-label={`${label}: ${total} requests in the last hour`}>
      <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" height={height} role="img" aria-hidden="true">
        <line x1="0" x2="100" y1={height - 0.5} y2={height - 0.5} className="activity-baseline" />
        {counts.map((c, i) => {
          const h = c === 0 ? 0 : Math.max(3, (c / max) * (height - 4));
          return (
            <g key={i} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
              <rect x={i * width} y={0} width={width} height={height} className="activity-hit" />
              {h > 0 && (
                <path
                  className={`activity-bar${hover === i ? ' active' : ''}`}
                  d={roundedTop(i * width + 0.6, height - 1, width - 1.2, h, 1.6)}
                />
              )}
            </g>
          );
        })}
      </svg>
      {hover !== null && (
        <div className="activity-tip" style={{ left: `${(hover + 0.5) * width}%` }} role="tooltip">
          <strong className="num">{counts[hover]}</strong> {counts[hover] === 1 ? 'request' : 'requests'}
          <span>{bucketLabel(hover, counts.length, bucketMinutes)}</span>
        </div>
      )}
      <table className="sr-only">
        <caption>{label}</caption>
        <tbody>
          {counts.map((c, i) => (
            <tr key={i}>
              <th scope="row">{bucketLabel(i, counts.length, bucketMinutes)}</th>
              <td>{c}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

function roundedTop(x: number, baseline: number, w: number, h: number, r: number) {
  const radius = Math.min(r, w / 2, h);
  const top = baseline - h;
  return `M${x},${baseline} V${top + radius} Q${x},${top} ${x + radius},${top} H${x + w - radius} Q${x + w},${top} ${x + w},${top + radius} V${baseline} Z`;
}
