import { useState, type CSSProperties, type ReactNode, type ChangeEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi, type AdminUser, type CreateUserRequest } from '@/features/admin/api/adminApi'
import { Spinner } from '@/shared/ui/Spinner'
import {
  Users, BarChart3, Shield, Trash2, UserCheck, UserX, ExternalLink, UserPlus,
} from 'lucide-react'

type Tab = 'users' | 'monitoring'

const S = {
  page: {
    padding: '24px',
    maxWidth: 1280,
    margin: '0 auto',
    color: 'var(--ink)',
    fontFamily: 'var(--mono)',
  } as CSSProperties,

  heading: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    marginBottom: 28,
  } as CSSProperties,

  h1: {
    fontSize: 20,
    fontWeight: 700,
    color: 'var(--ink)',
    letterSpacing: '-0.03em',
  } as CSSProperties,

  tabBar: {
    display: 'flex',
    gap: 4,
    borderBottom: '1px solid var(--line)',
    marginBottom: 24,
  } as CSSProperties,

  panel: {
    background: 'var(--panel)',
    border: '1px solid var(--line)',
    borderRadius: 14,
    overflow: 'hidden',
  } as CSSProperties,

  panelHead: {
    padding: '12px 20px',
    borderBottom: '1px solid var(--line)',
    fontSize: 13,
    fontWeight: 600,
    color: 'var(--ink)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  } as CSSProperties,

  statCard: (color: string) => ({
    background: 'var(--panel)',
    border: '1px solid var(--line)',
    borderRadius: 14,
    padding: '20px 24px',
    color,
  }) as CSSProperties,

  statValue: {
    fontSize: 30,
    fontWeight: 700,
    lineHeight: 1,
  } as CSSProperties,

  statLabel: {
    fontSize: 12,
    color: 'var(--dim)',
    marginTop: 6,
  } as CSSProperties,

  th: {
    padding: '10px 20px',
    textAlign: 'left',
    fontSize: 11,
    fontWeight: 600,
    color: 'var(--dim)',
    textTransform: 'uppercase',
    letterSpacing: '0.06em',
    background: 'var(--bg)',
    borderBottom: '1px solid var(--line)',
  } as CSSProperties,

  td: {
    padding: '12px 20px',
    borderBottom: '1px solid var(--line-strong, rgba(26,29,46,0.07))',
    fontSize: 13,
    color: 'var(--ink)',
    verticalAlign: 'middle',
  } as CSSProperties,

  select: {
    background: 'var(--bg)',
    border: '1px solid var(--line)',
    borderRadius: 8,
    padding: '4px 8px',
    fontSize: 12,
    color: 'var(--ink)',
    fontFamily: 'var(--mono)',
    cursor: 'pointer',
    outline: 'none',
  } as CSSProperties,

  iconBtn: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 30,
    height: 30,
    borderRadius: 8,
    border: '1px solid transparent',
    color: 'var(--dim)',
    cursor: 'pointer',
    transition: 'all 120ms',
  } as CSSProperties,

  primaryBtn: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    padding: '6px 14px',
    borderRadius: 8,
    background: 'var(--purple)',
    color: '#fff',
    fontSize: 12,
    fontWeight: 600,
    border: 'none',
    cursor: 'pointer',
    fontFamily: 'var(--mono)',
    transition: 'opacity 120ms',
  } as CSSProperties,

  ghostBtn: {
    padding: '6px 16px',
    borderRadius: 8,
    border: '1px solid var(--line)',
    background: 'var(--panel)',
    color: 'var(--ink)',
    fontSize: 12,
    fontWeight: 500,
    cursor: 'pointer',
    fontFamily: 'var(--mono)',
    transition: 'border-color 120ms',
  } as CSSProperties,

  overlay: {
    position: 'fixed' as const,
    inset: 0,
    background: 'rgba(26,29,46,0.4)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 50,
  } as CSSProperties,

  modal: {
    background: 'var(--panel)',
    border: '1px solid var(--line)',
    borderRadius: 16,
    padding: 28,
    maxWidth: 440,
    width: '100%',
    margin: '0 16px',
    boxShadow: '0 12px 40px rgba(26,29,46,0.18)',
  } as CSSProperties,

  input: {
    width: '100%',
    background: 'var(--bg)',
    border: '1px solid var(--line)',
    borderRadius: 8,
    padding: '8px 12px',
    fontSize: 13,
    color: 'var(--ink)',
    fontFamily: 'var(--mono)',
    outline: 'none',
    boxSizing: 'border-box' as const,
  } as CSSProperties,

  fieldLabel: {
    display: 'block',
    fontSize: 11,
    color: 'var(--dim)',
    marginBottom: 5,
    fontWeight: 600,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.05em',
  } as CSSProperties,
}

