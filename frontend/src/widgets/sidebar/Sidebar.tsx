import { useState, useEffect } from "react";
import {
  Plus,
  Trash2,
  MessageSquare,
  Briefcase,
  BookOpen,
  LogOut,
  MoreHorizontal,
  Archive,
  ArchiveRestore,
  X,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/model/useAuthStore";
import { useConversationStore } from "@/features/conversation/model/useConversationStore";
import { Spinner } from "@/shared/ui/Spinner";
import { cn } from "@/shared/lib/cn";
import { LawSearch } from "./LawSearch";
import { GraphsList } from "./GraphsList";
import type { ConversationResponse } from "@/entities/conversation/types";

interface SidebarProps {
  onCloseMobile?: () => void;
}

export function Sidebar({ onCloseMobile }: SidebarProps) {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const {
    conversations,
    activeId,
    isLoading,
    fetchConversations,
    createConversation,
    deleteConversation,
    setActive,
    toggleActive,
  } = useConversationStore();

  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  useEffect(() => {
    function handleClick() {
      setMenuOpenId(null);
    }
    document.addEventListener("click", handleClick);
    return () => document.removeEventListener("click", handleClick);
  }, []);

  async function handleNewChat() {
    const conv = await createConversation();
    setActive(conv.id);
    navigate("/chat");
  }

  async function handleDelete(e: React.MouseEvent, id: string) {
    e.stopPropagation();
    setDeletingId(id);
    try {
      await deleteConversation(id);
    } finally {
      setDeletingId(null);
      setMenuOpenId(null);
    }
  }

  async function handleToggleActive(e: React.MouseEvent, id: string) {
    e.stopPropagation();
    await toggleActive(id);
    setMenuOpenId(null);
  }

  function handleSelectConversation(conv: ConversationResponse) {
    setActive(conv.id);
    navigate("/chat");
  }

  const userInitials = user
    ? (
        (user.firstName?.[0] ?? "") + (user.lastName?.[0] ?? "") ||
        user.username[0]
      ).toUpperCase()
    : "?";

  const userDisplayName = user
    ? user.firstName && user.lastName
      ? `${user.firstName} ${user.lastName}`
      : user.username
    : "Unknown";

  return (
    <aside
      style={{ width: "260px", flexShrink: 0 }}
      className="flex flex-col h-full bg-[var(--color-bg)] border-r border-[var(--color-border)]"
    >

      <div className="lg:hidden flex items-center justify-end pr-4 pt-4">
        <button
          onClick={onCloseMobile}
          className="w-8 h-8 flex items-center justify-center rounded-md text-[var(--color-text-muted)] hover:text-[var(--color-text)] hover:bg-[var(--color-surface-2)] transition-colors"
        >
          <X size={18} />
        </button>
      </div>

      <div
        className="flex items-center gap-3 pr-4 py-5"
        style={{
          paddingLeft: "26px",
          paddingTop: onCloseMobile ? "16px" : "24px",
        }}
      >
        <div
          className="w-16 h-16 rounded-xl flex items-center justify-center shrink-0"
          style={{
            background: "var(--color-accent-glow)",
            border: "1px solid rgba(139,92,246,0.15)",
          }}
        >
          <img
            src="/logo.png"
            alt="LE"
            style={{ width: "40px", height: "40px", objectFit: "contain" }}
          />
        </div>
        <div
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "22px",
            letterSpacing: "0.05em",
            textTransform: "uppercase",
            color: "var(--color-text)",
            fontWeight: 600,
            lineHeight: 1.1,
          }}
        >
          Legis
          <br />
          Entropy
        </div>
      </div>

      <div
        className="px-6 py-4"
        style={{
          paddingTop: "20px",
          paddingLeft: "30px",
          paddingRight: "30px",
        }}
      >
        <LawSearch />
      </div>

      <div className="pr-5 mb-2 space-y-1" style={{ paddingLeft: "30px" }}>
        <button
          onClick={handleNewChat}
          className="w-full flex items-center gap-3 px-4 py-3 rounded-2xl text-[13px] transition-colors cursor-pointer text-left border-none bg-transparent text-[var(--color-text-muted)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-text)]"
          style={{ fontFamily: "'IBM Plex Sans', sans-serif" }}
        >
          <Plus size={18} className="shrink-0" />
          <span className="flex-1">Новый чат</span>
        </button>
        <button
          onClick={() => navigate("/workspaces")}
          className="w-full flex items-center gap-3 px-4 py-3 rounded-2xl text-[13px] transition-colors cursor-pointer text-left border-none bg-transparent text-[var(--color-text-muted)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-text)]"
          style={{ fontFamily: "'IBM Plex Sans', sans-serif" }}
        >
          <Briefcase size={18} className="shrink-0" />
          <span className="flex-1">Рабочие пространства</span>
        </button>
        <button
          onClick={() => navigate("/laws")}
          className="w-full flex items-center gap-3 px-4 py-3 rounded-2xl text-[13px] transition-colors cursor-pointer text-left border-none bg-transparent text-[var(--color-text-muted)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-text)]"
          style={{ fontFamily: "'IBM Plex Sans', sans-serif" }}
        >
          <BookOpen size={18} className="shrink-0" />
          <span className="flex-1">Законы</span>
        </button>
      </div>

      <GraphsList />

      <div
        className="pr-5 pb-2 flex items-center justify-between"
        style={{ paddingTop: "30px", paddingLeft: "30px" }}
      >
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "9px",
            textTransform: "uppercase",
            letterSpacing: "0.12em",
            color: "var(--color-text-muted)",
            fontWeight: 600,
          }}
        >
          Недавние
        </span>
        {isLoading && <Spinner size="sm" />}
      </div>

      <div
        className="flex-1 overflow-y-auto pr-5 pb-3 flex flex-col gap-0.5"
        style={{ paddingTop: "10px", paddingLeft: "30px" }}
      >
        {!isLoading && conversations.length === 0 && (
          <div className="text-center py-10 px-4">
            <div
              className="w-10 h-10 mx-auto mb-3 rounded-md flex items-center justify-center"
              style={{ background: "var(--color-surface-2)" }}
            >
              <MessageSquare
                size={16}
                style={{ color: "var(--color-text-light)" }}
              />
            </div>
            <p
              style={{
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "10px",
                color: "var(--color-text-muted)",
                letterSpacing: "0.06em",
              }}
            >
              Нет диалогов
            </p>
          </div>
        )}

        {conversations.map((conv, i) => (
          <ConversationItem
            key={conv.id}
            conv={conv}
            isActive={conv.id === activeId}
            menuOpen={menuOpenId === conv.id}
            isDeleting={deletingId === conv.id}
            animIndex={i}
            onSelect={() => handleSelectConversation(conv)}
            onMenuToggle={(e) => {
              e.stopPropagation();
              setMenuOpenId((prev) => (prev === conv.id ? null : conv.id));
            }}
            onDelete={(e) => handleDelete(e, conv.id)}
            onToggleActive={(e) => handleToggleActive(e, conv.id)}
          />
        ))}
      </div>

      <div
        className="border-t border-[var(--color-border)]"
        style={{ padding: "5px" }}
      >
        <div
          className="flex items-center gap-2.5 p-3 rounded-md hover:bg-[var(--color-surface)] transition-colors cursor-pointer group"
          style={{ border: "1px solid rgba(139,92,246,0.35)" }}
        >
          <div
            className="w-8 h-8 rounded-md flex items-center justify-center text-white shrink-0"
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "11px",
              fontWeight: 600,
              background: "var(--color-accent)",
            }}
          >
            {userInitials}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-[13px] font-medium text-[var(--color-text)] truncate leading-snug">
              {userDisplayName}
            </p>
            <p
              className="truncate leading-snug text-[9px] text-[var(--color-text-muted)] font-mono"
              style={{ letterSpacing: "0.02em" }}
            >
              {user?.email ?? ""}
            </p>
          </div>
          <button
            onClick={logout}
            className="p-1.5 flex items-center justify-center rounded-md text-[var(--color-text-muted)] hover:text-[var(--color-error)] hover:bg-red-50 transition-colors cursor-pointer opacity-0 group-hover:opacity-100"
            title="Выйти"
          >
            <LogOut size={14} />
          </button>
        </div>
      </div>
    </aside>
  );
}

