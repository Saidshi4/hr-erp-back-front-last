import Layout from '../components/Layout'
import { useAuthStore } from '../store/authStore'

export default function DashboardPage() {
  const { user } = useAuthStore()

  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">Dashboard</h1>
        <p className="text-gray-600 mb-8">Welcome back, {user?.username}!</p>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {[
            { label: 'Total Employees', value: '-', color: 'bg-blue-500' },
            { label: 'Present Today', value: '-', color: 'bg-green-500' },
            { label: 'On Leave', value: '-', color: 'bg-yellow-500' },
            { label: 'Devices Active', value: '-', color: 'bg-purple-500' },
          ].map((card) => (
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
