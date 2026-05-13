import { useState, useCallback } from 'react'
import Layout from '../components/Layout.tsx'
import { attendanceApi } from '../api/attendanceApi.ts'
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

  const formatTime = (dt?: string) => {
    if (!dt) return '—'
    return new Date(dt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  const presentCount = logs.filter(l => l.checkInTime && !l.checkOutTime).length
  const completedCount = logs.filter(l => l.checkInTime && l.checkOutTime).length

  return (
    <Layout>
      <div className="p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Attendance</h1>
          <p className="text-sm text-gray-500 mt-1">View and track employee attendance records</p>
        </div>

        {/* Filter Card */}
        <div className="bg-white rounded-xl shadow-sm p-5 mb-6">
          <div className="flex flex-wrap gap-4 items-end">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">From Date</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">To Date</label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <button
              onClick={fetchLogs}
              disabled={loading}
              className="flex items-center gap-2 px-5 py-2 text-sm font-medium text-white rounded-lg disabled:opacity-50 transition-colors"
              style={{ background: '#a855f7' }}
            >
              {loading ? (
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
              ) : (
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              )}
              {loading ? 'Loading...' : 'Search'}
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4 text-sm">
            {error}
          </div>
        )}

        {fetched && (
          <>
            {/* Summary */}
            <div className="grid grid-cols-3 gap-4 mb-6">
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">Total Records</p>
                <p className="text-xl font-bold text-gray-900 mt-1">{logs.length}</p>
              </div>
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">Currently In</p>
                <p className="text-xl font-bold mt-1" style={{ color: '#10b981' }}>{presentCount}</p>
              </div>
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">Completed</p>
                <p className="text-xl font-bold text-gray-900 mt-1">{completedCount}</p>
              </div>
            </div>

            {/* Logs */}
            {logs.length === 0 ? (
              <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
                No attendance records found for the selected period.
              </div>
            ) : (
              <div className="space-y-3">
                {logs.map((log) => (
                  <div key={log.id} className="bg-white rounded-xl shadow-sm p-5 flex items-center gap-5">
                    <div className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: '#faf5ff' }}>
                      <svg className="w-5 h-5" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                      </svg>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-gray-900">Employee #{log.employeeId}</p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {log.eventType || 'Check-in'} {log.deviceId ? `· ${log.deviceId}` : ''}
                      </p>
                    </div>
                    <div className="hidden md:block text-center min-w-[120px]">
                      <p className="text-xs text-gray-400 mb-0.5">Check In</p>
                      <p className="text-sm font-medium text-gray-700">{formatTime(log.checkInTime)}</p>
                      <p className="text-xs text-gray-400">{log.checkInTime ? new Date(log.checkInTime).toLocaleDateString() : '—'}</p>
                    </div>
                    <div className="hidden md:block text-center min-w-[120px]">
                      <p className="text-xs text-gray-400 mb-0.5">Check Out</p>
                      <p className="text-sm font-medium text-gray-700">{formatTime(log.checkOutTime)}</p>
                    </div>
                    <div>
                      <span
                        className="px-2.5 py-1 rounded-full text-xs font-medium"
                        style={log.checkInTime && log.checkOutTime
                          ? { background: '#d1fae5', color: '#065f46' }
                          : log.checkInTime
                          ? { background: '#fef3c7', color: '#92400e' }
                          : { background: '#f3f4f6', color: '#6b7280' }}
                      >
                        {log.checkInTime && log.checkOutTime ? 'Completed' :
                         log.checkInTime ? 'In Office' : 'No Record'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        {!fetched && !loading && (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <p>Select a date range and click Search to view attendance records.</p>
          </div>
        )}
      </div>
    </Layout>
  )
}
