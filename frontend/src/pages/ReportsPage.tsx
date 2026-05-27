import { useEffect } from 'react'
import Layout from '../components/Layout.tsx'
import { attendanceApi } from '../api/attendanceApi.ts'
import { useAttendanceReportStore } from '../store/attendanceReportStore.ts'

const SHIFT_TABS = [
  { label: 'Sərbəst növbə', value: 'FIRST_ENTRY' },
  { label: 'Standart növbə', value: 'STANDARD_SHIFT' },
  { label: 'Dəqiq vaxt növbəsi', value: 'LATE_SHIFT' },
]

export default function ReportsPage() {
  const {
    rows, filters, page, totalPages, loading, totalElements, setFilters, setPage, fetchReport,
  } = useAttendanceReportStore()

  useEffect(() => {
    void fetchReport()
  }, [filters, page, fetchReport])

  const updateFilter = (name: string, value: string) => {
    setFilters({ ...filters, [name]: value })
  }

  const exportExcel = async () => {
    const res = await attendanceApi.exportExcel(filters)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const href = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = href
    a.download = 'attendance_reports.xlsx'
    a.click()
    URL.revokeObjectURL(href)
  }

  return (
    <Layout>
      <div className="p-8 space-y-5">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold text-gray-900">Davamiyyət hesabatları</h1>
          <button onClick={exportExcel} className="bg-emerald-600 text-white px-4 py-2 rounded-lg">Excel Export</button>
        </div>

        <div className="flex gap-2 bg-white p-2 rounded-xl shadow-sm">
          {SHIFT_TABS.map((tab) => (
            <button
              key={tab.value}
              onClick={() => setFilters({ ...filters, shiftType: tab.value })}
              className={`px-4 py-2 rounded-lg text-sm ${filters.shiftType === tab.value ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-700'}`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="bg-white rounded-xl shadow-sm p-4 grid grid-cols-1 md:grid-cols-4 lg:grid-cols-5 gap-3">
          <input type="date" value={filters.start} onChange={(e) => updateFilter('start', e.target.value)} className="border rounded-lg px-3 py-2" />
          <input type="date" value={filters.end} onChange={(e) => updateFilter('end', e.target.value)} className="border rounded-lg px-3 py-2" />
          <input placeholder="ID axtar" value={filters.employeeId ?? ''} onChange={(e) => updateFilter('employeeId', e.target.value)} className="border rounded-lg px-3 py-2" />
          <input placeholder="Ad soyad" value={filters.name ?? ''} onChange={(e) => updateFilter('name', e.target.value)} className="border rounded-lg px-3 py-2" />
          <input placeholder="FIN" value={filters.fin ?? ''} onChange={(e) => updateFilter('fin', e.target.value)} className="border rounded-lg px-3 py-2" />
          <input placeholder="Vəzifə" value={filters.position ?? ''} onChange={(e) => updateFilter('position', e.target.value)} className="border rounded-lg px-3 py-2" />
          <input placeholder="Departament" value={filters.department ?? ''} onChange={(e) => updateFilter('department', e.target.value)} className="border rounded-lg px-3 py-2" />
          <input placeholder="Area" value={filters.area ?? ''} onChange={(e) => updateFilter('area', e.target.value)} className="border rounded-lg px-3 py-2" />
        </div>

        <div className="bg-white rounded-xl shadow-sm overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                {['ID', 'Şəkil', 'Ad Soyad', 'FIN', 'Depart', 'Vəzifə', 'Area', 'Tarix', 'Giriş'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-sm text-gray-500">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={9} className="px-4 py-6 text-sm text-gray-500">Yüklənir...</td></tr>
              ) : rows.length === 0 ? (
                <tr><td colSpan={9} className="px-4 py-6 text-sm text-gray-500">Məlumat tapılmadı</td></tr>
              ) : rows.map((row) => (
                <tr key={`${row.employeePk}-${row.date}-${row.checkInTime}`} className="border-t">
                  <td className="px-4 py-3">{row.employeeId}</td>
                  <td className="px-4 py-3">
                    {row.photoUrl ? (
                      <img src={row.photoUrl} alt={row.fullName} className="w-8 h-8 rounded-full object-cover" />
                    ) : (
                      <div className="w-8 h-8 rounded-full bg-pink-200" />
                    )}
                  </td>
                  <td className="px-4 py-3">{row.fullName}</td>
                  <td className="px-4 py-3">{row.fin ?? '-'}</td>
                  <td className="px-4 py-3">{row.department ?? '-'}</td>
                  <td className="px-4 py-3">{row.position ?? '-'}</td>
                  <td className="px-4 py-3">{row.area ?? '-'}</td>
                  <td className="px-4 py-3">{row.date}</td>
                  <td className="px-4 py-3">{row.checkInTime ? new Date(row.checkInTime).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }) : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-sm text-gray-500">{totalElements} nəticə</span>
          <div className="flex gap-2">
            <button disabled={page <= 0} onClick={() => setPage(page - 1)} className="px-3 py-1 border rounded disabled:opacity-50">Prev</button>
            <span className="px-2 py-1 text-sm">{page + 1} / {Math.max(totalPages, 1)}</span>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)} className="px-3 py-1 border rounded disabled:opacity-50">Next</button>
          </div>
        </div>
      </div>
    </Layout>
  )
}
