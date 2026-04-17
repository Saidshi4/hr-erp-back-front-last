import { useState } from 'react'
import Layout from '../components/Layout'
import client from '../api/client'

interface AttendanceReportRow {
  employeeId: number
  employeeName: string
  presentDays: number
  absentDays: number
  lateDays: number
  totalHoursWorked: number
}

export default function ReportsPage() {
  const [start, setStart] = useState(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0])
  const [end, setEnd] = useState(new Date().toISOString().split('T')[0])
  const [report, setReport] = useState<AttendanceReportRow[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchReport = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await client.get(`/reports/attendance?start=${start}&end=${end}`)
      setReport(res.data?.data || [])
    } catch (e: unknown) {
      setError((e as Error).message || 'Failed to load report')
      setReport([])
    } finally {
      setLoading(false)
    }
  }

  const exportExcel = () => {
    window.open(`/api/export/attendance/excel?start=${start}&end=${end}`, '_blank')
  }

  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Reports</h1>

        <div className="bg-white rounded-xl shadow-sm p-6 mb-6">
          <div className="flex flex-wrap gap-4 items-end">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">From</label>
              <input
                type="date"
                value={start}
                onChange={(e) => setStart(e.target.value)}
                className="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">To</label>
              <input
                type="date"
                value={end}
                onChange={(e) => setEnd(e.target.value)}
                className="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              onClick={fetchReport}
              disabled={loading}
              className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? 'Loading...' : 'Generate Report'}
            </button>
            <button
              onClick={exportExcel}
              className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700"
            >
              Export Excel
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">
            {error}
          </div>
        )}

        {report && (
          <div className="bg-white rounded-xl shadow-sm">
            <div className="p-4 font-medium text-gray-700 border-b">
              Attendance Report ({report.length} records)
            </div>
            {report.length === 0 ? (
              <div className="p-8 text-center text-gray-500">No data found for the selected period.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Employee</th>
                      <th className="text-right px-6 py-3 text-sm font-medium text-gray-500">Present Days</th>
                      <th className="text-right px-6 py-3 text-sm font-medium text-gray-500">Absent Days</th>
                      <th className="text-right px-6 py-3 text-sm font-medium text-gray-500">Late Days</th>
                      <th className="text-right px-6 py-3 text-sm font-medium text-gray-500">Total Hours</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {report.map((row) => (
                      <tr key={row.employeeId} className="hover:bg-gray-50">
                        <td className="px-6 py-4 text-sm font-medium text-gray-900">{row.employeeName}</td>
                        <td className="px-6 py-4 text-sm text-right text-green-600">{row.presentDays}</td>
                        <td className="px-6 py-4 text-sm text-right text-red-600">{row.absentDays}</td>
                        <td className="px-6 py-4 text-sm text-right text-yellow-600">{row.lateDays}</td>
                        <td className="px-6 py-4 text-sm text-right text-gray-600">{row.totalHoursWorked.toFixed(1)}</td>
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
