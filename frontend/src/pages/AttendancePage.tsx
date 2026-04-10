import { useState } from 'react'
import Layout from '../components/Layout'

export default function AttendancePage() {
  const [dateRange] = useState({
    start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    end: new Date().toISOString().split('T')[0],
  })

  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Attendance</h1>
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex gap-4 mb-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">From</label>
              <input type="date" defaultValue={dateRange.start} className="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">To</label>
              <input type="date" defaultValue={dateRange.end} className="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>
          <p className="text-gray-500">Select a date range and employee to view attendance records.</p>
        </div>
      </div>
    </Layout>
  )
}
