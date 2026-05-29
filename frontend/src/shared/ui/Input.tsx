import { type InputHTMLAttributes, type ReactNode, forwardRef } from 'react'
import { cn } from '@/shared/lib/cn'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  hint?: string
  icon?: ReactNode
  iconRight?: ReactNode
  fullWidth?: boolean
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, hint, icon, iconRight, fullWidth = true, className, id, ...rest }, ref) => {
    const inputId = id ?? `input-${Math.random().toString(36).slice(2, 7)}`

    return (
      <div className={cn('flex flex-col gap-1.5', fullWidth && 'w-full')}>
        {label && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium text-[var(--color-text)]"
          >
            {label}
          </label>
        )}

        <div className="relative flex items-center">
          {icon && (
            <span className="absolute left-3.5 flex items-center text-[var(--color-text-muted)] pointer-events-none">
              {icon}
            </span>
          )}

          <input
            ref={ref}
            id={inputId}
            className={cn(
              `w-full bg-[var(--color-bg)]
               border border-[var(--color-border)]
               text-[var(--color-text)] placeholder-[var(--color-text-light)]
               rounded-lg
               h-11 px-4 text-sm
               transition-all duration-150
               hover:border-[var(--color-text-light)]
               focus:outline-none focus:border-[var(--color-accent)]
               focus:ring-[3px] focus:ring-[var(--color-accent-glow)]`,
              icon && 'pl-10',
              iconRight && 'pr-10',
              error && 'border-[var(--color-error)] focus:border-[var(--color-error)] focus:ring-red-100',
              className,
            )}
            {...rest}
          />

          {iconRight && (
            <span className="absolute right-3.5 flex items-center text-[var(--color-text-muted)]">
              {iconRight}
            </span>
          )}
        </div>

        {error && (
          <p className="text-xs text-[var(--color-error)] animate-fade-in">{error}</p>
        )}
        {hint && !error && (
          <p className="text-xs text-[var(--color-text-muted)]">{hint}</p>
        )}
      </div>
    )
  },
)

Input.displayName = 'Input'