export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('users')

  return (
    <div style={S.page}>
      <div style={S.heading}>
        <Shield size={20} color="var(--purple)" />
        <h1 style={S.h1}>Admin Panel</h1>
      </div>

      <div style={S.tabBar}>
        <TabButton active={tab === 'users'} onClick={() => setTab('users')} icon={<Users size={14} />}>
          Users
        </TabButton>
        <TabButton active={tab === 'monitoring'} onClick={() => setTab('monitoring')} icon={<BarChart3 size={14} />}>
          Monitoring
        </TabButton>
      </div>

      {tab === 'users' && <UsersTab />}
      {tab === 'monitoring' && <MonitoringTab />}
    </div>
  )
}

function TabButton({ active, onClick, icon, children }: {
  active: boolean
  onClick: () => void
  icon: ReactNode
  children: ReactNode
}) {
  return (
    <button
      onClick={onClick}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '8px 16px',
        fontSize: 13,
        fontWeight: 500,
        fontFamily: 'var(--mono)',
        color: active ? 'var(--purple)' : 'var(--dim)',
        background: 'none',
        border: 'none',
        borderBottom: active ? '2px solid var(--purple)' : '2px solid transparent',
        cursor: 'pointer',
        transition: 'color 120ms',
        marginBottom: -1,
      }}
    >
      {icon}
      {children}
    </button>
  )
}

