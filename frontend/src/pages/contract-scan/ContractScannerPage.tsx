import { useCallback, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FileScan, Upload, AlertCircle, CheckCircle2, AlertTriangle,
  HelpCircle, XCircle, ExternalLink, ChevronLeft, FileText, Download,
  ShieldAlert, Lock,
} from 'lucide-react'
import { documentApi } from '@/features/document/api/documentApi'
import {
  contractScanApi,
  type CitationCheck,
  type CitationStatus,
  type ContractScanResponse,
  type ClauseRiskResult,
  type RiskScanResponse,
  type RiskLevel,
} from '@/features/contract-scan/api/contractScanApi'
import { useAuthStore } from '@/features/auth/model/useAuthStore'

type Phase = 'idle' | 'uploading' | 'extracting' | 'scanning' | 'ready' | 'error' | 'upgrade'
type Mode = 'citations' | 'risks'

export default function ContractScannerPage() {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { user } = useAuthStore()

  const [mode, setMode] = useState<Mode>('citations')
  const [fileName, setFileName] = useState<string | null>(null)
  const [phase, setPhase] = useState<Phase>('idle')
  const [progressPct, setProgressPct] = useState(0)
  const [report, setReport] = useState<ContractScanResponse | null>(null)
  const [riskReport, setRiskReport] = useState<RiskScanResponse | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<CitationStatus | 'ALL'>('ALL')
  const [riskFilter, setRiskFilter] = useState<RiskLevel | 'ALL'>('ALL')

  const reset = useCallback(() => {
    setFileName(null)
    setPhase('idle')
    setProgressPct(0)
    setReport(null)
    setRiskReport(null)
    setErrorMsg(null)
    setStatusFilter('ALL')
    setRiskFilter('ALL')
  }, [])

  const runScan = useCallback(async (file: File) => {
    setFileName(file.name)
    setPhase('uploading')
    setProgressPct(0)
    setErrorMsg(null)
    setReport(null)
    setRiskReport(null)
    try {
      const documentId = await documentApi.upload(file, (pct) => setProgressPct(pct))
      setPhase('extracting')
      const text = await documentApi.text(documentId)
      if (!text || !text.trim()) {
        setErrorMsg('не удалось извлечь текст из документа')
        setPhase('error')
        return
      }
      setPhase('scanning')
      if (mode === 'citations') {
        const scan = await contractScanApi.scan(text)
        setReport(scan)
        setPhase('ready')
      } else {
        try {
          const scan = await contractScanApi.riskScan(text)
          setRiskReport(scan)
          setPhase('ready')
        } catch (err: unknown) {
          const status = (err as { response?: { status?: number } })?.response?.status
          if (status === 402) {
            setPhase('upgrade')
          } else {
            setErrorMsg('не удалось обработать документ')
            setPhase('error')
          }
        }
        return
      }
    } catch {
      setErrorMsg('не удалось обработать документ')
      setPhase('error')
    }
  }, [mode])

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (file) void runScan(file)
  }

  function handleDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault()
    const file = e.dataTransfer.files?.[0]
    if (file) void runScan(file)
  }

  const filtered = (report?.citations ?? []).filter(
    (c) => statusFilter === 'ALL' || c.status === statusFilter,
  )
  const filteredRisk = (riskReport?.clauses ?? []).filter(
    (c) => riskFilter === 'ALL' || c.riskLevel === riskFilter,
  )

  return (
    <div className="le-cpage">
      <div className="le-cpage__hero">
        <div className="le-cpage__hero-icon">
          {mode === 'risks' ? <ShieldAlert /> : <FileScan />}
        </div>
        <div className="le-cpage__hero-text">
          <button
            className="le-backlink le-backlink--mb"
            onClick={() => navigate(-1)}
          >
            <ChevronLeft /> назад
          </button>
          <div className="le-cpage__eyebrow">contract_scanner</div>
          <div className="le-chips" style={{ marginBottom: 8 }}>
            <button
              className={'le-chip' + (mode === 'citations' ? ' le-chip--on' : '')}
              onClick={() => { if (phase === 'idle' || phase === 'ready' || phase === 'error') { reset(); setMode('citations') } }}
            >
              аудит цитат
            </button>
            <button
              className={'le-chip' + (mode === 'risks' ? ' le-chip--on' : '')}
              onClick={() => { if (phase === 'idle' || phase === 'ready' || phase === 'error') { reset(); setMode('risks') } }}
            >
              анализ рисков
            </button>
          </div>
          {mode === 'citations'
            ? <p className="le-cpage__sub">загрузите договор — найдём все цитаты на статьи и сверим с графом законов</p>
            : <p className="le-cpage__sub">загрузите договор — ИИ проверит каждый пункт на соответствие нормам РК</p>
          }
        </div>

        {phase === 'ready' && report && mode === 'citations' && (
          <div className="le-cpage__hero-actions">
            <div className="le-stats">
              <Stat label="всего" value={report.totalCitations} variant="accent" />
              <Stat label="ок" value={report.totalFound} variant="ok" />
              <Stat label="не найдено" value={report.totalMissing} variant="err" />
              <Stat label="без кода" value={report.totalUnknownCode} variant="warn" />
            </div>
          </div>
        )}
        {phase === 'ready' && riskReport && mode === 'risks' && (
          <div className="le-cpage__hero-actions">
            <div className="le-stats">
              <Stat label="нарушений" value={riskReport.totalViolations} variant="err" />
              <Stat label="предупреждений" value={riskReport.totalWarnings} variant="warn" />
              <Stat label="ок" value={riskReport.totalCompliant} variant="ok" />
            </div>
          </div>
        )}
      </div>

      <div className="le-cpage__body">
        {phase === 'idle' && (
          <DropZone
            onPick={() => fileInputRef.current?.click()}
            onDrop={handleDrop}
          />
        )}

        {(phase === 'uploading' || phase === 'extracting' || phase === 'scanning') && (
          <ProgressStrip phase={phase} progressPct={progressPct} fileName={fileName} />
        )}

        {phase === 'error' && (
          <div className="le-col" style={{ alignItems: 'flex-start' }}>
            <div className="le-notice">
              <AlertCircle />
              <span>{errorMsg ?? 'ошибка'}</span>
            </div>
            <button className="le-btn" onClick={reset}>
              попробовать снова
            </button>
          </div>
        )}

        {phase === 'upgrade' && (
          <div className="le-col" style={{ alignItems: 'center', gap: 16, paddingTop: 24 }}>
            <div style={{ fontSize: 48, color: 'var(--c-accent)' }}><Lock /></div>
            <div style={{ textAlign: 'center' }}>
              <h2 style={{ fontWeight: 700, fontSize: '1.1rem', marginBottom: 8 }}>
                Анализ рисков доступен с тарифом Basic
              </h2>
              <p className="le-dim" style={{ maxWidth: 340 }}>
                Ваш текущий тариф: <strong>{user?.planType ?? 'FREE'}</strong>.
                Обратитесь к администратору для смены тарифа.
              </p>
            </div>
            <button className="le-btn le-btn--ghost" onClick={reset}>
              вернуться
            </button>
          </div>
        )}

        {phase === 'ready' && report && mode === 'citations' && (
          <>
            <div
              className="le-chips"
              style={{ justifyContent: 'space-between', alignItems: 'center' }}
            >
              <div className="le-chips">
                <button
                  className={'le-chip' + (statusFilter === 'ALL' ? ' le-chip--on' : '')}
                  onClick={() => setStatusFilter('ALL')}
                >
                  все · {report.totalCitations}
                </button>
                <button
                  className={'le-chip le-chip--ok' + (statusFilter === 'OK' ? ' le-chip--on' : '')}
                  onClick={() => setStatusFilter('OK')}
                >
                  ок · {report.totalFound}
                </button>
                <button
                  className={'le-chip le-chip--err' + (statusFilter === 'NOT_FOUND' ? ' le-chip--on' : '')}
                  onClick={() => setStatusFilter('NOT_FOUND')}
                >
                  не найдено · {report.totalMissing}
                </button>
                <button
                  className={'le-chip le-chip--warn' + (statusFilter === 'UNKNOWN_CODE' ? ' le-chip--on' : '')}
                  onClick={() => setStatusFilter('UNKNOWN_CODE')}
                >
                  без кода · {report.totalUnknownCode}
                </button>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="le-btn le-btn--primary" onClick={() => exportReport(report, fileName)}>
                  <Download /> скачать pdf
                </button>
                <button className="le-btn le-btn--ghost" onClick={reset}>
                  новый документ
                </button>
              </div>
            </div>

            {filtered.length === 0 ? (
              <div className="le-dim-note">по фильтру ничего нет</div>
            ) : (
              <div className="le-col">
                {filtered.map((c, i) => <CitationRow key={i} citation={c} />)}
              </div>
            )}
          </>
        )}

        {phase === 'ready' && riskReport && mode === 'risks' && (
          <>
            <div
              className="le-chips"
              style={{ justifyContent: 'space-between', alignItems: 'center' }}
            >
              <div className="le-chips">
                <button
                  className={'le-chip' + (riskFilter === 'ALL' ? ' le-chip--on' : '')}
                  onClick={() => setRiskFilter('ALL')}
                >
                  все · {riskReport.clauses.length}
                </button>
                <button
                  className={'le-chip le-chip--err' + (riskFilter === 'VIOLATION' ? ' le-chip--on' : '')}
                  onClick={() => setRiskFilter('VIOLATION')}
                >
                  нарушения · {riskReport.totalViolations}
                </button>
                <button
                  className={'le-chip le-chip--warn' + (riskFilter === 'WARNING' ? ' le-chip--on' : '')}
                  onClick={() => setRiskFilter('WARNING')}
                >
                  предупреждения · {riskReport.totalWarnings}
                </button>
                <button
                  className={'le-chip le-chip--ok' + (riskFilter === 'COMPLIANT' ? ' le-chip--on' : '')}
                  onClick={() => setRiskFilter('COMPLIANT')}
                >
                  ок · {riskReport.totalCompliant}
                </button>
              </div>
              <button className="le-btn le-btn--ghost" onClick={reset}>
                новый документ
              </button>
            </div>

            {riskReport.missingClauses.length > 0 && (
              <div className="le-col" style={{ marginBottom: 8 }}>
                <div className="le-notice" style={{ alignItems: 'flex-start', flexDirection: 'column', gap: 4 }}>
                  <strong>Отсутствующие обязательные пункты ({riskReport.contractType} договор):</strong>
                  {riskReport.missingClauses.map((m, i) => (
                    <span key={i}>• {m.requiredClause} <span className="le-dim">({m.lawBasis})</span></span>
                  ))}
                </div>
              </div>
            )}

            {filteredRisk.length === 0 ? (
              <div className="le-dim-note">по фильтру ничего нет</div>
            ) : (
              <div className="le-col">
                {filteredRisk.map((c, i) => <ClauseRiskRow key={i} clause={c} />)}
              </div>
            )}
          </>
        )}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,.txt,.docx,.doc"
        className="hidden"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
    </div>
  )
}

