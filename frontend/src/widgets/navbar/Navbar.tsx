import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { useBreadcrumbStore } from '@/shared/lib/useBreadcrumbStore'
import { useAuthStore } from '@/features/auth/model/useAuthStore'
import { useIsMobile } from '@/shared/hooks/useMediaQuery'

function IconBack() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6">
      <path d="M10 3L5 8l5 5" />
    </svg>
  )
}

export function Navbar() {
  const navigate = useNavigate()
  const location = useLocation()
  const params = useParams()
  const { workspaceName, graphName } = useBreadcrumbStore()
  const { logout, user } = useAuthStore()
  const isMobile = useIsMobile()

  const isLaws = location.pathname.startsWith('/laws')
  const isSearch = location.pathname.startsWith('/search')
  const isScan = location.pathname.startsWith('/scan')
  const isWatcher = location.pathname.startsWith('/watcher')
  const isTemplates = location.pathname.startsWith('/templates')
  const isAdmin = location.pathname.startsWith('/admin')
  const isWorkspaceArea = location.pathname.startsWith('/workspace/')
  const isGraph = isWorkspaceArea && !!params.graphId

  const wsId = isWorkspaceArea ? location.pathname.split('/')[2] : null

  return (
    <header className={'le-header' + (isMobile ? ' le-header--mobile' : '')}>
      <div className="le-header__left">
        <button
          className="le-logo"
          onClick={() => navigate('/workspaces')}
          style={{ background: 'none', padding: 0 }}
          title="к workspaces"
        >
          <span className="le-logo__l">L</span>
          <span className="le-logo__e">E</span>
          {!isMobile && <span className="le-logo__cursor" />}
        </button>

        {!isWorkspaceArea && !isMobile && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 16 }}>
            <button
              className="le-settings"
              onClick={logout}
              title={user ? `выйти (${user.username})` : 'выйти'}
            >
              <span className="le-avatar" />
              <span>{user?.username ?? 'settings'}</span>
            </button>
            {user?.planType && user.planType !== 'PRO' && (
              <span className={'le-plan-badge le-plan-badge--' + user.planType.toLowerCase()}>
                {user.planType === 'FREE' ? 'free' : 'basic'}
              </span>
            )}
          </div>
        )}

        {isWorkspaceArea && (
          <>
            <span className="le-header__div" />
            {isMobile ? (
              <button
                className="le-backlink le-backlink--icon"
                onClick={() => navigate(isGraph ? `/workspace/${wsId}` : '/workspaces')}
                title={isGraph ? 'к workspace' : 'к workspaces'}
              >
                <IconBack />
              </button>
            ) : (
              <button className="le-backlink" onClick={() => navigate('/workspaces')}>
                <IconBack /> workspaces
              </button>
            )}
            {!isMobile && <span className="le-crumb-sep">/</span>}
            {!isGraph ? (
              <span className="le-graphname le-graphname--truncate">{workspaceName ?? '...'}</span>
            ) : isMobile ? (
              <span className="le-graphname le-graphname--truncate">{graphName ?? '...'}</span>
            ) : (
              <>
                <button className="le-crumb-link" onClick={() => navigate(`/workspace/${wsId}`)}>
                  {workspaceName ?? '...'}
                </button>
                <span className="le-crumb-sep">/</span>
                <span className="le-graphname le-graphname--truncate">{graphName ?? '...'}</span>
              </>
            )}
          </>
        )}
      </div>

      {!isWorkspaceArea && !isMobile && (
        <nav className="le-tabs">
          <button
            className={'le-tab' + (!isLaws && !isSearch && !isScan && !isWatcher && !isTemplates && !isAdmin ? ' le-tab--active' : '')}
            onClick={() => navigate('/workspaces')}
          >
            workspaces
          </button>
          <button
            className={'le-tab' + (isLaws ? ' le-tab--active' : '')}
            onClick={() => navigate('/laws')}
          >
            laws
          </button>
          <button
            className={'le-tab' + (isTemplates ? ' le-tab--active' : '')}
            onClick={() => navigate('/templates')}
          >
            templates
          </button>
          <button
            className={'le-tab' + (isScan ? ' le-tab--active' : '')}
            onClick={() => navigate('/scan')}
          >
            scan
          </button>
          {user?.role === 'ROLE_ADMIN' && (
            <button
              className={'le-tab' + (isAdmin ? ' le-tab--active' : '')}
              onClick={() => navigate('/admin')}
            >
              admin
            </button>
          )}
        </nav>
      )}

      {isMobile && (
        <button
          className="le-settings"
          onClick={logout}
          title={user ? `выйти (${user.username})` : 'выйти'}
        >
          <span className="le-avatar" />
        </button>
      )}
    </header>
  )
}
