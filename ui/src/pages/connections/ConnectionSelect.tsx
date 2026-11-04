import { Link } from 'react-router-dom';

export function ConnectionSelect({ label, value, keys, disabled, onChange }: {
  label: string;
  value: string;
  keys: string[];
  disabled: boolean;
  onChange: (key: string) => void;
}) {
  const options = value && !keys.includes(value) ? [...keys, value] : keys;
  return (
    <label className="field">
      <span>{label} <span className="hint">adds a token or key from <Link to="/org/connections">Connections</Link></span></span>
      <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
        <option value="">No connection</option>
        {options.map((k) => <option key={k} value={k}>{k}{keys.includes(k) ? '' : ' (missing)'}</option>)}
      </select>
    </label>
  );
}
