import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Eye, EyeOff, LogIn } from "lucide-react";
import { useAuthStore } from "@/features/auth/model/useAuthStore";
import { useIsMobile } from "@/shared/hooks/useMediaQuery";
import LoginPageMobile from "./LoginPageMobile";

function LoginPageDesktop() {
  const navigate = useNavigate();
  const { login, isLoading, error, clearError, isAuthenticated } =
    useAuthStore();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [fieldErr, setFieldErr] = useState<Record<string, string>>({});

  useEffect(() => {
    if (isAuthenticated) navigate("/workspaces", { replace: true });
  }, [isAuthenticated, navigate]);
  useEffect(() => {
    clearError();
  }, [clearError]);

  function validate() {
    const errs: Record<string, string> = {};
    if (!username.trim()) errs.username = "Обязательное поле";
    if (!password) errs.password = "Обязательное поле";
    setFieldErr(errs);
    return Object.keys(errs).length === 0;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    try {
      await login({ username: username.trim(), password });
      navigate("/workspaces", { replace: true });
    } catch {

    }
  }

  return (
    <div
      style={{
        minHeight: "100dvh",
        display: "flex",
        overflow: "hidden",
        background: "var(--color-bg)",
      }}
    >

      <div
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          height: "3px",
          background:
            "linear-gradient(90deg, transparent, var(--color-accent), transparent)",
          zIndex: 1,
        }}
      />

      <BrandPanel />

      <div
        style={{
          flex: 1,
          background: "var(--color-bg)",
          borderLeft: "1px solid var(--color-border)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "48px",
        }}
      >
        <div style={{ width: "100%", maxWidth: "400px" }}>
          <div className="label-mono" style={{ marginBottom: "8px" }}>
            Вход
          </div>
          <h2
            style={{
              fontFamily: "'IBM Plex Serif', serif",
              fontSize: "32px",
              fontWeight: 500,
              color: "var(--color-text)",
              margin: "0 0 28px",
              letterSpacing: "-0.01em",
            }}
          >
            Продолжить сессию
          </h2>

          {error && (
            <div
              style={{
                background: "rgba(239,68,68,0.06)",
                border: "1px solid rgba(239,68,68,0.2)",
                borderRadius: "6px",
                padding: "12px 14px",
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "12px",
                color: "var(--color-error)",
                letterSpacing: "0.02em",
                display: "flex",
                gap: "10px",
                alignItems: "flex-start",
                marginBottom: "20px",
              }}
            >
              <span style={{ flexShrink: 0 }}>!</span>
              <span>{error}</span>
            </div>
          )}

          <form
            onSubmit={handleSubmit}
            noValidate
            style={{ display: "flex", flexDirection: "column", gap: "16px" }}
          >
            <AuthField
              label="Имя пользователя или email"
              error={fieldErr.username}
            >
              <input
                type="text"
                autoComplete="username"
                autoFocus
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  clearError();
                }}
                placeholder="m.aalto@firm.com"
                style={{
                  borderColor: fieldErr.username
                    ? "var(--color-error)"
                    : undefined,
                }}
              />
            </AuthField>

            <AuthField label="Пароль" error={fieldErr.password}>
              <div style={{ position: "relative" }}>
                <input
                  type={showPw ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    clearError();
                  }}
                  placeholder="Введите пароль"
                  style={{
                    borderColor: fieldErr.password
                      ? "var(--color-error)"
                      : undefined,
                    paddingRight: "44px",
                  }}
                />
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={() => setShowPw((v) => !v)}
                  style={{
                    position: "absolute",
                    right: "14px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    background: "none",
                    border: "none",
                    color: "var(--color-text-light)",
                    cursor: "pointer",
                    display: "flex",
                    padding: "8px",
                    minWidth: "44px",
                    minHeight: "44px",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                  onMouseEnter={(e) =>
                    (e.currentTarget.style.color = "var(--color-accent)")
                  }
                  onMouseLeave={(e) =>
                    (e.currentTarget.style.color = "var(--color-text-light)")
                  }
                >
                  {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </AuthField>

            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <span
                style={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "11px",
                  color: "var(--color-accent)",
                  cursor: "pointer",
                  letterSpacing: "0.04em",
                }}
              >
                забыли?
              </span>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              style={{
                marginTop: "8px",
                height: "48px",
                padding: "0 24px",
                background: isLoading
                  ? "var(--color-accent-hover)"
                  : "var(--color-accent)",
                color: "white",
                border: "none",
                borderRadius: "8px",
                fontFamily: "'IBM Plex Sans', sans-serif",
                fontSize: "15px",
                fontWeight: 600,
                letterSpacing: "0.01em",
                cursor: isLoading ? "not-allowed" : "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "8px",
                opacity: isLoading ? 0.7 : 1,
                transition: "all 0.2s ease",
                boxShadow: "0 4px 12px rgba(139,92,246,0.3)",
              }}
              onMouseEnter={(e) => {
                if (!isLoading) {
                  e.currentTarget.style.background =
                    "var(--color-accent-hover)";
                  e.currentTarget.style.boxShadow =
                    "0 6px 16px rgba(139,92,246,0.4)";
                }
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = "var(--color-accent)";
                e.currentTarget.style.boxShadow =
                  "0 4px 12px rgba(139,92,246,0.3)";
              }}
            >
              {isLoading ? (
                <span
                  style={{
                    width: "18px",
                    height: "18px",
                    border: "2px solid rgba(255,255,255,0.3)",
                    borderTopColor: "white",
                    borderRadius: "50%",
                    display: "inline-block",
                    animation: "spin 0.7s linear infinite",
                  }}
                />
              ) : (
                <>
                  <LogIn size={18} />
                  <span>Войти</span>
                </>
              )}
            </button>
          </form>

          <div
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "12px",
              color: "var(--color-text-muted)",
              textAlign: "center",
              marginTop: "24px",
              letterSpacing: "0.02em",
            }}
          >
            Нет аккаунта?{" "}
            <Link
              to="/register"
              style={{
                color: "var(--color-accent)",
                textDecoration: "none",
                fontWeight: 600,
                borderBottom: "1px solid var(--color-accent)",
              }}
              onMouseEnter={(e) => (e.currentTarget.style.opacity = "0.8")}
              onMouseLeave={(e) => (e.currentTarget.style.opacity = "1")}
            >
              Запросить доступ
            </Link>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
}

