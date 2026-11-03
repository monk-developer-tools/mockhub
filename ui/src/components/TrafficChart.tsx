import { useState } from 'react';

export interface TrafficPoint {
  label: string;
  requests: number;
  unmatched: number;
  from: number;
  to: number;
}

interface Props {
  points: TrafficPoint[];
  axis: [string, string, string];
  label: string;
  height?: number;
  onSelect?: (from: number, to: number) => void;
}

export function TrafficChart({ points, axis, label, height = 96, onSelect }: Props) {
  const [hover, setHover] = useState<number | null>(null);
  const [anchor, setAnchor] = useState<number | null>(null);
  const max = Math.max(1, ...points.map((p) => p.requests));
  const width = 100 / Math.max(1, points.length);
  const bar = points.length > 120 ? width : width * 0.7;
  const scale = (n: number) => (n === 0 ? 0 : Math.max(2, (n / max) * (height - 4)));
  const selecting = anchor !== null && hover !== null && anchor !== hover;
  const low = selecting ? Math.min(anchor, hover) : null;
  const high = selecting ? Math.max(anchor, hover) : null;
  const tip = hover === null ? null : points[hover];

  const finish = () => {
    if (onSelect && low !== null && high !== null) onSelect(points[low].from, points[high].to);
    setAnchor(null);
  };

  return (
    <figure
      className={`traffic-chart${onSelect ? ' selectable' : ''}`}
      onMouseLeave={() => {
        setHover(null);
        setAnchor(null);
      }}
      onMouseUp={finish}
    >
      <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" height={height} aria-hidden="true">
        <line x1="0" x2="100" y1={height - 0.5} y2={height - 0.5} className="activity-baseline" />
        {low !== null && high !== null && <rect className="traffic-selection" x={low * width} y={0} width={(high - low + 1) * width} height={height} />}
        {points.map((p, i) => {
          const total = scale(p.requests);
          const missed = p.unmatched === 0 ? 0 : Math.max(1.5, (p.unmatched / p.requests) * total);
          const x = i * width + (width - bar) / 2;
          return (
            <g
              key={i}
              onMouseEnter={() => setHover(i)}
              onMouseDown={(e) => {
                if (!onSelect) return;
                e.preventDefault();
                setAnchor(i);
              }}
            >
              <rect x={i * width} y={0} width={width} height={height} className={`activity-hit${hover === i ? ' active' : ''}`} />
              {total > 0 && <rect className="traffic-matched" x={x} y={height - 1 - total} width={bar} height={total - missed} />}
              {missed > 0 && <rect className="traffic-unmatched" x={x} y={height - 1 - missed} width={bar} height={missed} />}
            </g>
          );
        })}
      </svg>
      <div className="activity-axis" aria-hidden="true">
        {axis.map((a, i) => <span key={i}>{a}</span>)}
      </div>
      {tip && hover !== null && (
        <div className="activity-tip" style={{ left: `${Math.min(88, Math.max(12, (hover + 0.5) * width))}%` }} role="tooltip">
          {low !== null && high !== null ? (
            <>
              <strong className="num">{points.slice(low, high + 1).reduce((a, p) => a + p.requests, 0)}</strong> requests
              <span>{points[low].label.split('–')[0]} – {lastPart(points[high].label)}</span>
            </>
          ) : (
            <>
              <strong className="num">{tip.requests}</strong> {tip.requests === 1 ? 'request' : 'requests'}
              {tip.unmatched > 0 && <span className="traffic-tip-miss">{tip.unmatched} unmatched</span>}
              <span>{tip.label}</span>
            </>
          )}
        </div>
      )}
      <table className="sr-only">
        <caption>{label}</caption>
        <tbody>
          {points.filter((p) => p.requests > 0).map((p) => (
            <tr key={p.from}>
              <th scope="row">{p.label}</th>
              <td>{p.requests} requests, {p.unmatched} unmatched</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

function lastPart(label: string) {
  const parts = label.split('–');
  return parts[parts.length - 1];
}
