import { Toaster } from 'sonner'

export function ToastProvider() {
  return (
    <Toaster
      position="bottom-right"
      theme="light"
      richColors
      expand
      closeButton
      visibleToasts={5}
      style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        color: 'var(--color-text)',
      }}
    />
  )
}

export { toast } from 'sonner'
