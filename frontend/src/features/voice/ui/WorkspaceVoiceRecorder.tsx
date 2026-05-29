import { useCallback, useEffect, useRef, useState } from 'react'
import { Mic, Square, Loader2 } from 'lucide-react'

interface Props {
  uploading?: boolean
  onCapture: (blob: Blob, fileName: string) => void
}

export function WorkspaceVoiceRecorder({ uploading, onCapture }: Props) {
  const [recording, setRecording] = useState(false)
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
      const mimeType = pickMimeType()
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
      recorderRef.current = recorder
      chunksRef.current = []

      recorder.ondataavailable = e => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType })
        stream.getTracks().forEach(t => t.stop())
        onCapture(blob, `voice-${Date.now()}.${extensionFor(recorder.mimeType)}`)
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
  }, [onCapture, supported])

  const stop = useCallback(() => {
    if (!recorderRef.current) return
    recorderRef.current.stop()
    if (tickRef.current) {
      window.clearInterval(tickRef.current)
      tickRef.current = null
    }
    setRecording(false)
  }, [])

  if (!supported) return null

  return (
    <button
      type="button"
      onClick={recording ? stop : start}
      disabled={uploading}
      className={'le-voice-rec' + (recording ? ' le-voice-rec--recording' : '')}
      title={recording ? 'Остановить запись' : 'Записать голосовое'}
    >
      {uploading ? (
        <>
          <Loader2 className="le-spin" />
          загрузка
        </>
      ) : recording ? (
        <>
          <Square fill="currentColor" strokeWidth={0} />
          <span className="le-voice-rec__elapsed">{formatElapsed(elapsed)}</span>
        </>
      ) : (
        <>
          <Mic />
          запись
        </>
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
