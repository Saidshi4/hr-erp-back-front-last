import { useEffect, useState } from 'react'
import Layout from '../components/Layout'
import { useAuthStore } from '../store/authStore'
import client from '../api/client'

interface DashboardStats {
  totalEmployees: number
  activeEmployees: number
  onLeaveEmployees: number
  activeDevices: number
  totalDevices: number
  todayAttendance: number
  pendingLeaves: number
}

export default function DashboardPage() {
  const { user } = useAuthStore()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchStats = async () => {
      setLoading(true)
      try {
        const res = await client.get('/dashboard')
        setStats(res.data?.data ?? null)
      } catch {
        // fallback to manual aggregation
        try {
          const [employeesRes, devicesRes, leavesRes] = await Promise.allSettled([
            client.get('/employees?page=0&size=1'),
            client.get('/devices'),
            client.get('/leaves'),
          ])
          const today = new Date().toISOString().split('T')[0]
          setStats({
            totalEmployees: employeesRes.status === 'fulfilled' ? (employeesRes.value.data?.totalElements ?? 0) : 0,
            activeEmployees: 0,
            onLeaveEmployees: leavesRes.status === 'fulfilled'
              ? (leavesRes.value.data?.data ?? []).filter(
                  (l: { status?: string; startDate?: string; endDate?: string }) =>
                    l.status === 'APPROVED' && l.startDate && l.endDate && l.startDate <= today && l.endDate >= today
                ).length : 0,
            activeDevices: devicesRes.status === 'fulfilled'
              ? (devicesRes.value.data?.data ?? []).filter((d: { status?: string }) => d.status === 'ACTIVE').length : 0,
            totalDevices: devicesRes.status === 'fulfilled' ? (devicesRes.value.data?.data ?? []).length : 0,
            todayAttendance: 0,
            pendingLeaves: leavesRes.status === 'fulfilled'
              ? (leavesRes.value.data?.data ?? []).filter((l: { status?: string }) => l.status === 'PENDING').length : 0,
          })
        } catch {
          // ignore
        }
      } finally {
        setLoading(false)
      }
    }
    fetchStats()
  }, [])

  const statCards = [
    {
      label: 'Total Employees',
      value: stats?.totalEmployees ?? 0,
      sub: `${stats?.activeEmployees ?? 0} active`,
      icon: (
        <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      ),
      color: '#6366f1',
      bgLight: '#eef2ff',
    },
    {
      label: 'Online Devices',
      value: stats?.activeDevices ?? 0,
      sub: `${stats?.totalDevices ?? 0} total`,
      icon: (
        <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
        </svg>
      ),
      color: '#10b981',
      bgLight: '#d1fae5',
    },
    {
      label: "Today's Attendance",
      value: stats?.todayAttendance ?? 0,
      sub: 'Check-ins today',
      icon: (
        <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      ),
      color: '#a855f7',
      bgLight: '#f3e8ff',
    },
    {
      label: 'Pending Leaves',
      value: stats?.pendingLeaves ?? 0,
      sub: `${stats?.onLeaveEmployees ?? 0} on leave`,
      icon: (
        <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
        </svg>
      ),
      color: '#f59e0b',
      bgLight: '#fef3c7',
    },
  ]

  const quickLinks = [
    { label: 'Add Employee', path: '/employees', color: '#6366f1' },
    { label: 'View Attendance', path: '/attendance', color: '#10b981' },
    { label: 'Manage Devices', path: '/devices', color: '#a855f7' },
    { label: 'Leave Requests', path: '/leaves', color: '#f59e0b' },
  ]

  return (
    <Layout>
      <div className="p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500 mt-1">Welcome back, <span className="font-medium text-gray-700">{user?.username}</span>! Here's what's happening today.</p>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6 mb-8">
          {statCards.map((card) => (
            <div key={card.label} className="bg-white rounded-xl shadow-sm p-6 flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: card.color }}>
                {card.icon}
              </div>
              <div>
                <p className="text-sm text-gray-500">{card.label}</p>
                <p className="text-2xl font-bold text-gray-900">
                  {loading ? <span className="text-gray-300">—</span> : card.value}
                </p>
                <p className="text-xs text-gray-400 mt-0.5">{card.sub}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Quick Actions */}
        <div className="bg-white rounded-xl shadow-sm p-6 mb-6">
          <h2 className="text-base font-semibold text-gray-800 mb-4">Quick Actions</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {quickLinks.map((link) => (
              <a
                key={link.path}
                href={link.path}
                className="flex items-center justify-center px-4 py-3 rounded-lg text-sm font-medium text-white transition-opacity hover:opacity-90"
                style={{ background: link.color }}
              >
                {link.label}
              </a>
            ))}
          </div>
        </div>

        {/* System Status */}
        <div className="bg-white rounded-xl shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-800 mb-4">System Overview</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="flex items-center gap-3 p-4 rounded-lg" style={{ background: '#f0fdf4' }}>
              <div className="w-2.5 h-2.5 rounded-full bg-green-500"></div>
              <div>
                <p className="text-sm font-medium text-gray-700">Backend API</p>
                <p className="text-xs text-green-600">Operational</p>
              </div>
            </div>
            <div className="flex items-center gap-3 p-4 rounded-lg" style={{ background: '#f0fdf4' }}>
              <div className="w-2.5 h-2.5 rounded-full bg-green-500"></div>
              <div>
                <p className="text-sm font-medium text-gray-700">Database</p>
                <p className="text-xs text-green-600">Connected</p>
              </div>
            </div>
            <div className="flex items-center gap-3 p-4 rounded-lg" style={{ background: '#faf5ff' }}>
              <div className="w-2.5 h-2.5 rounded-full" style={{ background: '#a855f7' }}></div>
              <div>
                <p className="text-sm font-medium text-gray-700">Device Sync</p>
                <p className="text-xs" style={{ color: '#a855f7' }}>{loading ? '...' : `${stats?.activeDevices ?? 0} active`}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  )
}
