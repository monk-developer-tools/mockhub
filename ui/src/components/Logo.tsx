export function Logo({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="7" fill="var(--ink)" />
      <circle cx="9" cy="16" r="3.2" fill="var(--m-post)" />
      <circle cx="23" cy="16" r="3.2" fill="var(--m-get)" />
      <path d="M12 16c3-6 5 6 8 0" stroke="var(--paper)" strokeWidth="2" fill="none" strokeLinecap="round" />
    </svg>
  );
}
