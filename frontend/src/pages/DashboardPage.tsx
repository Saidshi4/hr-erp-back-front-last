import { useEffect, useState } from 'react'
import Layout from '../components/Layout'
import { useAuthStore } from '../store/authStore'
import client from '../api/client'

interface DashboardStats {
  totalEmployees: number
  presentToday: number
  onLeave: number
  activeDevices: number
}

export default function DashboardPage() {
  const { user } = useAuthStore()
  const [stats, setStats] = useState<DashboardStats>({
    totalEmployees: 0,
    presentToday: 0,
    onLeave: 0,
    activeDevices: 0,
  })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchStats = async () => {
      setLoading(true)
      try {
        const today = new Date().toISOString().split('T')[0]
        const [employeesRes, devicesRes, leavesRes] = await Promise.allSettled([
          client.get('/employees?page=0&size=1'),
          client.get('/devices'),
          client.get('/leaves'),
        ])

        const totalEmployees =
          employeesRes.status === 'fulfilled'
            ? employeesRes.value.data?.totalElements ?? 0
            : 0

        const activeDevices =
          devicesRes.status === 'fulfilled'
            ? (devicesRes.value.data?.data ?? []).filter(
                (d: { status?: string }) => d.status === 'ACTIVE'
              ).length
            : 0

        const onLeave =
          leavesRes.status === 'fulfilled'
            ? (leavesRes.value.data?.data ?? []).filter(
                (l: { status?: string; startDate?: string; endDate?: string }) =>
                  l.status === 'APPROVED' &&
                  l.startDate &&
                  l.endDate &&
                  l.startDate <= today &&
                  l.endDate >= today
              ).length
            : 0

        setStats({ totalEmployees, presentToday: 0, onLeave, activeDevices })
      } finally {
        setLoading(false)
      }
    }
    fetchStats()
  }, [])

  const cards = [
    { label: 'Total Employees', value: loading ? '...' : stats.totalEmployees, color: 'bg-blue-500' },
    { label: 'Present Today', value: loading ? '...' : stats.presentToday, color: 'bg-green-500' },
    { label: 'On Leave', value: loading ? '...' : stats.onLeave, color: 'bg-yellow-500' },
    { label: 'Devices Active', value: loading ? '...' : stats.activeDevices, color: 'bg-purple-500' },
  ]

  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">Dashboard</h1>
        <p className="text-gray-600 mb-8">Welcome back, {user?.username}!</p>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {cards.map((card) => (
            <div key={card.label} className="bg-white rounded-xl shadow-sm p-6">
              <div className={`w-12 h-12 ${card.color} rounded-lg mb-4`} />
              <p className="text-gray-500 text-sm">{card.label}</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">{card.value}</p>
            </div>
          ))}
        </div>
      </div>
    </Layout>
  )
}