function ConversationItem({
  conv,
  isActive,
  menuOpen,
  isDeleting,
  animIndex,
  onSelect,
  onMenuToggle,
  onDelete,
  onToggleActive,
}: {
  conv: ConversationResponse;
  isActive: boolean;
  menuOpen: boolean;
  isDeleting: boolean;
  animIndex: number;
  onSelect: () => void;
  onMenuToggle: (e: React.MouseEvent) => void;
  onDelete: (e: React.MouseEvent) => void;
  onToggleActive: (e: React.MouseEvent) => void;
}) {
  return (
    <div
      className="relative animate-fade-in"
      style={{ animationDelay: `${animIndex * 15}ms` }}
    >
      <button
        onClick={onSelect}
        className={cn(
          "w-full flex items-center gap-2.5 py-2 px-3 text-left transition-colors cursor-pointer group rounded-md",
          isActive
            ? "bg-[var(--color-surface-2)]"
            : "bg-transparent hover:bg-[var(--color-surface)]",
          !conv.active && "opacity-50",
        )}
      >
        <div className="flex-1 min-w-0">
          <div
            className="text-[var(--color-text)] truncate leading-snug"
            style={{
              fontFamily: "'IBM Plex Sans', sans-serif",
              fontSize: "12px",
              fontWeight: isActive ? 500 : 400,
            }}
          >
            {conv.title}
          </div>
        </div>

        <button
          onClick={onMenuToggle}
          className={cn(
            "w-6 h-6 shrink-0 flex items-center justify-center rounded-md text-[var(--color-text-muted)] hover:text-[var(--color-text)] hover:bg-[var(--color-surface-3)] transition-colors cursor-pointer border-none",
            menuOpen ? "opacity-100" : "opacity-0 group-hover:opacity-100",
          )}
        >
          {isDeleting ? <Spinner size="sm" /> : <MoreHorizontal size={14} />}
        </button>
      </button>

      {menuOpen && (
        <div
          className="absolute right-0 top-full mt-1 z-50 w-36 bg-[var(--color-bg)] rounded-md border border-[var(--color-border)] animate-fade-in-scale overflow-hidden"
          style={{ boxShadow: "0 4px 12px rgba(30,27,75,0.1)" }}
        >
          <button
            onClick={onToggleActive}
            className="w-full flex items-center gap-2 px-3 py-2 text-[11px] text-[var(--color-text-muted)] hover:bg-[var(--color-surface)] hover:text-[var(--color-text)] transition-colors cursor-pointer border-none bg-transparent"
          >
            {conv.active ? <Archive size={12} /> : <ArchiveRestore size={12} />}
            {conv.active ? "В архив" : "Восстановить"}
          </button>
          <div className="h-px bg-[var(--color-border)] mx-2" />
          <button
            onClick={onDelete}
            className="w-full flex items-center gap-2 px-3 py-2 text-[11px] text-[var(--color-error)] hover:bg-red-50 transition-colors cursor-pointer border-none bg-transparent"
          >
            <Trash2 size={12} />
            Удалить
          </button>
        </div>
      )}
    </div>
  );
}
