import { useEffect, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { timetableApi } from '../api/timetableApi.ts'
import { ApiResponse, Timetable } from '../types'

export default function WorkSchedulePage() {
  const [timetables, setTimetables] = useState<Timetable[]>([])
  const [loading, setLoading] = useState(false)
  const [name, setName] = useState('')
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('18:00')

  const load = async () => {
    setLoading(true)
    try {
      const res = await timetableApi.getAll()
      const payload: ApiResponse<Timetable[]> = res.data
      setTimetables(payload?.data ?? [])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const createTimetable = async () => {
    if (!name.trim()) return
    await timetableApi.create({ name: name.trim(), startTime, endTime, crossesMidnight: false, allowedLateMinutes: 0, allowedEarlyLeaveMinutes: 0 })
    setName('')
    await load()
  }

  return (
    <Layout>
      <div className="p-8 space-y-6">
        <h1 className="text-2xl font-bold text-gray-900">İş qrafiki</h1>
        <div className="bg-white rounded-xl shadow-sm p-4 grid grid-cols-1 md:grid-cols-4 gap-3">
          <input value={name} onChange={(e) => setName(e.target.value)} className="border rounded-lg px-3 py-2" placeholder="Qrafik adı" />
          <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} className="border rounded-lg px-3 py-2" />
          <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} className="border rounded-lg px-3 py-2" />
          <button onClick={createTimetable} className="bg-indigo-600 text-white rounded-lg px-4 py-2">Əlavə et</button>
        </div>

        <div className="bg-white rounded-xl shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-4 py-3 text-sm text-gray-500">Ad</th>
                <th className="text-left px-4 py-3 text-sm text-gray-500">Başlanğıc</th>
                <th className="text-left px-4 py-3 text-sm text-gray-500">Bitmə</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td className="px-4 py-4 text-sm text-gray-500" colSpan={3}>Yüklənir...</td></tr>
              ) : timetables.length === 0 ? (
                <tr><td className="px-4 py-4 text-sm text-gray-500" colSpan={3}>Məlumat yoxdur</td></tr>
              ) : timetables.map((row) => (
                <tr key={row.id} className="border-t">
                  <td className="px-4 py-3">{row.name}</td>
                  <td className="px-4 py-3">{row.startTime}</td>
                  <td className="px-4 py-3">{row.endTime}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Layout>
  )
}
