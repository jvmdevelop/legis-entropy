import { useCallback, useEffect, useRef, useState } from 'react'
import { Mic, Square, Loader2 } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import type { VoiceMessageDTO } from '@/entities/voice'
import { voiceApi } from '@/features/voice/api/voiceApi'

interface Props {
  graphId?: string
  situationId?: string
  conversationId?: string
  onUploaded?: (voice: VoiceMessageDTO) => void
  language?: string
}

export function VoiceRecorderButton({
  graphId, situationId, conversationId, onUploaded, language = 'ru',
}: Props) {
  const [recording, setRecording] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const startedAt = useRef<number>(0)
  const tickRef = useRef<number | null>(null)

  const supported = typeof window !== 'undefined' && 'MediaRecorder' in window

  useEffect(() => () => {
    if (tickRef.current) window.clearInterval(tickRef.current)
    recorderRef.current?.stream.getTracks().forEach(t => t.stop())
  }, [])

  const start = useCallback(async () => {
    if (!supported) return
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(stream, { mimeType: pickMimeType() })
      recorderRef.current = recorder
      chunksRef.current = []

      recorder.ondataavailable = e => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }
      recorder.onstop = async () => {
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType })
        stream.getTracks().forEach(t => t.stop())
        setUploading(true)
        try {
          const dto = await voiceApi.upload({
            file: blob,
            fileName: `voice-${Date.now()}.${extensionFor(recorder.mimeType)}`,
            graphId,
            situationId,
            conversationId,
            language,
          })
          onUploaded?.(dto)
        } catch (err) {
          console.error('voice upload failed', err)
        } finally {
          setUploading(false)
        }
      }

      recorder.start(250)
      startedAt.current = Date.now()
      setElapsed(0)
      tickRef.current = window.setInterval(() => {
        setElapsed(Math.floor((Date.now() - startedAt.current) / 1000))
      }, 250)
      setRecording(true)
    } catch (err) {
      console.warn('Microphone access denied', err)
    }
  }, [conversationId, graphId, language, onUploaded, situationId, supported])

  const stop = useCallback(() => {
    if (!recorderRef.current) return
    recorderRef.current.stop()
    if (tickRef.current) {
      window.clearInterval(tickRef.current)
      tickRef.current = null
    }
    setRecording(false)
  }, [])

  if (!supported) {
    return null
  }

  return (
    <button
      type="button"
      onClick={recording ? stop : start}
      disabled={uploading}
      className={cn(
        'inline-flex items-center justify-center h-10 rounded-2xl transition-colors cursor-pointer',
        recording
          ? 'px-3 gap-2 bg-red-50 text-[var(--color-error)] border border-red-100'
          : 'w-10 text-[var(--color-text-muted)] hover:text-[var(--color-accent)] hover:bg-[var(--color-accent-glow)]',
      )}
      title={recording ? 'Остановить запись' : 'Записать голосовое сообщение'}
    >
      {uploading ? (
        <Loader2 size={16} className="animate-spin text-[var(--color-accent)]" />
      ) : recording ? (
        <>
          <Square size={14} fill="currentColor" />
          <span className="font-mono text-xs tabular-nums">{formatElapsed(elapsed)}</span>
        </>
      ) : (
        <Mic size={16} />
      )}
    </button>
  )
}

function pickMimeType(): string {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/mpeg']
  for (const c of candidates) {
    if (typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(c)) return c
  }
  return ''
}

function extensionFor(mime: string): string {
  if (!mime) return 'webm'
  if (mime.includes('webm')) return 'webm'
  if (mime.includes('mp4')) return 'm4a'
  if (mime.includes('mpeg')) return 'mp3'
  return 'webm'
}

function formatElapsed(seconds: number): string {
  const m = Math.floor(seconds / 60).toString().padStart(2, '0')
  const s = (seconds % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}
