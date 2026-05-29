import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, LogIn } from 'lucide-react'
import { useAuthStore } from '@/features/auth/model/useAuthStore'

export default function LoginPageMobile() {
  const navigate = useNavigate()
  const { login, isLoading, error, clearError, isAuthenticated } = useAuthStore()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [fieldErr, setFieldErr] = useState<Record<string, string>>({})

  useEffect(() => {
    if (isAuthenticated) navigate('/workspaces', { replace: true })
  }, [isAuthenticated, navigate])

  useEffect(() => {
    clearError()
  }, [clearError])

  function validate() {
    const errs: Record<string, string> = {}
    if (!username.trim()) errs.username = 'Обязательное поле'
    if (!password) errs.password = 'Обязательное поле'
    setFieldErr(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return
    try {
      await login({ username: username.trim(), password })
      navigate('/workspaces', { replace: true })
    } catch {  }
  }

  return (
    <div style={{
      minHeight: '100dvh',
      background: 'linear-gradient(135deg, var(--color-bg) 0%, var(--color-surface) 100%)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px',
      overflow: 'auto'
    }}>

      <div style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        height: '3px',
        background: 'linear-gradient(90deg, transparent, var(--color-accent), transparent)',
        zIndex: 1
      }} />

      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        marginBottom: '32px',
        marginTop: '16px'
      }}>
        <img src="/logo.png" alt="LE"
          style={{ width: '60px', height: '60px', objectFit: 'contain', marginBottom: '16px' }} />
        <div style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: '12px',
          letterSpacing: '0.15em',
          textTransform: 'uppercase',
          color: 'var(--color-text-muted)',
          fontWeight: 500
        }}>Legis Entropy</div>
      </div>

      <div style={{
        width: '100%',
        maxWidth: '360px',
        background: 'var(--color-bg)',
        borderRadius: '12px',
        border: '1px solid var(--color-border)',
        padding: '24px',
        boxShadow: '0 8px 24px rgba(30,27,75,0.08)'
      }}>
        <div style={{ marginBottom: '20px' }}>
          <div className="label-mono" style={{ marginBottom: '6px' }}>Вход</div>
          <h1 style={{
            fontFamily: "'IBM Plex Serif', serif",
            fontSize: '24px',
            fontWeight: 500,
            color: 'var(--color-text)',
            margin: 0,
            letterSpacing: '-0.01em'
          }}>
            Продолжить сессию
          </h1>
        </div>

        {error && (
          <div style={{
            background: 'rgba(239,68,68,0.06)',
            border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: '6px',
            padding: '12px 14px',
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: '12px',
            color: 'var(--color-error)',
            letterSpacing: '0.02em',
            display: 'flex',
            gap: '10px',
            alignItems: 'flex-start',
            marginBottom: '20px'
          }}>
            <span style={{ flexShrink: 0 }}>!</span>
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <AuthField label="Имя пользователя или email" error={fieldErr.username}>
            <input
              type="text"
              autoComplete="username"
              autoFocus
              value={username}
              onChange={e => {
                setUsername(e.target.value)
                clearError()
              }}
              placeholder="m.aalto@firm.com"
              style={{
                borderColor: fieldErr.username ? 'var(--color-error)' : undefined,
                fontSize: '16px'
              }}
            />
          </AuthField>

          <AuthField label="Пароль" error={fieldErr.password}>
            <div style={{ position: 'relative' }}>
              <input
                type={showPw ? 'text' : 'password'}
                autoComplete="current-password"
                value={password}
                onChange={e => {
                  setPassword(e.target.value)
                  clearError()
                }}
                placeholder="Введите пароль"
                style={{
                  borderColor: fieldErr.password ? 'var(--color-error)' : undefined,
                  paddingRight: '44px',
                  fontSize: '16px'
                }}
              />
              <button
                type="button"
                tabIndex={-1}
                onClick={() => setShowPw(v => !v)}
                style={{
                  position: 'absolute',
                  right: '14px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  color: 'var(--color-text-light)',
                  cursor: 'pointer',
                  display: 'flex',
                  padding: '8px',
                  minWidth: '44px',
                  minHeight: '44px',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
                onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-accent)')}
                onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-light)')}
              >
                {showPw ? <EyeOff size={20} /> : <Eye size={20} />}
              </button>
            </div>
          </AuthField>

          <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: '4px' }}>
            <span style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: '11px',
              color: 'var(--color-accent)',
              cursor: 'pointer',
              letterSpacing: '0.04em'
            }}>
              забыли?
            </span>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            style={{
              height: '48px',
              padding: '0 24px',
              background: isLoading ? 'var(--color-accent-hover)' : 'var(--color-accent)',
              color: 'white',
              border: 'none',
              borderRadius: '8px',
              fontFamily: "'IBM Plex Sans', sans-serif",
              fontSize: '16px',
              fontWeight: 600,
              letterSpacing: '0.01em',
              cursor: isLoading ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
              opacity: isLoading ? 0.7 : 1,
              transition: 'all 0.2s ease',
              boxShadow: '0 4px 12px rgba(139,92,246,0.3)',
              marginTop: '8px'
            }}
            onMouseEnter={e => {
              if (!isLoading) {
                e.currentTarget.style.background = 'var(--color-accent-hover)'
                e.currentTarget.style.boxShadow = '0 6px 16px rgba(139,92,246,0.4)'
              }
            }}
            onMouseLeave={e => {
              e.currentTarget.style.background = 'var(--color-accent)'
              e.currentTarget.style.boxShadow = '0 4px 12px rgba(139,92,246,0.3)'
            }}
          >
            {isLoading
              ? <span style={{
                width: '20px',
                height: '20px',
                border: '2px solid rgba(255,255,255,0.3)',
                borderTopColor: 'white',
                borderRadius: '50%',
                display: 'inline-block',
                animation: 'spin 0.7s linear infinite'
              }} />
              : <>
                <LogIn size={18} />
                <span>Войти</span>
              </>
            }
          </button>
        </form>

        <div style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: '12px',
          color: 'var(--color-text-muted)',
          textAlign: 'center',
          marginTop: '20px',
          letterSpacing: '0.02em'
        }}>
          Нет аккаунта?{' '}
          <Link to="/register"
            style={{
              color: 'var(--color-accent)',
              textDecoration: 'none',
              fontWeight: 600,
              borderBottom: '1px solid var(--color-accent)'
            }}
            onMouseEnter={e => (e.currentTarget.style.opacity = '0.8')}
            onMouseLeave={e => (e.currentTarget.style.opacity = '1')}>
            Запросить доступ
          </Link>
        </div>
      </div>

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  )
}

function AuthField({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="auth-field">
      <label>{label}</label>
      {children}
      {error && <span style={{
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: '11px',
        color: 'var(--color-error)',
        letterSpacing: '0.04em'
      }}>{error}</span>}
    </div>
  )
}
