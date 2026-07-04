import { useEffect, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { attendanceApi } from '../api/attendanceApi.ts'
import { positionApi } from '../api/positionApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { employeeApi } from '../api/employeeApi.ts'
import { useAttendanceReportStore } from '../store/attendanceReportStore.ts'
import { t } from '../i18n/index.ts'
import { Position, Department } from '../types'

const SHIFT_TABS = [
  { label: 'Sərbəst növbə', value: 'FIRST_ENTRY' },
  { label: 'Standart növbə', value: 'STANDARD_SHIFT' },
  { label: 'Dəqiq vaxt növbəsi', value: 'LATE_SHIFT' },
]

export default function ReportsPage() {
  const {
    rows, filters, page, totalPages, loading, totalElements, setFilters, setPage, fetchReport,
  } = useAttendanceReportStore()

  const [positions, setPositions] = useState<Position[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [areas, setAreas] = useState<string[]>([])

  useEffect(() => {
    void fetchReport()
  }, [filters, page, fetchReport])

  useEffect(() => {
    const loadLookups = async () => {
      const [posRes, deptRes, areaRes] = await Promise.allSettled([
        positionApi.getAll(),
        departmentApi.getAll(),
        employeeApi.getDistinctAreas(),
      ])
      if (posRes.status === 'fulfilled') setPositions(posRes.value.data.data ?? [])
      if (deptRes.status === 'fulfilled') setDepartments(deptRes.value.data.data ?? [])
      if (areaRes.status === 'fulfilled') setAreas(areaRes.value.data.data ?? [])
    }
    void loadLookups()
  }, [])

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
      <div className="p-4 sm:p-8 space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-bold text-gray-900">Davamiyyət hesabatları</h1>
          <button onClick={exportExcel} className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm font-medium">{t('reports.excelExport')}</button>
        </div>

        <div className="flex flex-wrap gap-2 bg-white p-2 rounded-xl shadow-sm">
          {SHIFT_TABS.map((tab) => (
            <button
              key={tab.value}
              onClick={() => setFilters({ ...filters, shiftType: tab.value })}
              className={`px-3 py-2 rounded-lg text-sm font-medium ${filters.shiftType === tab.value ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-700'}`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="bg-white rounded-xl shadow-sm p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
          <input type="date" value={filters.start} onChange={(e) => updateFilter('start', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          <input type="date" value={filters.end} onChange={(e) => updateFilter('end', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          <input placeholder="ID axtar" value={filters.employeeId ?? ''} onChange={(e) => updateFilter('employeeId', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          <input placeholder="Ad soyad" value={filters.name ?? ''} onChange={(e) => updateFilter('name', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          <input placeholder="FIN" value={filters.fin ?? ''} onChange={(e) => updateFilter('fin', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />

          <select
            value={filters.position ?? ''}
            onChange={(e) => updateFilter('position', e.target.value)}
            className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full"
          >
            <option value="">Hamısı (Vəzifə)</option>
            {positions.map((p) => (
              <option key={p.id} value={p.positionName}>{p.positionName}</option>
            ))}
          </select>

          <select
            value={filters.department ?? ''}
            onChange={(e) => updateFilter('department', e.target.value)}
            className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full"
          >
            <option value="">Hamısı (Departament)</option>
            {departments.map((d) => (
              <option key={d.id} value={d.departmentName}>{d.departmentName}</option>
            ))}
          </select>

          <select
            value={filters.area ?? ''}
            onChange={(e) => updateFilter('area', e.target.value)}
            className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full"
          >
            <option value="">Hamısı (Ərazi)</option>
            {areas.map((a) => (
              <option key={a} value={a}>{a}</option>
            ))}
          </select>
        </div>

        <div className="bg-white rounded-xl shadow-sm overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {['ID', 'Şəkil', 'Ad Soyad', 'FIN', 'Depart', 'Vəzifə', 'Area', 'Tarix', 'Giriş'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={9} className="px-4 py-6 text-sm text-gray-500 text-center">Yüklənir...</td></tr>
              ) : rows.length === 0 ? (
                <tr><td colSpan={9} className="px-4 py-6 text-sm text-gray-500 text-center">Məlumat tapılmadı</td></tr>
              ) : rows.map((row) => (
                <tr key={`${row.employeePk}-${row.date}-${row.checkInTime}`} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-3 whitespace-nowrap text-gray-700">{row.employeeId}</td>
                  <td className="px-4 py-3">
                    {row.photoUrl ? (
                      <img src={row.photoUrl} alt={row.fullName} className="w-8 h-8 rounded-full object-cover" />
                    ) : (
                      <div className="w-8 h-8 rounded-full bg-pink-200" />
                    )}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap font-medium text-gray-900">{row.fullName}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.fin ?? '-'}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.department ?? '-'}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.position ?? '-'}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.area ?? '-'}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.date}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.checkInTime ? new Date(row.checkInTime).toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit' }) : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="text-sm text-gray-500">{totalElements} nəticə</span>
          <div className="flex items-center gap-2">
            <button disabled={page <= 0} onClick={() => setPage(page - 1)} className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm disabled:opacity-50 hover:bg-gray-50">{t('reports.prev')}</button>
            <span className="px-2 py-1 text-sm text-gray-600">{page + 1} / {Math.max(totalPages, 1)}</span>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)} className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm disabled:opacity-50 hover:bg-gray-50">{t('reports.next')}</button>
          </div>
        </div>
      </div>
    </Layout>
  )
}
