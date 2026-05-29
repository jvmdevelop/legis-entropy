import { useIsMobile } from '@/shared/hooks/useMediaQuery'
import { useLawBrowser } from './useLawBrowser'
import LawBrowserPageDesktop from './LawBrowserPage.desktop'
import LawBrowserPageMobile from './LawBrowserPage.mobile'

export default function LawBrowserPage() {
  const state = useLawBrowser()
  const isMobile = useIsMobile()
  return isMobile
    ? <LawBrowserPageMobile state={state} />
    : <LawBrowserPageDesktop state={state} />
}
