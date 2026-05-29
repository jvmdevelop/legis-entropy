import { useEffect, type ReactNode } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  useLocation,
} from "react-router-dom";
import { QueryProvider } from "@/app/providers/QueryProvider";
import { useAuthStore } from "@/features/auth/model/useAuthStore";
import { FullscreenSpinner } from "@/shared/ui/Spinner";
import { ToastProvider } from "@/shared/ui/Toast";
import { MainLayout } from "@/layouts/MainLayout";
import LoginPage from "@/pages/auth/LoginPage";
import RegisterPage from "@/pages/auth/RegisterPage";
import WorkspacesPage from "@/pages/workspaces/WorkspacesPage";
import WorkspaceShell from "@/pages/workspaces/WorkspaceShell";
import LawBrowserPage from "@/pages/laws/LawBrowserPage";
import ArticlePage from "@/pages/article/ArticlePage";
import ConflictsPage from "@/pages/conflicts/ConflictsPage";
import ContractScannerPage from "@/pages/contract-scan/ContractScannerPage";
import TemplatesPage from "@/pages/templates/TemplatesPage";
import AdminPage from "@/pages/admin/AdminPage";

function AuthBootstrap({ children }: { children: ReactNode }) {
  const { bootstrapFromStorage, isLoading } = useAuthStore();

  useEffect(() => {
    bootstrapFromStorage();
    const handler = () => useAuthStore.getState().logout();
    window.addEventListener("auth:logout", handler);
    return () => window.removeEventListener("auth:logout", handler);
  }, [bootstrapFromStorage]);

  if (isLoading) return <FullscreenSpinner />;
  return <>{children}</>;
}

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuthStore();
  const location = useLocation();

  if (isLoading) return <FullscreenSpinner />;
  if (!isAuthenticated)
    return <Navigate to="/login" state={{ from: location }} replace />;
  return <>{children}</>;
}

function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuthStore();
  if (isAuthenticated) return <Navigate to="/workspaces" replace />;
  return <>{children}</>;
}

function AdminRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading, user } = useAuthStore();
  const location = useLocation();

  if (isLoading) return <FullscreenSpinner />;
  if (!isAuthenticated)
    return <Navigate to="/login" state={{ from: location }} replace />;
  if (user?.role !== "ROLE_ADMIN") return <Navigate to="/workspaces" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <QueryProvider>
      <ToastProvider />
      <BrowserRouter>
        <AuthBootstrap>
          <Routes>
            <Route path="/" element={<Navigate to="/workspaces" replace />} />

            <Route
              path="/login"
              element={
                <PublicOnlyRoute>
                  <LoginPage />
                </PublicOnlyRoute>
              }
            />
            <Route
              path="/register"
              element={
                <PublicOnlyRoute>
                  <RegisterPage />
                </PublicOnlyRoute>
              }
            />
            <Route
              path="/workspaces"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <WorkspacesPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/workspace/:workspaceId"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <WorkspaceShell />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/workspace/:workspaceId/graph/:graphId"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <WorkspaceShell />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/laws"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <LawBrowserPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />

            <Route
              path="/articles/:lawCode/:number"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <ArticlePage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/conflicts/:graphId"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <ConflictsPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/templates"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <TemplatesPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/scan"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <ContractScannerPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />

            <Route
              path="/admin"
              element={
                <AdminRoute>
                  <MainLayout>
                    <AdminPage />
                  </MainLayout>
                </AdminRoute>
              }
            />
            <Route path="*" element={<Navigate to="/workspaces" replace />} />
          </Routes>
        </AuthBootstrap>
      </BrowserRouter>
    </QueryProvider>
  );
}
