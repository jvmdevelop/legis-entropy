import { type ButtonHTMLAttributes, type ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline'
type Size    = 'sm' | 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  icon?: ReactNode
  iconRight?: ReactNode
  fullWidth?: boolean
  children?: ReactNode
}

const base = `
  inline-flex items-center justify-center gap-2
  font-medium cursor-pointer select-none
  transition-all duration-150 ease-out
  focus-visible:outline-2 focus-visible:outline-offset-2
  disabled:opacity-50 disabled:pointer-events-none
`

const variants: Record<Variant, string> = {
  primary: `
    bg-[var(--color-accent)] text-white
    hover:bg-[var(--color-accent-hover)]
    active:scale-[0.98]
    shadow-[0_2px_8px_rgba(139,92,246,0.25)]
  `,
  secondary: `
    bg-[var(--color-surface)] text-[var(--color-text)]
    border border-[var(--color-border)]
    hover:bg-[var(--color-surface-2)] hover:border-[var(--color-accent)]
    active:scale-[0.98]
  `,
  ghost: `
    bg-transparent text-[var(--color-text-muted)]
    hover:bg-[var(--color-surface)]
    hover:text-[var(--color-text)]
    active:scale-[0.98]
  `,
  danger: `
    bg-red-50 text-[var(--color-error)]
    border border-red-100
    hover:bg-red-100
    active:scale-[0.98]
  `,
  outline: `
    bg-transparent text-[var(--color-accent)]
    border border-[var(--color-accent)]
    hover:bg-[var(--color-accent-glow)]
    active:scale-[0.98]
  `,
}

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs rounded-lg',
  md: 'h-10 px-4 text-sm rounded-lg',
  lg: 'h-11 px-5 text-sm rounded-xl',
}

export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  icon,
  iconRight,
  fullWidth = false,
  className,
  children,
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cn(
        base,
        variants[variant],
        sizes[size],
        fullWidth && 'w-full',
        className,
      )}
    >
      {loading ? (
        <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
      ) : (
        icon && <span className="shrink-0 flex items-center">{icon}</span>
      )}
      {children && <span>{children}</span>}
      {iconRight && !loading && (
        <span className="shrink-0 flex items-center">{iconRight}</span>
      )}
    </button>
  )
}
