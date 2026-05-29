import { useIsMobile } from '@/shared/hooks/useMediaQuery'
import { useWorkspacesPage } from './useWorkspacesPage'
import WorkspacesPageDesktop from './WorkspacesPage.desktop'
import WorkspacesPageMobile from './WorkspacesPage.mobile'

export default function WorkspacesPage() {
  const state = useWorkspacesPage()
  const isMobile = useIsMobile()
  return isMobile
    ? <WorkspacesPageMobile state={state} />
    : <WorkspacesPageDesktop state={state} />
}
