import type { ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { Navbar } from "@/widgets/navbar/Navbar";
import { BottomNav } from "@/widgets/bottom-nav/BottomNav";
import { useIsMobile } from "@/shared/hooks/useMediaQuery";

interface MainLayoutProps {
  children: ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  const isMobile = useIsMobile();
  const location = useLocation();

  const isWorkspaceShell = location.pathname.startsWith("/workspace/");
  const showBottomNav = isMobile && !isWorkspaceShell;

  return (
    <div className={"le-root" + (showBottomNav ? " le-root--has-bnav" : "")}>
      <Navbar />
      <div className="le-body">{children}</div>
      {showBottomNav && <BottomNav />}
    </div>
  );
}