function DropZone({
  onPick, onDrop,
}: {
  onPick: () => void
  onDrop: (e: React.DragEvent<HTMLDivElement>) => void
}) {
  return (
    <div
      onClick={onPick}
      onDragOver={(e) => e.preventDefault()}
      onDrop={onDrop}
      className="le-dropzone"
    >
      <div className="le-dropzone__icon">
        <Upload />
      </div>
      <div>
        <h2 className="le-dropzone__title">перетащите договор сюда</h2>
        <p className="le-dropzone__sub">PDF, DOCX, TXT · до 10 МБ · обработка ~5–15 секунд</p>
      </div>
      <span className="le-dropzone__hint">или кликните для выбора</span>
    </div>
  )
}

function ProgressStrip({
  phase, progressPct, fileName,
}: {
  phase: 'uploading' | 'extracting' | 'scanning'
  progressPct: number
  fileName: string | null
}) {
  const label =
    phase === 'uploading' ? `загружаем · ${progressPct}%` :
    phase === 'extracting' ? 'извлекаем текст…' :
    'ищем ссылки на нормы…'

  const stepIdx = phase === 'uploading' ? 0 : phase === 'extracting' ? 1 : 2

  return (
    <div className="le-progress">
      <div className="le-progress__spinner" />
      <div className="le-progress__body">
        <div className="le-progress__file">
          <FileText />
          <span>{fileName ?? '...'}</span>
        </div>
        <div className="le-progress__label">{label}</div>
        <div className="le-progress__bars">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className={'le-progress__bar' + (i <= stepIdx ? ' le-progress__bar--on' : '')}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

const STATUS_META: Record<CitationStatus, {
  icon: React.ReactNode
  label: string
  variant: 'ok' | 'warn' | 'err' | 'neutral'
}> = {
  OK: {
    icon: <CheckCircle2 />, label: 'найдена', variant: 'ok',
  },
  RECENTLY_AMENDED: {
    icon: <AlertTriangle />, label: 'изменена недавно', variant: 'warn',
  },
  NOT_FOUND: {
    icon: <XCircle />, label: 'не найдена в графе', variant: 'err',
  },
  UNKNOWN_CODE: {
    icon: <HelpCircle />, label: 'код не распознан', variant: 'neutral',
  },
}

function CitationRow({ citation }: { citation: CitationCheck }) {
  const navigate = useNavigate()
  const meta = STATUS_META[citation.status]
  const canOpen = citation.foundInGraph && citation.inferredCode
  const variantClass =
    meta.variant === 'ok'      ? 'le-rcard--ok' :
    meta.variant === 'err'     ? 'le-rcard--err' :
    meta.variant === 'warn'    ? 'le-rcard--warn' :
    ''

  return (
    <article className={'le-rcard le-rcard--static ' + variantClass}>
      <div className="le-rcard__head">
        <div className="le-rcard__lhs">
          <span className={'le-badge le-badge--' + meta.variant}>
            {meta.icon}
            {meta.label}
          </span>
          <span className="le-rcard__code">
            ст. {citation.number} · {citation.inferredCode ?? 'код?'}
          </span>
          {citation.lastAmendmentDate && (
            <span className="le-rcard__meta" style={{ marginTop: 0 }}>
              изм. {formatDate(citation.lastAmendmentDate)}
            </span>
          )}
        </div>
        {canOpen && (
          <button
            className="le-rcard__open"
            onClick={() => navigate(`/articles/${encodeURIComponent(citation.inferredCode!)}/${encodeURIComponent(citation.number)}`)}
          >
            открыть <ExternalLink />
          </button>
        )}
      </div>

      {citation.articleTitle && (
        <div className="le-rcard__title">{citation.articleTitle}</div>
      )}

      {citation.contextSnippet && (
        <div className="le-snippet">
          <div className="le-snippet__eyebrow">из документа</div>
          <p className="le-snippet__text">…{citation.contextSnippet}…</p>
        </div>
      )}
    </article>
  )
}

function Stat({
  label, value, variant = 'default',
}: {
  label: string
  value: number
  variant?: 'default' | 'accent' | 'ok' | 'err' | 'warn'
}) {
  const valClass = variant === 'default' ? '' : ' le-stat__val--' + variant
  return (
    <div className="le-stat">
      <span className={'le-stat__val' + valClass}>{value}</span>
      <span className="le-stat__label">{label}</span>
    </div>
  )
}

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  if (!y || !m || !d) return iso
  const month = MONTHS[parseInt(m, 10) - 1] ?? m
  return `${parseInt(d, 10)} ${month} ${y}`
}

const RISK_META: Record<RiskLevel, { label: string; variant: 'ok' | 'warn' | 'err' }> = {
  COMPLIANT: { label: 'соответствует', variant: 'ok' },
  WARNING:   { label: 'предупреждение', variant: 'warn' },
  VIOLATION: { label: 'нарушение', variant: 'err' },
}

function ClauseRiskRow({ clause }: { clause: ClauseRiskResult }) {
  const meta = RISK_META[clause.riskLevel] ?? RISK_META.COMPLIANT
  const variantClass = meta.variant === 'err' ? 'le-rcard--err' : meta.variant === 'warn' ? 'le-rcard--warn' : 'le-rcard--ok'

  return (
    <article className={'le-rcard le-rcard--static ' + variantClass}>
      <div className="le-rcard__head">
        <div className="le-rcard__lhs">
          <span className={'le-badge le-badge--' + meta.variant}>
            {meta.variant === 'err' ? <XCircle /> : meta.variant === 'warn' ? <AlertTriangle /> : <CheckCircle2 />}
            {meta.label}
          </span>
          <span className="le-rcard__code">п. {clause.clauseId}</span>
          {clause.lawCitation && (
            <span className="le-rcard__meta" style={{ marginTop: 0 }}>{clause.lawCitation}</span>
          )}
        </div>
      </div>

      {clause.clauseText && (
        <div className="le-snippet">
          <div className="le-snippet__eyebrow">пункт договора</div>
          <p className="le-snippet__text">{clause.clauseText}</p>
        </div>
      )}

      {clause.reason && (
        <div className="le-rcard__title" style={{ marginTop: 4 }}>{clause.reason}</div>
      )}
    </article>
  )
}

async function exportReport(report: ContractScanResponse, fileName: string | null) {
  const lines: string[] = []
  lines.push(`# Аудит договора${fileName ? ` — ${fileName}` : ''}`)
  lines.push('')
  lines.push(`**Всего ссылок:** ${report.totalCitations}  `)
  lines.push(`**Найдено в графе:** ${report.totalFound}  `)
  lines.push(`**Не найдено:** ${report.totalMissing}  `)
  lines.push(`**Без распознанного кода:** ${report.totalUnknownCode}`)
  lines.push('')
  lines.push('## Перечень ссылок')
  lines.push('')
  for (const c of report.citations) {
    const code = c.inferredCode ?? 'код?'
    const statusLabel = STATUS_META[c.status]?.label ?? c.status
    lines.push(`### ст. ${c.number} · ${code} — ${statusLabel}`)
    if (c.articleTitle) lines.push(`**${c.articleTitle}**`)
    if (c.lastAmendmentDate) lines.push(`_Последняя поправка: ${formatDate(c.lastAmendmentDate)}_`)
    if (c.contextSnippet) lines.push(`> …${c.contextSnippet}…`)
    if (c.sourceUri) lines.push(`[Открыть на adilet](${c.sourceUri}${c.anchor ? '#' + c.anchor : ''})`)
    lines.push('')
  }
  const { exportAnswerToPdf } = await import('@/shared/lib/exportAnswerToPdf')
  exportAnswerToPdf({
    content: lines.join('\n'),
    conversationTitle: `Аудит договора${fileName ? ` · ${fileName}` : ''}`,
  })
}
