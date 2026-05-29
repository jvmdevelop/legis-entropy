import { renderToStaticMarkup } from 'react-dom/server'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface ExportOptions {
  content: string
  question?: string
  conversationTitle?: string
  redactedCount?: number
}

export function exportAnswerToPdf({
  content,
  question,
  conversationTitle,
  redactedCount,
}: ExportOptions): void {
  const body = renderToStaticMarkup(
    <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>,
  )

  const now = new Date()
  const dateStr = now.toLocaleString('ru-RU', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  const html = `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>Юридическое обоснование — ${dateStr}</title>
<style>
  @page { size: A4; margin: 18mm 16mm 22mm 16mm; }
  * { box-sizing: border-box; }
  body { font-family: 'IBM Plex Serif', Georgia, 'Times New Roman', serif; color: #1a1a1a; font-size: 11pt; line-height: 1.55; margin: 0; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .header { border-bottom: 1px solid #d4d4d4; padding-bottom: 8mm; margin-bottom: 7mm; }
  .brand-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 5mm; }
  .brand { font-family: 'IBM Plex Mono', ui-monospace, monospace; letter-spacing: 0.14em; font-size: 8.5pt; color: #6d28d9; text-transform: uppercase; font-weight: 600; }
  .brand-tag { font-family: 'IBM Plex Mono', monospace; font-size: 7.5pt; color: #999; letter-spacing: 0.08em; }
  .title { font-family: 'IBM Plex Serif', serif; font-size: 18pt; font-weight: 500; margin: 0 0 3mm 0; letter-spacing: -0.01em; color: #1a1a1a; }
  .meta { font-family: 'IBM Plex Mono', monospace; font-size: 8.5pt; color: #6b6b6b; letter-spacing: 0.04em; }
  .question { margin: 0 0 7mm 0; padding: 5mm 6mm; background: #f7f4ff; border-left: 3px solid #8b5cf6; border-radius: 3px; font-family: 'IBM Plex Sans', sans-serif; font-size: 10.5pt; color: #2d2d2d; line-height: 1.5; }
  .question-label { font-family: 'IBM Plex Mono', monospace; font-size: 7.5pt; letter-spacing: 0.12em; color: #6d28d9; text-transform: uppercase; margin-bottom: 2mm; display: block; font-weight: 600; }
  .body { font-family: 'IBM Plex Sans', sans-serif; font-size: 10.5pt; color: #1a1a1a; }
  .body h1 { font-family: 'IBM Plex Serif', serif; font-size: 15pt; margin: 6mm 0 3mm 0; font-weight: 600; letter-spacing: -0.01em; }
  .body h2 { font-family: 'IBM Plex Serif', serif; font-size: 13pt; margin: 5mm 0 2.5mm 0; font-weight: 600; }
  .body h3 { font-family: 'IBM Plex Sans', sans-serif; font-size: 11pt; margin: 4mm 0 2mm 0; font-weight: 600; }
  .body p { margin: 2.5mm 0; }
  .body ul, .body ol { padding-left: 7mm; margin: 2.5mm 0; }
  .body li { margin: 1mm 0; }
  .body a { color: #6d28d9; text-decoration: underline; word-break: break-word; }
  .body strong { font-weight: 600; }
  .body em { font-style: italic; color: #4c1d95; }
  .body blockquote { margin: 3mm 0; padding: 2mm 4mm; border-left: 2px solid #c4b5fd; color: #555; font-style: italic; background: #faf8ff; }
  .body code { font-family: 'IBM Plex Mono', monospace; background: #f3f3f6; padding: 0.5mm 1.5mm; border-radius: 2px; font-size: 9.5pt; }
  .body pre { background: #f3f3f6; padding: 3mm 4mm; border-radius: 3px; overflow-x: auto; font-size: 9pt; line-height: 1.4; }
  .body table { border-collapse: collapse; width: 100%; margin: 3mm 0; font-size: 9.5pt; page-break-inside: avoid; }
  .body th, .body td { border: 1px solid #d4d4d4; padding: 2mm 3mm; text-align: left; vertical-align: top; }
  .body th { background: #f7f4ff; font-weight: 600; }
  .body hr { border: none; border-top: 1px solid #e0e0e0; margin: 5mm 0; }
  .redaction-notice { margin-top: 5mm; padding: 3mm 4mm; background: #fef3c7; border-left: 3px solid #f59e0b; border-radius: 3px; font-family: 'IBM Plex Sans', sans-serif; font-size: 9.5pt; color: #78350f; }
  .footer { margin-top: 10mm; padding-top: 4mm; border-top: 1px solid #d4d4d4; font-family: 'IBM Plex Mono', monospace; font-size: 7.5pt; color: #888; line-height: 1.55; letter-spacing: 0.02em; }
  .footer .disclaimer { font-style: italic; margin-top: 2mm; }
  @media print {
    body { font-size: 10.5pt; }
  }
</style>
</head>
<body>
  <div class="header">
    <div class="brand-row">
      <div class="brand">Legis Entropy · юридический анализ</div>
      <div class="brand-tag">v1 · adilet.zan.kz</div>
    </div>
    <h1 class="title">${escapeHtml(conversationTitle || 'Юридическое обоснование')}</h1>
    <div class="meta">Сформировано: ${dateStr} · Источник данных: adilet.zan.kz</div>
  </div>
  ${question ? `<div class="question"><span class="question-label">Вопрос</span>${escapeHtml(question)}</div>` : ''}
  <div class="body">${body}</div>
  ${redactedCount && redactedCount > 0 ? `<div class="redaction-notice"><strong>Антигаллюцинатор:</strong> в ответе обнаружено и удалено ${redactedCount} непод­твер­ждённое упоминание статьи. Это упоминание не нашло подтверждения в реестре adilet.zan.kz и было автоматически вычеркнуто.</div>` : ''}
  <div class="footer">
    <div>Ответ сформирован системой Legis Entropy на основе данных открытого реестра adilet.zan.kz. Каждая ссылка на норму проверена антигаллюцинатором: упоминания статей без подтверждения в графе удалены автоматически.</div>
    <div class="disclaimer">Документ не является юридической консультацией. Перед принятием решения сверьте редакцию нормы с первоисточником.</div>
  </div>
  <script>
    window.addEventListener('load', function () {
      setTimeout(function () { window.print(); }, 250);
    });
  </script>
</body>
</html>`

  const win = window.open('', '_blank', 'width=900,height=1100')
  if (!win) {
    alert('Разрешите всплывающие окна, чтобы скачать PDF')
    return
  }
  win.document.open()
  win.document.write(html)
  win.document.close()
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => {
    switch (c) {
      case '&': return '&amp;'
      case '<': return '&lt;'
      case '>': return '&gt;'
      case '"': return '&quot;'
      case "'": return '&#39;'
      default: return c
    }
  })
}
