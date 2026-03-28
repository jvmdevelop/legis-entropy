import logo from '../assets/logo.svg';

interface LogoProps {
  size?: number;
  className?: string;
}

export function Logo({ size = 36, className }: LogoProps) {
  const h = size;
  const w = Math.round(size * (52 / 28));

  return (
    <img
      src={logo}
      width={w}
      height={h}
      alt="Logo"
      className={className}
    />
  );
}
