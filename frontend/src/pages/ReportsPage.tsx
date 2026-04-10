import { useState } from 'react'
import Layout from '../components/Layout'
import client from '../api/client'

export default function ReportsPage() {
  const [start, setStart] = useState(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0])
  const [end, setEnd] = useState(new Date().toISOString().split('T')[0])
  const [report, setReport] = useState<object[] | null>(null)
  const [loading, setLoading] = useState(false)

  const fetchReport = async () => {
    setLoading(true)
    try {
      const res = await client.get(`/reports/attendance?start=${start}&end=${end}`)
      setReport(res.data?.data || [])
    } catch {
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
          <div className="flex gap-4 items-end">
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

        {report && (
          <div className="bg-white rounded-xl shadow-sm">
            <div className="p-4 font-medium text-gray-700 border-b">Attendance Report ({report.length} records)</div>
            <div className="p-4 text-sm text-gray-500">
              {report.length === 0 ? 'No data found for the selected period.' : `Found ${report.length} records.`}
            </div>
          </div>
        )}
      </div>
    </Layout>
  )
}
