import { IconContext, type IconProps } from '@phosphor-icons/react';
import type { ReactNode } from 'react';

const DEFAULTS: IconProps = { size: 16, weight: 'regular', 'aria-hidden': true };

export function IconDefaults({ children }: { children: ReactNode }) {
  return <IconContext.Provider value={DEFAULTS}>{children}</IconContext.Provider>;
}