function UsersTab() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)
  const [showCreateModal, setShowCreateModal] = useState(false)

  const { data: stats } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: adminApi.getStats,
  })

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', page],
    queryFn: () => adminApi.getUsers(page, 20),
  })

  const toggleActive = useMutation({
    mutationFn: (id: number) => adminApi.toggleActive(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const updateRole = useMutation({
    mutationFn: ({ id, role }: { id: number; role: string }) => adminApi.updateRole(id, role),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const updatePlan = useMutation({
    mutationFn: ({ id, planType }: { id: number; planType: string }) => adminApi.updatePlan(id, planType),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const deleteUser = useMutation({
    mutationFn: (id: number) => adminApi.deleteUser(id),
    onSuccess: () => {
      setConfirmDelete(null)
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      qc.invalidateQueries({ queryKey: ['admin-stats'] })
    },
  })

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {stats && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12 }}>
          <StatCard label="Total Users" value={stats.totalUsers} color="var(--purple)" />
          <StatCard label="Active Users" value={stats.activeUsers} color="#10b981" />
          <StatCard label="Admins" value={stats.adminUsers} color="var(--amber, #f59e0b)" />
        </div>
      )}

      <div style={S.panel}>
        <div style={S.panelHead}>
          <span>All Users</span>
          <button style={S.primaryBtn} onClick={() => setShowCreateModal(true)}>
            <UserPlus size={13} />
            Create User
          </button>
        </div>

        {isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><Spinner /></div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead>
                  <tr>
                    <th style={S.th}>User</th>
                    <th style={S.th}>Email</th>
                    <th style={S.th}>Role</th>
                    <th style={S.th}>Plan</th>
                    <th style={S.th}>Status</th>
                    <th style={S.th}>Joined</th>
                    <th style={{ ...S.th, textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data?.content.map(user => (
                    <UserRow
                      key={user.id}
                      user={user}
                      onToggleActive={() => toggleActive.mutate(user.id)}
                      onRoleChange={(role) => updateRole.mutate({ id: user.id, role })}
                      onPlanChange={(planType) => updatePlan.mutate({ id: user.id, planType })}
                      onDelete={() => setConfirmDelete(user.id)}
                      isProcessing={toggleActive.isPending || updateRole.isPending || updatePlan.isPending}
                    />
                  ))}
                </tbody>
              </table>
            </div>

            {data && data.page.totalPages > 1 && (
              <div style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '12px 20px', borderTop: '1px solid var(--line)', fontSize: 12, color: 'var(--dim)',
              }}>
                <span>Page {page + 1} of {data.page.totalPages}</span>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={S.ghostBtn}>Prev</button>
                  <button disabled={page >= data.page.totalPages - 1} onClick={() => setPage(p => p + 1)} style={S.ghostBtn}>Next</button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {confirmDelete !== null && (
        <ConfirmDialog
          message="Delete this user permanently?"
          onConfirm={() => deleteUser.mutate(confirmDelete)}
          onCancel={() => setConfirmDelete(null)}
          loading={deleteUser.isPending}
        />
      )}

      {showCreateModal && (
        <CreateUserModal
          onClose={() => setShowCreateModal(false)}
          onCreated={() => {
            setShowCreateModal(false)
            qc.invalidateQueries({ queryKey: ['admin-users'] })
            qc.invalidateQueries({ queryKey: ['admin-stats'] })
          }}
        />
      )}
    </div>
  )
}

function UserRow({ user, onToggleActive, onRoleChange, onPlanChange, onDelete, isProcessing }: {
  user: AdminUser
  onToggleActive: () => void
  onRoleChange: (role: string) => void
  onPlanChange: (planType: string) => void
  onDelete: () => void
  isProcessing: boolean
}) {
  const isAdmin = user.role === 'ROLE_ADMIN'
  const joinedDate = user.createdAt
    ? new Date(user.createdAt).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit' })
    : '—'

  return (
    <tr style={{ transition: 'background 80ms' }}
      onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg)')}
      onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
    >
      <td style={S.td}>
        <div style={{ fontWeight: 600 }}>{user.username}</div>
        <div style={{ fontSize: 11, color: 'var(--dim)' }}>{user.firstName} {user.lastName}</div>
      </td>
      <td style={{ ...S.td, color: 'var(--dim)' }}>{user.email}</td>
      <td style={S.td}>
        <select value={user.role} onChange={e => onRoleChange(e.target.value)} disabled={isProcessing} style={S.select}>
          <option value="ROLE_USER">User</option>
          <option value="ROLE_ADMIN">Admin</option>
        </select>
      </td>
      <td style={S.td}>
        <select value={user.planType ?? 'FREE'} onChange={e => onPlanChange(e.target.value)} disabled={isProcessing} style={S.select}>
          <option value="FREE">Free</option>
          <option value="BASIC">Basic</option>
          <option value="PRO">Pro</option>
        </select>
      </td>
      <td style={S.td}>
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          padding: '3px 10px', borderRadius: 999, fontSize: 11, fontWeight: 600,
          background: user.active ? 'rgba(16,185,129,0.12)' : 'rgba(220,38,38,0.10)',
          color: user.active ? '#059669' : '#dc2626',
        }}>
          {user.active ? 'Active' : 'Inactive'}
        </span>
      </td>
      <td style={{ ...S.td, color: 'var(--dim)', fontSize: 12 }}>{joinedDate}</td>
      <td style={S.td}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 4 }}>
          <button
            onClick={onToggleActive}
            disabled={isProcessing}
            title={user.active ? 'Deactivate' : 'Activate'}
            style={S.iconBtn}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
          >
            {user.active ? <UserX size={15} /> : <UserCheck size={15} />}
          </button>
          {!isAdmin && (
            <button
              onClick={onDelete}
              title="Delete user"
              style={{ ...S.iconBtn, color: 'var(--dim)' }}
              onMouseEnter={e => { e.currentTarget.style.background = 'rgba(220,38,38,0.08)'; e.currentTarget.style.color = '#dc2626' }}
              onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = 'var(--dim)' }}
            >
              <Trash2 size={15} />
            </button>
          )}
        </div>
      </td>
    </tr>
  )
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={S.statCard(color)}>
      <div style={{ ...S.statValue, color }}>{value}</div>
      <div style={S.statLabel}>{label}</div>
    </div>
  )
}

