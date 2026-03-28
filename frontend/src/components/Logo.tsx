interface LogoProps {
  size?: number;
  className?: string;
}

/**
 * "LE" geometric wordmark — v0-style: bold filled rectangles, pure black, no background box.
 * viewBox 52×28: L occupies [0-18], gap [18-22], E occupies [22-52].
 */
export function Logo({ size = 36, className }: LogoProps) {
  const h = size;
  const w = Math.round(size * (52 / 28));

  return (
    <svg
      width={w}
      height={h}
      viewBox="0 0 52 28"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
    >
      {/* — L — */}
      {/* Vertical bar */}
      <rect x="0" y="0" width="5" height="28" rx="1" fill="#0a0a0a" />
      {/* Bottom horizontal bar */}
      <rect x="0" y="22" width="18" height="6" rx="1" fill="#0a0a0a" />

      {/* — E — */}
      {/* Vertical bar */}
      <rect x="22" y="0" width="5" height="28" rx="1" fill="#0a0a0a" />
      {/* Top bar */}
      <rect x="22" y="0" width="20" height="6" rx="1" fill="#0a0a0a" />
      {/* Middle bar (slightly shorter) */}
      <rect x="22" y="11" width="15" height="6" rx="1" fill="#0a0a0a" />
      {/* Bottom bar */}
      <rect x="22" y="22" width="20" height="6" rx="1" fill="#0a0a0a" />
    </svg>
  );
}
