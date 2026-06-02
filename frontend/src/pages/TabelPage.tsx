import { useEffect, useMemo, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { branchApi } from '../api/branchApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { tabelApi } from '../api/tabelApi.ts'
import { Branch, Department, TabelMonthlyData } from '../types'
import { useDebounce } from '../hooks/useSearch.ts'

const monthOptions = [
  'Yanvar',
  'Fevral',
  'Mart',
  'Aprel',
  'May',
  'İyun',
  'İyul',
  'Avqust',
  'Sentyabr',
  'Oktyabr',
  'Noyabr',
  'Dekabr',
]

export default function TabelPage() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [branchId, setBranchId] = useState<number | ''>('')
  const [departmentId, setDepartmentId] = useState<number | ''>('')
  const [search, setSearch] = useState('')
  const [branches, setBranches] = useState<Branch[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [data, setData] = useState<TabelMonthlyData | null>(null)
  const [loading, setLoading] = useState(false)
  const debouncedSearch = useDebounce(search, 300)

  const years = useMemo(() => {
    const currentYear = new Date().getFullYear()
    return [currentYear - 2, currentYear - 1, currentYear, currentYear + 1]
  }, [])

  useEffect(() => {
    const loadBranches = async () => {
      const res = await branchApi.getAll()
      setBranches(res.data.data ?? [])
    }

    void loadBranches()
  }, [])

  useEffect(() => {
    const loadDepartments = async () => {
      const res = await departmentApi.getAll(branchId === '' ? undefined : branchId)
      setDepartments(res.data.data ?? [])
    }

    if (branchId === '') {
      setDepartmentId('')
    }
    void loadDepartments()
  }, [branchId])

  useEffect(() => {
    const loadTabel = async () => {
      setLoading(true)
      try {
        const res = await tabelApi.getMonthly({
          year,
          month,
          branchId: branchId === '' ? undefined : branchId,
          departmentId: departmentId === '' ? undefined : departmentId,
          search: debouncedSearch || undefined,
        })
        setData(res.data.data)
      } finally {
        setLoading(false)
      }
    }

    void loadTabel()
  }, [year, month, branchId, departmentId, debouncedSearch])

  const handleExport = async () => {
    const res = await tabelApi.exportExcel({
      year,
      month,
      branchId: branchId === '' ? undefined : branchId,
      departmentId: departmentId === '' ? undefined : departmentId,
      search: debouncedSearch || undefined,
    })

    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const href = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = href
    anchor.download = `tabel-${year}-${String(month).padStart(2, '0')}.xlsx`
    anchor.click()
    URL.revokeObjectURL(href)
  }

  return (
    <Layout>
      <div className="p-6 lg:p-8">
        <div className="grid gap-6 lg:grid-cols-[280px,1fr]">
          <aside className="space-y-4 rounded-2xl bg-white p-4 shadow-sm h-fit">
            <h2 className="text-sm font-semibold text-slate-700">Arxiv</h2>

            <div>
              <label className="mb-1 block text-xs text-slate-500">İl</label>
              <select value={year} onChange={(e) => setYear(Number(e.target.value))} className="w-full rounded-lg border px-3 py-2">
                {years.map((item) => (
                  <option key={item} value={item}>{item}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs text-slate-500">Ay</label>
              <select value={month} onChange={(e) => setMonth(Number(e.target.value))} className="w-full rounded-lg border px-3 py-2">
                {monthOptions.map((label, index) => (
                  <option key={label} value={index + 1}>{label}</option>
                ))}
              </select>
            </div>

            <div className="rounded-xl border border-indigo-100 bg-indigo-50 p-3">
              <p className="text-sm font-semibold text-indigo-700">{monthOptions[month - 1]} {year}</p>
              <p className="mt-1 text-xs text-indigo-600">İşçi sayı: {data?.employees ?? 0}</p>
              <p className="text-xs text-indigo-600">Gün sayı: {data?.daysInMonth ?? 0}</p>
            </div>
          </aside>

          <section className="space-y-4 min-w-0">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h1 className="text-2xl font-bold text-slate-900">Tabel</h1>
                <p className="mt-1 text-sm text-slate-500">FIN kodu, günlük iş saatları və aylıq tabel arxivi</p>
              </div>
              <button onClick={handleExport} className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700">
                Excel Export
              </button>
            </div>

            <div className="grid gap-3 rounded-2xl bg-white p-4 shadow-sm md:grid-cols-3">
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Ad və ya FIN axtar"
                className="rounded-lg border px-3 py-2"
              />
              <select value={branchId} onChange={(e) => setBranchId(e.target.value ? Number(e.target.value) : '')} className="rounded-lg border px-3 py-2">
                <option value="">Bütün filiallar</option>
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>{branch.name}</option>
                ))}
              </select>
              <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value ? Number(e.target.value) : '')} className="rounded-lg border px-3 py-2">
                <option value="">Bütün departamentlər</option>
                {departments.map((department) => (
                  <option key={department.id} value={department.id}>{department.departmentName}</option>
                ))}
              </select>
            </div>

            <div className="overflow-x-auto rounded-2xl bg-white shadow-sm">
              <table className="min-w-max border-collapse text-sm">
                <thead>
                  <tr className="bg-slate-50 text-xs uppercase text-slate-500">
                    <th className="sticky left-0 top-0 z-30 min-w-[60px] border-b bg-slate-50 px-3 py-3 text-left">s/s</th>
                    <th className="sticky left-[60px] top-0 z-30 min-w-[120px] border-b bg-slate-50 px-3 py-3 text-left">FIN kod</th>
                    <th className="sticky left-[180px] top-0 z-30 min-w-[280px] border-b bg-slate-50 px-3 py-3 text-left">Soyadı, adı, atasının adı</th>
                    <th className="sticky left-[460px] top-0 z-30 min-w-[180px] border-b bg-slate-50 px-3 py-3 text-left">Vəzifəsi</th>
                    {Array.from({ length: data?.daysInMonth ?? 0 }, (_, day) => (
                      <th key={day + 1} className="sticky top-0 min-w-[70px] border-b bg-slate-50 px-2 py-3 text-center">{day + 1}</th>
                    ))}
                    <th className="sticky top-0 min-w-[130px] border-b bg-slate-50 px-3 py-3 text-center">İş günlərinin sayı</th>
                    <th className="sticky top-0 min-w-[140px] border-b bg-slate-50 px-3 py-3 text-center">İş saatlarının cəmi</th>
                  </tr>
                </thead>
                <tbody>
                  {loading ? (
                    <tr><td colSpan={(data?.daysInMonth ?? 0) + 6} className="px-4 py-8 text-center text-slate-500">Yüklənir...</td></tr>
                  ) : (data?.rows.length ?? 0) === 0 ? (
                    <tr><td colSpan={(data?.daysInMonth ?? 0) + 6} className="px-4 py-8 text-center text-slate-400">Məlumat tapılmadı</td></tr>
                  ) : data?.rows.map((row, index) => (
                    <tr key={row.employeePk} className="border-b border-slate-100 text-slate-700">
                      <td className="sticky left-0 z-20 bg-white px-3 py-2">{index + 1}</td>
                      <td className="sticky left-[60px] z-20 bg-white px-3 py-2">{row.fin ?? '-'}</td>
                      <td className="sticky left-[180px] z-20 bg-white px-3 py-2">{row.fullName}</td>
                      <td className="sticky left-[460px] z-20 bg-white px-3 py-2">{row.position}</td>
                      {Array.from({ length: data.daysInMonth }, (_, day) => {
                        const value = row.daily[String(day + 1)]
                        const isAbsent = value === 0
                        const isCode = value === 'Q/I'
                        const isEmpty = value === null || value === undefined

                        return (
                          <td key={day + 1} className={`px-2 py-2 text-center ${isAbsent ? 'font-semibold text-red-600' : isCode ? 'font-medium text-amber-700' : isEmpty ? 'text-slate-300' : ''}`}>
                            {typeof value === 'number' ? value.toFixed(2) : isEmpty ? '' : value}
                          </td>
                        )
                      })}
                      <td className="px-3 py-2 text-center font-medium">{row.workingDays}</td>
                      <td className="px-3 py-2 text-center font-medium">{row.totalHours.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </div>
    </Layout>
  )
}
