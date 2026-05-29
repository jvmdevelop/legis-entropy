import { cn } from '@/shared/lib/cn'

interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const sizes = {
  sm: 'w-4 h-4 border-2',
  md: 'w-6 h-6 border-2',
  lg: 'w-8 h-8 border-[3px]',
}

export function Spinner({ size = 'md', className }: SpinnerProps) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={cn(
        'inline-block rounded-full border-[var(--color-accent)] border-t-transparent animate-spin',
        sizes[size],
        className,
      )}
    />
  )
}

export function FullscreenSpinner() {
  return (
    <div className="fixed inset-0 flex items-center justify-center bg-[var(--color-bg)]">
      <div className="flex flex-col items-center gap-5">
        <div className="relative">
          <div className="w-14 h-14 rounded-full border-2 border-[var(--color-accent)]/15" />
          <div className="absolute inset-0 w-14 h-14 rounded-full border-2 border-transparent border-t-[var(--color-accent)] animate-spin" />
        </div>
        <p className="text-[var(--color-text-muted)] text-sm font-medium tracking-wider uppercase"
          style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: '11px', letterSpacing: '0.14em' }}>
          Loading
        </p>
      </div>
    </div>
  )
}
