export function MockFlowArt() {
  return (
    <svg className="flow-art" viewBox="0 0 460 250" role="img" aria-label="Your app calls MockHub instead of the real service">
      <defs>
        <linearGradient id="flow-hub" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#5b7cff" />
          <stop offset="1" stopColor="#1fbf9f" />
        </linearGradient>
      </defs>

      <g className="flow-node">
        <rect x="14" y="88" width="120" height="74" rx="12" />
        <path className="flow-glyph" d="M60 108l-8 8 8 8M88 108l8 8-8 8M78 104l-8 24" />
        <text x="74" y="154" textAnchor="middle">Your app</text>
      </g>

      <path className="flow-wire" d="M134 125H170" />
      <path className="flow-wire flow-live" d="M134 125H170" />

      <g>
        <rect x="170" y="74" width="120" height="102" rx="16" fill="url(#flow-hub)" />
        <path d="M206 140V108l15.6 19.2L237.2 108v32" fill="none" stroke="#fff" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M237.2 128h7l4.8-9.6 4.8 18.6 3.6-9h2.4" fill="none" stroke="#fff" strokeOpacity="0.85" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
        <text className="flow-hub-label" x="230" y="164" textAnchor="middle">MockHub</text>
      </g>

      <path className="flow-wire flow-idle" d="M290 125H326" />

      <g className="flow-node flow-node-off">
        <rect x="326" y="88" width="120" height="74" rx="12" />
        <path className="flow-glyph" d="M370 106h32M370 116h32M370 126h20" />
        <text x="386" y="154" textAnchor="middle">Real service</text>
      </g>

      <g className="flow-chip">
        <rect x="64" y="24" width="152" height="30" rx="15" />
        <text x="84" y="44"><tspan className="flow-method">GET</tspan> /orders/42</text>
      </g>
      <g className="flow-chip flow-chip-ok">
        <rect x="200" y="200" width="128" height="30" rx="15" />
        <text x="220" y="220"><tspan className="flow-status">200</tspan> in 4 ms</text>
      </g>
      <g className="flow-chip flow-chip-off">
        <rect x="336" y="44" width="100" height="26" rx="13" />
        <text x="386" y="61" textAnchor="middle">not needed</text>
      </g>
    </svg>
  );
}

export type EmptyKind = 'stubs' | 'requests' | 'scenarios' | 'projects';

export function EmptyArt({ kind }: { kind: EmptyKind }) {
  return (
    <svg className="empty-art" viewBox="0 0 160 96" aria-hidden="true">
      {kind === 'stubs' && (
        <>
          <rect className="ea-card ea-back" x="40" y="10" width="96" height="56" rx="9" />
          <rect className="ea-card" x="24" y="26" width="96" height="58" rx="9" />
          <rect className="ea-pill" x="34" y="38" width="26" height="12" rx="6" />
          <rect className="ea-line" x="66" y="41" width="42" height="6" rx="3" />
          <rect className="ea-line ea-faint" x="34" y="58" width="74" height="5" rx="2.5" />
          <rect className="ea-line ea-faint" x="34" y="69" width="52" height="5" rx="2.5" />
        </>
      )}
      {kind === 'requests' && (
        <>
          <rect className="ea-card" x="14" y="16" width="132" height="64" rx="10" />
          <path className="ea-wave" d="M26 54h22l8-18 10 30 9-22 7 10h52" />
          <circle className="ea-dot" cx="134" cy="54" r="4" />
        </>
      )}
      {kind === 'scenarios' && (
        <>
          <circle className="ea-node" cx="32" cy="48" r="12" />
          <circle className="ea-node" cx="80" cy="26" r="12" />
          <circle className="ea-node ea-accent" cx="80" cy="70" r="12" />
          <circle className="ea-node" cx="128" cy="48" r="12" />
          <path className="ea-link" d="M43 42l26-10M43 54l26 10M91 32l26 10M91 64l26-10" />
        </>
      )}
      {kind === 'projects' && (
        <>
          <rect className="ea-card ea-back" x="44" y="8" width="88" height="60" rx="10" />
          <rect className="ea-card" x="28" y="24" width="88" height="62" rx="10" />
          <rect className="ea-pill" x="40" y="36" width="20" height="20" rx="6" />
          <rect className="ea-line" x="66" y="40" width="38" height="6" rx="3" />
          <rect className="ea-line ea-faint" x="40" y="66" width="64" height="5" rx="2.5" />
        </>
      )}
    </svg>
  );
}
