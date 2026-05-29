import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Check, UserPlus } from 'lucide-react'
import { useAuthStore } from '@/features/auth/model/useAuthStore'

export default function RegisterPageMobile() {
  const navigate = useNavigate()
  const { register, isLoading, error, clearError, isAuthenticated } = useAuthStore()

  const [form, setForm] = useState({
    username: '', email: '', password: '', confirmPassword: '',
    firstName: '', lastName: '',
  })
  const [showPw, setShowPw] = useState(false)
  const [fieldErr, setFieldErr] = useState<Record<string, string>>({})
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (isAuthenticated) navigate('/workspaces', { replace: true })
  }, [isAuthenticated, navigate])

  useEffect(() => { clearError() }, [clearError])

  function set(field: keyof typeof form) {
    return (e: React.ChangeEvent<HTMLInputElement>) => {
      setForm(f => ({ ...f, [field]: e.target.value }))
      clearError()
    }
  }

  function validate() {
    const errs: Record<string, string> = {}
    if (!form.username.trim()) errs.username = 'Обязательное поле'
    else if (form.username.length < 3) errs.username = 'Минимум 3 символа'
    if (!form.email.trim()) errs.email = 'Обязательное поле'
    else if (!/\S+@\S+\.\S+/.test(form.email)) errs.email = 'Некорректный email'
    if (!form.password) errs.password = 'Обязательное поле'
    else if (form.password.length < 6) errs.password = 'Минимум 6 символов'
    if (form.password !== form.confirmPassword) errs.confirmPassword = 'Пароли не совпадают'
    setFieldErr(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return
    try {
      await register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim() || undefined,
        lastName: form.lastName.trim() || undefined,
      })
      setSuccess(true)
    } catch {  }
  }

  if (success) {
    return (
      <div style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, var(--color-bg) 0%, var(--color-surface) 100%)',
        padding: '24px'
      }}>
        <div style={{
          width: '100%',
          maxWidth: '360px',
          textAlign: 'center',
          background: 'var(--color-bg)',
          borderRadius: '12px',
          border: '1px solid var(--color-border)',
          padding: '32px 24px',
          boxShadow: '0 8px 24px rgba(30,27,75,0.08)'
        }} className="animate-fade-in">
          <div style={{
            width: '72px',
            height: '72px',
            borderRadius: '50%',
            background: 'rgba(139,92,246,0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 24px',
            border: '2px solid var(--color-accent)'
          }}>
            <Check size={36} style={{ color: 'var(--color-accent)' }} />
          </div>
          <h2 style={{
            fontFamily: "'IBM Plex Serif', serif",
            fontSize: '24px',
            fontWeight: 500,
            color: 'var(--color-text)',
            margin: '0 0 8px'
          }}>
            Аккаунт создан!
          </h2>
          <p style={{
            fontFamily: "'IBM Plex Sans', sans-serif",
            fontSize: '14px',
            color: 'var(--color-text-muted)',
            marginBottom: '28px'
          }}>
            Ваш аккаунт готов. Войдите, чтобы начать.
          </p>
          <button
            onClick={() => navigate('/login')}
            style={{
              width: '100%',
              height: '48px',
              borderRadius: '8px',
              background: 'var(--color-accent)',
              color: 'white',
              border: 'none',
              fontFamily: "'IBM Plex Sans', sans-serif",
              fontSize: '16px',
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              boxShadow: '0 4px 12px rgba(139,92,246,0.3)'
            }}
            onMouseEnter={e => {
              e.currentTarget.style.background = 'var(--color-accent-hover)'
              e.currentTarget.style.boxShadow = '0 6px 16px rgba(139,92,246,0.4)'
            }}
            onMouseLeave={e => {
              e.currentTarget.style.background = 'var(--color-accent)'
              e.currentTarget.style.boxShadow = '0 4px 12px rgba(139,92,246,0.3)'
            }}
          >
            Войти
          </button>
        </div>
      </div>
    )
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
        marginBottom: '28px',
        marginTop: '16px'
      }}>
        <img src="/logo.png" alt="LE"
          style={{ width: '60px', height: '60px', objectFit: 'contain', marginBottom: '14px' }} />
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
          <div className="label-mono" style={{ marginBottom: '6px' }}>Запросить доступ</div>
          <h1 style={{
            fontFamily: "'IBM Plex Serif', serif",
            fontSize: '24px',
            fontWeight: 500,
            color: 'var(--color-text)',
            margin: 0,
            letterSpacing: '-0.01em'
          }}>
            Создать аккаунт
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

        <form onSubmit={handleSubmit} noValidate style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>

          <AuthField label="Имя">
            <input
              type="text"
              autoComplete="given-name"
              value={form.firstName}
              onChange={set('firstName')}
              placeholder="Мира"
              style={{ fontSize: '16px' }}
            />
          </AuthField>

          <AuthField label="Фамилия">
            <input
              type="text"
              autoComplete="family-name"
              value={form.lastName}
              onChange={set('lastName')}
              placeholder="Аалто"
              style={{ fontSize: '16px' }}
            />
          </AuthField>

          <AuthField label="Имя пользователя" error={fieldErr.username}>
            <input
              type="text"
              autoComplete="username"
              autoFocus
              value={form.username}
              onChange={set('username')}
              placeholder="m.aalto"
              style={{
                borderColor: fieldErr.username ? 'var(--color-error)' : undefined,
                fontSize: '16px'
              }}
            />
          </AuthField>

          <AuthField label="Email" error={fieldErr.email}>
            <input
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={set('email')}
              placeholder="counsel@firm.com"
              style={{
                borderColor: fieldErr.email ? 'var(--color-error)' : undefined,
                fontSize: '16px'
              }}
            />
          </AuthField>

          <AuthField label="Пароль" error={fieldErr.password}>
            <div style={{ position: 'relative' }}>
              <input
                type={showPw ? 'text' : 'password'}
                autoComplete="new-password"
                value={form.password}
                onChange={set('password')}
                placeholder="12+ символов"
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

          <AuthField label="Подтвердите пароль" error={fieldErr.confirmPassword}>
            <input
              type={showPw ? 'text' : 'password'}
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={set('confirmPassword')}
              placeholder="Повторите пароль"
              style={{
                borderColor: fieldErr.confirmPassword ? 'var(--color-error)' : undefined,
                fontSize: '16px'
              }}
            />
          </AuthField>

          <label style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: '10px',
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: '11px',
            color: 'var(--color-text-muted)',
            lineHeight: 1.6,
            letterSpacing: '0.02em',
            marginTop: '4px',
            cursor: 'pointer'
          }}>
            <input
              type="checkbox"
              required
              style={{
                accentColor: 'var(--color-accent)',
                marginTop: '4px',
                flexShrink: 0,
                width: '18px',
                height: '18px',
                cursor: 'pointer'
              }}
            />
            <span>Я принимаю условия и подтверждаю, что не буду загружать конфиденциальные материалы без согласия клиента.</span>
          </label>

          <button
            type="submit"
            disabled={isLoading}
            style={{
              marginTop: '12px',
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
              boxShadow: '0 4px 12px rgba(139,92,246,0.3)'
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
                <UserPlus size={18} />
                <span>Создать</span>
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
          Уже есть аккаунт?{' '}
          <Link to="/login"
            style={{
              color: 'var(--color-accent)',
              textDecoration: 'none',
              fontWeight: 600,
              borderBottom: '1px solid var(--color-accent)'
            }}
            onMouseEnter={e => (e.currentTarget.style.opacity = '0.8')}
            onMouseLeave={e => (e.currentTarget.style.opacity = '1')}>
            Войти
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