function BrandPanel() {
  return (
    <div
      style={{
        flex: 1,
        background: "var(--color-surface)",
        position: "relative",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: "48px",
        overflow: "hidden",
      }}
    >

      <div
        style={{
          position: "absolute",
          inset: 0,
          background:
            "radial-gradient(60% 50% at 28% 65%, var(--color-accent-glow), transparent 70%)",
          pointerEvents: "none",
        }}
      />
      <div
        className="dotgrid"
        style={{ position: "absolute", inset: 0, pointerEvents: "none" }}
      />

      <div
        style={{
          position: "absolute",
          right: "-60px",
          bottom: "-60px",
          pointerEvents: "none",
        }}
      >
        <img
          src="/logo.png"
          alt=""
          style={{
            width: "340px",
            height: "340px",
            objectFit: "contain",
            opacity: 0.06,
          }}
        />
      </div>

      <div
        style={{
          position: "relative",
          flex: 1,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          textAlign: "center",
        }}
      >
        <img
          src="/logo.png"
          alt="LE"
          style={{
            width: "80px",
            height: "80px",
            objectFit: "contain",
            marginBottom: "20px",
          }}
        />
        <div
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "14px",
            letterSpacing: "0.2em",
            textTransform: "uppercase",
            color: "var(--color-text)",
            fontWeight: 500,
            marginBottom: "32px",
          }}
        >
          Legis Entropy
        </div>
        <h1
          style={{
            fontFamily: "'IBM Plex Serif', serif",
            fontSize: "40px",
            fontWeight: 500,
            lineHeight: 1.15,
            color: "var(--color-text)",
            margin: 0,
            letterSpacing: "-0.02em",
          }}
        ></h1>
      </div>
    </div>
  );
}

function AuthField({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="auth-field">
      <label>{label}</label>
      {children}
      {error && (
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "11px",
            color: "var(--color-error)",
            letterSpacing: "0.04em",
          }}
        >
          {error}
        </span>
      )}
    </div>
  );
}

export default function LoginPage() {
  const isMobile = useIsMobile();
  return isMobile ? <LoginPageMobile /> : <LoginPageDesktop />;
}
