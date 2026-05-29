import { useIsMobile } from '@/shared/hooks/useMediaQuery'
import { useWorkspaceShell } from './useWorkspaceShell'
import WorkspaceShellDesktop from './WorkspaceShell.desktop'
import WorkspaceShellMobile from './WorkspaceShell.mobile'

export default function WorkspaceShell() {
  const state = useWorkspaceShell()
  const isMobile = useIsMobile()
  return isMobile
    ? <WorkspaceShellMobile state={state} />
    : <WorkspaceShellDesktop state={state} />
}
