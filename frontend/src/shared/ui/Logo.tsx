import { cn } from '@/shared/lib/cn'

interface LogoProps {
  size?: 'sm' | 'md' | 'lg' | 'xl'
  showText?: boolean
  className?: string
  textClassName?: string
}

const iconSizes = { sm: 28, md: 36, lg: 48, xl: 64 }
const textSizes = { sm: 'text-base', md: 'text-lg', lg: 'text-2xl', xl: 'text-3xl' }

export function Logo({ size = 'md', showText = true, className, textClassName }: LogoProps) {
  const px = iconSizes[size]

  return (
    <div className={cn('flex items-center gap-3', className)}>

      <img
        src="/logo.png"
        alt="Legis Entropy logo"
        width={px}
        height={px}
        style={{ objectFit: 'contain' }}
      />

      {showText && (
        <div className={cn('flex flex-col leading-none', textClassName)}>
          <span
            className={cn(
              'font-semibold tracking-tight text-[var(--color-text)]',
              textSizes[size],
            )}
            style={{ fontFamily: "'IBM Plex Mono', monospace", letterSpacing: '0.12em', textTransform: 'uppercase' }}
          >
            Legis Entropy
          </span>
        </div>
      )}
    </div>
  )
}
