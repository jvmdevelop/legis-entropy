import logoDark from '../assets/logo-dark.svg';
import logoLight from '../assets/logo-light.svg';
import type { Theme } from '../hooks/useTheme';

interface LogoProps {
  size?: number;
  className?: string;
  theme?: Theme;
}

export function Logo({ size = 36, className, theme = 'dark' }: LogoProps) {
  const h = size;
  const w = Math.round(size * (52 / 28));

  return (
    <img
      src={theme === 'light' ? logoLight : logoDark}
      width={w}
      height={h}
      alt="Legis Entropy"
      className={className}
    />
  );
}
