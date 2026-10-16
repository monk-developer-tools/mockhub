const PAIRS: [string, string][] = [
  ['#5b7cff', '#7c3aed'],
  ['#0ea5e9', '#2563eb'],
  ['#10b981', '#0d9488'],
  ['#f59e0b', '#ea580c'],
  ['#ec4899', '#db2777'],
  ['#8b5cf6', '#6366f1'],
  ['#14b8a6', '#0891b2'],
  ['#f97316', '#e11d48'],
];

export function hashOf(text: string) {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export function initials(name: string) {
  const words = name.replace(/[@._-]+/g, ' ').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

export function Avatar({ name, seed, size = 28 }: { name: string; seed?: string; size?: number }) {
  const key = seed ?? name;
  const [from, to] = PAIRS[hashOf(key) % PAIRS.length];
  const id = `av-${hashOf(key).toString(36)}`;
  return (
    <svg className="avatar" width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={from} />
          <stop offset="1" stopColor={to} />
        </linearGradient>
      </defs>
      <circle cx="16" cy="16" r="16" fill={`url(#${id})`} />
      <text x="16" y="16" dy="0.36em" textAnchor="middle" fontSize={initials(name).length > 1 ? 12.5 : 14} fontWeight="600" fill="#fff">
        {initials(name)}
      </text>
    </svg>
  );
}

export function TeamMark({ name, seed, size = 26 }: { name: string; seed?: string; size?: number }) {
  const [from, to] = PAIRS[(hashOf(seed ?? name) + 3) % PAIRS.length];
  return (
    <span
      className="team-mark"
      style={{ width: size, height: size, fontSize: size * 0.42, background: `linear-gradient(135deg, ${from}, ${to})` }}
      aria-hidden="true"
    >
      {initials(name)}
    </span>
  );
}

export function PersonCell({ name, detail, seed }: { name: string; detail?: string; seed?: string }) {
  return (
    <span className="person-cell">
      <Avatar name={name} seed={seed} />
      <span className="person">
        <strong>{name}</strong>
        {detail && <span className="muted small">{detail}</span>}
      </span>
    </span>
  );
}
