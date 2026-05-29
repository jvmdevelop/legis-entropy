import { useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/features/auth/model/useAuthStore'

function IcHome() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 9.5L10 3l7 6.5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z" />
      <path d="M7.5 18v-5.5h5V18" />
    </svg>
  )
}

function IcLaws() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 2.5v15M3.5 17.5h13" />
      <path d="M5.5 7L3 12h5zM14.5 7L12 12h5z" />
    </svg>
  )
}

function IcTemplates() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="14" height="14" rx="2" />
      <path d="M3 8.5h14M8.5 8.5V17" />
    </svg>
  )
}

function IcScan() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 7V4a1 1 0 0 1 1-1h3M13 3h3a1 1 0 0 1 1 1v3M17 13v3a1 1 0 0 1-1 1h-3M7 17H4a1 1 0 0 1-1-1v-3" />
      <circle cx="10" cy="10" r="3" />
    </svg>
  )
}

function IcUser() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="10" cy="6.5" r="3.5" />
      <path d="M2 17.5c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5" />
    </svg>
  )
}

const NAV_ITEMS = [
  {
    icon: IcHome,
    label: 'рабочие',
    href: '/workspaces',
    match: (p: string) => p === '/workspaces',
  },
  {
    icon: IcLaws,
    label: 'законы',
    href: '/laws',
    match: (p: string) => p.startsWith('/laws') || p.startsWith('/articles'),
  },
  {
    icon: IcTemplates,
    label: 'шаблоны',
    href: '/templates',
    match: (p: string) => p.startsWith('/templates'),
  },
  {
    icon: IcScan,
    label: 'скан',
    href: '/scan',
    match: (p: string) => p.startsWith('/scan'),
  },
]

export function BottomNav() {
  const location = useLocation()
  const navigate = useNavigate()
  const { logout, user } = useAuthStore()

  return (
    <nav className="le-bottom-nav">
      {NAV_ITEMS.map(({ icon: Icon, label, href, match }) => (
        <button
          key={href}
          className={'le-bottom-nav__btn' + (match(location.pathname) ? ' le-bottom-nav__btn--on' : '')}
          onClick={() => navigate(href)}
        >
          <Icon />
          <span>{label}</span>
        </button>
      ))}

      <button
        className="le-bottom-nav__btn"
        onClick={logout}
        title={user ? `выйти (${user.username})` : 'выйти'}
      >
        <IcUser />
        <span className="le-bottom-nav__username">
          {user?.username ?? 'я'}
        </span>
      </button>
    </nav>
  )
}
