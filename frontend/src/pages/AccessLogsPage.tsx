import { useState, useCallback } from 'react'
import Layout from '../components/Layout.tsx'
import { attendanceApi } from '../api/attendanceApi.ts'
import { AccessLog } from '../types'

export default function AccessLogsPage() {
  const today = new Date().toISOString().split('T')[0]
  const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

  const [startDate, setStartDate] = useState(weekAgo)
  const [endDate, setEndDate] = useState(today)
  const [deviceIdFilter, setDeviceIdFilter] = useState('')
  const [search, setSearch] = useState('')
  const [logs, setLogs] = useState<AccessLog[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fetched, setFetched] = useState(false)

  const fetchLogs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const employeeNo = search.trim() || undefined
      const normalizedDeviceId = Number(deviceIdFilter.trim())
      const deviceId = deviceIdFilter.trim() && !Number.isNaN(normalizedDeviceId)
        ? normalizedDeviceId
        : undefined
      const res = await attendanceApi.getAccessLogs({ employeeNo, deviceId })
      setLogs(res.data?.data ?? [])
      setFetched(true)
    } catch (e: unknown) {
      setError((e as Error).message || 'Failed to fetch access logs')
    } finally {
      setLoading(false)
    }
  }, [search, deviceIdFilter])

  const filteredLogs = logs.filter((log) => {
    const normalizedSearch = search.trim().toLowerCase()
    const matchesSearch = !normalizedSearch || (
      String(log.employeeNo ?? '').toLowerCase().includes(normalizedSearch) ||
      String(log.deviceId ?? '').toLowerCase().includes(normalizedSearch)
    )
    if (!matchesSearch) return false

    if (!log.punchTime) return true
    const punchDate = new Date(log.punchTime)
    const start = new Date(`${startDate}T00:00:00`)
    const end = new Date(`${endDate}T23:59:59`)
    return punchDate >= start && punchDate <= end
  })

  const uniqueEmployees = new Set(filteredLogs.map(l => l.employeeNo).filter(Boolean)).size
  const uniqueDevices = new Set(filteredLogs.map(l => l.deviceId).filter(v => v !== undefined && v !== null)).size

  return (
    <Layout>
      <div className="p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Access Logs</h1>
          <p className="text-sm text-gray-500 mt-1">Device access history and entry records</p>
        </div>

        {/* Filters */}
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
            <div className="flex-1 min-w-[200px]">
              <label className="block text-xs font-medium text-gray-500 mb-1.5">Device ID</label>
              <input
                type="text"
                placeholder="e.g. 12"
                value={deviceIdFilter}
                onChange={(e) => setDeviceIdFilter(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <div className="flex-1 min-w-[200px]">
              <label className="block text-xs font-medium text-gray-500 mb-1.5">Search</label>
              <input
                type="text"
                placeholder="Employee No..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
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
                <p className="text-xs text-gray-400">Total Events</p>
                <p className="text-xl font-bold text-gray-900 mt-1">{filteredLogs.length}</p>
              </div>
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">Unique Employees</p>
                <p className="text-xl font-bold mt-1" style={{ color: '#10b981' }}>{uniqueEmployees}</p>
              </div>
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">Unique Devices</p>
                <p className="text-xl font-bold mt-1 text-gray-900">{uniqueDevices}</p>
              </div>
            </div>

            {filteredLogs.length === 0 ? (
              <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
                No access logs found for the selected criteria.
              </div>
            ) : (
              <div className="space-y-3">
                {filteredLogs.map((log) => {
                  return (
                    <div key={log.id} className="bg-white rounded-xl shadow-sm p-5 flex items-center gap-5">
                      {/* Status indicator */}
                      <div
                        className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0"
                        style={{ background: '#d1fae5' }}
                      >
                        <svg className="w-5 h-5 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                      </div>

                      {/* Info */}
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-gray-900">Employee #{log.employeeNo || '—'}</p>
                        <p className="text-xs text-gray-400 mt-0.5">
                          Access Event {log.deviceId ? `· Device: ${log.deviceId}` : ''}
                        </p>
                      </div>

                      {/* Time */}
                      <div className="hidden md:block text-center min-w-[150px]">
                        <p className="text-xs text-gray-400 mb-0.5">Access Time</p>
                        <p className="text-sm font-medium text-gray-700">
                          {log.punchTime
                            ? new Date(log.punchTime).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })
                            : '—'}
                        </p>
                      </div>

                      {/* Device */}
                      <div className="hidden lg:block text-center min-w-[120px]">
                        <p className="text-xs text-gray-400 mb-0.5">Device</p>
                        <p className="text-sm text-gray-600 font-mono">{log.deviceId ?? '—'}</p>
                      </div>

                      {/* Status badge */}
                      <span
                        className="px-2.5 py-1 rounded-full text-xs font-medium flex-shrink-0"
                        style={{ background: '#d1fae5', color: '#065f46' }}
                      >
                        Recorded
                      </span>
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}

        {!fetched && !loading && (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <p>Select a date range and click Search to view access logs.</p>
          </div>
        )}
      </div>
    </Layout>
  )
}
