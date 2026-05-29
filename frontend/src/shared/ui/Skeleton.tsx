import { cn } from '@/shared/lib/cn'

interface SkeletonProps {
  className?: string
}

export function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      className={cn(
        'bg-gradient-to-r from-[var(--color-surface)] via-[var(--color-surface-2)] to-[var(--color-surface)] bg-[length:200%_100%] animate-pulse rounded',
        className,
      )}
    />
  )
}

export function MessageSkeleton() {
  return (
    <div className="flex gap-3 animate-fade-in">
      <Skeleton className="w-7 h-7 rounded-full shrink-0" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
      </div>
    </div>
  )
}

export function GraphSkeleton() {
  return (
    <div className="border-t border-[var(--color-border)] bg-[var(--color-surface)] rounded-t-2xl p-4">
      <div className="flex items-start gap-3 mb-3">
        <Skeleton className="w-8 h-8 rounded-lg flex-shrink-0" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-3 w-1/3" />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {[...Array(4)].map((_, i) => (
          <Skeleton key={i} className="h-6" />
        ))}
      </div>
    </div>
  )
}
