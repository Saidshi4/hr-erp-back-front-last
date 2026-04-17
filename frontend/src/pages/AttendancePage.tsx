import { useState, useCallback } from 'react'
import Layout from '../components/Layout'
import { attendanceApi } from '../api/attendanceApi'
import { AttendanceLog } from '../types'

export default function AttendancePage() {
  const today = new Date().toISOString().split('T')[0]
  const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

  const [startDate, setStartDate] = useState(weekAgo)
  const [endDate, setEndDate] = useState(today)
  const [logs, setLogs] = useState<AttendanceLog[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fetched, setFetched] = useState(false)

  const fetchLogs = useCallback(async () => {
    if (!startDate || !endDate) return
    setLoading(true)
    setError(null)
    try {
      const start = `${startDate}T00:00:00`
      const end = `${endDate}T23:59:59`
      const res = await attendanceApi.getRange(start, end)
      setLogs(res.data?.data ?? [])
      setFetched(true)
    } catch (e: unknown) {
      setError((e as Error).message || 'Failed to fetch attendance logs')
    } finally {
      setLoading(false)
    }
  }, [startDate, endDate])

  const formatDateTime = (dt?: string) => {
    if (!dt) return '-'
    return new Date(dt).toLocaleString()
  }

  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Attendance</h1>

        <div className="bg-white rounded-xl shadow-sm p-6 mb-6">
          <div className="flex flex-wrap gap-4 items-end">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">From</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">To</label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              onClick={fetchLogs}
              disabled={loading}
              className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? 'Loading...' : 'Load Logs'}
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">
            {error}
          </div>
        )}

        {fetched && (
          <div className="bg-white rounded-xl shadow-sm">
            <div className="p-4 border-b font-medium text-gray-700">
              Attendance Logs ({logs.length} records)
            </div>
            {logs.length === 0 ? (
              <div className="p-8 text-center text-gray-500">No attendance records found for the selected period.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Employee ID</th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Check In</th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Check Out</th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Device</th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Event</th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {logs.map((log) => (
                      <tr key={log.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4 text-sm text-gray-600">{log.employeeId}</td>
                        <td className="px-6 py-4 text-sm text-gray-600">{formatDateTime(log.checkInTime)}</td>
                        <td className="px-6 py-4 text-sm text-gray-600">{formatDateTime(log.checkOutTime)}</td>
                        <td className="px-6 py-4 text-sm text-gray-600">{log.deviceId || '-'}</td>
                        <td className="px-6 py-4 text-sm text-gray-600">{log.eventType || '-'}</td>
                        <td className="px-6 py-4">
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                            log.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-700'
                          }`}>
                            {log.status || '-'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </Layout>
  )
}
