import { useId } from 'react';

export function Logo({ size = 24 }: { size?: number }) {
  const gradient = `logo-${useId().replace(/:/g, '')}`;
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className="logo">
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#5b7cff" />
          <stop offset="1" stopColor="#1fbf9f" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="8" fill={`url(#${gradient})`} />
      <path d="M8 21.5V11l5.2 6.4L18.4 11v10.5" fill="none" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M18.4 17.4h2.4l1.6-3.2 1.6 6.2 1.2-3h.8" fill="none" stroke="#fff" strokeOpacity="0.85" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
