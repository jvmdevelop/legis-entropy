interface LogoProps {
  size?: number;
  className?: string;
}

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
      <defs>
        <linearGradient id="lg-a" x1="0" y1="0" x2="52" y2="28" gradientUnits="userSpaceOnUse">
          <stop stopColor="#6366f1" />
          <stop offset="1" stopColor="#8b5cf6" />
        </linearGradient>
      </defs>
      <rect x="0" y="0" width="5" height="28" rx="1" fill="url(#lg-a)" />
      <rect x="0" y="22" width="18" height="6" rx="1" fill="url(#lg-a)" />
      <rect x="22" y="0" width="5" height="28" rx="1" fill="url(#lg-a)" />
      <rect x="22" y="0" width="20" height="6" rx="1" fill="url(#lg-a)" />
      <rect x="22" y="11" width="15" height="6" rx="1" fill="url(#lg-a)" />
      <rect x="22" y="22" width="20" height="6" rx="1" fill="url(#lg-a)" />
    </svg>
  );
}
