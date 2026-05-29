import { Archive } from 'lucide-react'

interface Props {

  graphId?: string
  situationId?: string
  label?: string
}

export function EvidencePackButton({ graphId, situationId, label }: Props) {
  const url = situationId
    ? `/api/evidence-pack/by-situation/${situationId}`
    : graphId
      ? `/api/evidence-pack/by-graph/${graphId}`
      : null

  if (!url) return null

  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className="self-start inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-[var(--color-surface)] text-[var(--color-text)] border border-[var(--color-border)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent)] transition-colors cursor-pointer"
      title="Собрать ZIP с расшифровками, аудио и документами"
    >
      <Archive size={14} />
      {label ?? 'Evidence Pack (ZIP)'}
    </a>
  )
}
