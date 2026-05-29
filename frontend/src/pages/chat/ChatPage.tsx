import { useState } from 'react'
import { Menu } from 'lucide-react'
import { Sidebar } from '@/widgets/sidebar/Sidebar'
import { ChatWindow } from '@/widgets/chat-window/ChatWindow'

export default function ChatPage() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="flex h-dvh w-full overflow-hidden bg-[var(--color-bg)]">

      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside className={`
        fixed inset-y-0 left-0 z-50 transform transition-transform duration-300 ease-in-out lg:relative lg:transform-none
        ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
      `}>
        <Sidebar onCloseMobile={() => setSidebarOpen(false)} />
      </aside>

      <main className="flex flex-1 min-w-0 overflow-hidden relative">

        <button
          onClick={() => setSidebarOpen(true)}
          className="lg:hidden absolute top-4 left-4 z-30 w-10 h-10 flex items-center justify-center rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-text)] hover:bg-[var(--color-surface-2)] transition-colors"
        >
          <Menu size={20} />
        </button>

        <ChatWindow />
      </main>
    </div>
  )
}
