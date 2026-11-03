import type { HeaderPair } from '../api/types';

export function HeaderTable({ headers }: { headers: HeaderPair[] }) {
  if (headers.length === 0) return <p className="muted small">No headers</p>;
  return (
    <table className="kv-table">
      <tbody>
        {headers.map((h, i) => (
          <tr key={`${h.name}-${i}`}>
            <td>{h.name}</td>
            <td>{h.value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
