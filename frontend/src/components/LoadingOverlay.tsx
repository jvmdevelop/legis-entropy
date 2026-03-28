import { Logo } from './Logo';

// Sinusoidal ring: polar coordinates with radial sine wave modulation
// r(θ) = R + A * sin(freq * θ)
function SineRing({ size = 120 }: { size?: number }) {
  const cx = size / 2;
  const cy = size / 2;
  const R = size * 0.38;
  const A = size * 0.06;
  const freq = 8;
  const steps = 360;

  const points: string[] = [];
  for (let i = 0; i <= steps; i++) {
    const theta = (i / steps) * 2 * Math.PI;
    const r = R + A * Math.sin(freq * theta);
    const x = cx + r * Math.cos(theta);
    const y = cy + r * Math.sin(theta);
    points.push(`${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`);
  }
  points.push('Z');
  const d = points.join(' ');

  const dashLen = 2 * Math.PI * R * 0.7;
  const gapLen = 2 * Math.PI * R * 0.3;

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ position: 'absolute', top: 0, left: 0 }}>
      {/* Faint track */}
      <path d={d} fill="none" stroke="#e5e7eb" strokeWidth="1.5" />
      {/* Animated arc */}
      <path
        d={d}
        fill="none"
        stroke="#1f2937"
        strokeWidth="2"
        strokeLinecap="round"
        strokeDasharray={`${dashLen} ${gapLen}`}
        style={{
          animation: 'sineRotate 1.4s linear infinite',
          transformOrigin: `${cx}px ${cy}px`,
        }}
      />
      <style>{`
        @keyframes sineRotate {
          from { stroke-dashoffset: 0; transform: rotate(0deg); }
          to   { stroke-dashoffset: 0; transform: rotate(360deg); }
        }
      `}</style>
    </svg>
  );
}

export function LoadingOverlay() {
  const ringSize = 100;
  const logoSize = 28;

  return (
    <div className="absolute inset-0 flex items-center justify-center z-20 bg-white/80 backdrop-blur-md">
      <div
        style={{
          position: 'relative',
          width: ringSize,
          height: ringSize,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <SineRing size={ringSize} />
        <Logo size={logoSize} />
      </div>
    </div>
  );
}