function ConfirmDialog({ message, onConfirm, onCancel, loading }: {
  message: string
  onConfirm: () => void
  onCancel: () => void
  loading: boolean
}) {
  return (
    <div style={S.overlay}>
      <div style={S.modal}>
        <p style={{ color: 'var(--ink)', marginBottom: 24, fontSize: 14 }}>{message}</p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={onCancel} style={S.ghostBtn}>Cancel</button>
          <button
            onClick={onConfirm}
            disabled={loading}
            style={{ ...S.primaryBtn, background: 'var(--danger, #dc2626)', opacity: loading ? 0.6 : 1 }}
          >
            {loading ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </div>
    </div>
  )
}

function CreateUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [form, setForm] = useState<CreateUserRequest>({
    username: '', email: '', password: '',
    firstName: '', lastName: '',
    role: 'ROLE_USER', planType: 'FREE',
  })
  const [error, setError] = useState<string | null>(null)

  const createUser = useMutation({
    mutationFn: () => adminApi.createUser(form),
    onSuccess: onCreated,
    onError: (e: any) => {
      setError(e?.response?.data?.message ?? e?.message ?? 'Failed to create user')
    },
  })

  const set = (k: keyof CreateUserRequest) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }))

  const canSubmit = !!form.username && !!form.email && !!form.password && !createUser.isPending

  return (
    <div style={S.overlay}>
      <div style={S.modal}>
        <h2 style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink)', marginBottom: 20 }}>Create User</h2>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="Username *">
            <input value={form.username} onChange={set('username')} placeholder="username" style={S.input} />
          </Field>
          <Field label="Email *">
            <input type="email" value={form.email} onChange={set('email')} placeholder="email@example.com" style={S.input} />
          </Field>
          <Field label="Password *">
            <input type="password" value={form.password} onChange={set('password')} placeholder="password" style={S.input} />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="First name">
              <input value={form.firstName} onChange={set('firstName')} placeholder="First" style={S.input} />
            </Field>
            <Field label="Last name">
              <input value={form.lastName} onChange={set('lastName')} placeholder="Last" style={S.input} />
            </Field>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Role">
              <select value={form.role} onChange={set('role')} style={{ ...S.input, cursor: 'pointer' }}>
                <option value="ROLE_USER">User</option>
                <option value="ROLE_ADMIN">Admin</option>
              </select>
            </Field>
            <Field label="Plan">
              <select value={form.planType} onChange={set('planType')} style={{ ...S.input, cursor: 'pointer' }}>
                <option value="FREE">Free</option>
                <option value="BASIC">Basic</option>
                <option value="PRO">Pro</option>
              </select>
            </Field>
          </div>
        </div>

        {error && <p style={{ marginTop: 12, fontSize: 12, color: 'var(--danger, #dc2626)' }}>{error}</p>}

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 24 }}>
          <button onClick={onClose} style={S.ghostBtn}>Cancel</button>
          <button
            onClick={() => createUser.mutate()}
            disabled={!canSubmit}
            style={{ ...S.primaryBtn, opacity: canSubmit ? 1 : 0.5 }}
          >
            {createUser.isPending ? 'Creating…' : 'Create'}
          </button>
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <label style={S.fieldLabel}>{label}</label>
      {children}
    </div>
  )
}

function MonitoringTab() {
  const grafanaUrl = `http://${window.location.hostname}:3000`
  const prometheusUrl = `http://${window.location.hostname}:9090`

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <MonitoringCard
        title="Grafana"
        description="Dashboards, metrics, service health, alerts"
        href={grafanaUrl}
        accentColor="#f97316"
        hint="Opens in a new tab — accept the self-signed cert if prompted"
      />
      <MonitoringCard
        title="Prometheus"
        description="Raw metrics, PromQL query explorer, targets"
        href={prometheusUrl}
        accentColor="#ef4444"
        hint="Opens in a new tab"
      />
    </div>
  )
}

function MonitoringCard({ title, description, href, accentColor, hint }: {
  title: string
  description: string
  href: string
  accentColor: string
  hint?: string
}) {
  return (
    <div style={{
      background: 'var(--panel)', border: '1px solid var(--line)',
      borderRadius: 14, padding: '20px 24px',
      display: 'flex', alignItems: 'center', gap: 20,
    }}>
      <div style={{
        width: 48, height: 48, borderRadius: 12, flexShrink: 0,
        background: accentColor + '18',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <BarChart3 size={22} color={accentColor} />
      </div>
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--ink)' }}>{title}</div>
        <div style={{ fontSize: 12, color: 'var(--dim)', marginTop: 2 }}>{description}</div>
        {hint && <div style={{ fontSize: 11, color: 'var(--dim)', marginTop: 4, opacity: 0.7 }}>{hint}</div>}
      </div>
      <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '8px 16px', borderRadius: 8,
          background: accentColor, color: '#fff',
          fontSize: 12, fontWeight: 600,
          textDecoration: 'none', fontFamily: 'var(--mono)',
          flexShrink: 0,
        }}
      >
        Open <ExternalLink size={12} />
      </a>
    </div>
  )
}
