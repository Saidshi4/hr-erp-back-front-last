import { FormEvent, useEffect, useMemo, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { Branch, Department, Employee, HolidayPermission } from '../types'
import { holidayPermissionApi } from '../api/holidayPermissionApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { branchApi } from '../api/branchApi.ts'
import { employeeApi } from '../api/employeeApi.ts'

type Scope = 'COMPANY' | 'DEPARTMENT' | 'BRANCH' | 'EMPLOYEE'

interface HolidayForm {
  name: string
  description: string
  startDate: string
  endDate: string
  applyScope: Scope
  targetIds: number[]
  employeeIds: number[]
  status: 'ACTIVE' | 'INACTIVE'
}

const emptyForm: HolidayForm = {
  name: '',
  description: '',
  startDate: '',
  endDate: '',
  applyScope: 'COMPANY',
  targetIds: [],
  employeeIds: [],
  status: 'ACTIVE',
}

const scopeLabels: Record<Scope, string> = {
  COMPANY: 'Şirkət',
  DEPARTMENT: 'Departament',
  BRANCH: 'Filial',
  EMPLOYEE: 'İşçi',
}

const normalizeScope = (value?: string): Scope => {
  const normalized = (value ?? '').toUpperCase()
  if (normalized === 'DEPARTMENT' || normalized === 'BRANCH' || normalized === 'EMPLOYEE') {
    return normalized
  }
  return 'COMPANY'
}

export default function HolidayPermissionsPage() {
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const [items, setItems] = useState<HolidayPermission[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [branches, setBranches] = useState<Branch[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])

  const [showModal, setShowModal] = useState(false)
  const [editing, setEditing] = useState<HolidayPermission | null>(null)
  const [deleting, setDeleting] = useState<HolidayPermission | null>(null)
  const [form, setForm] = useState<HolidayForm>(emptyForm)

  const targetOptions = useMemo(() => {
    if (form.applyScope === 'DEPARTMENT') {
      return departments.map(item => ({ id: item.id, label: item.departmentName }))
    }
    if (form.applyScope === 'BRANCH') {
      return branches.map(item => ({ id: item.id, label: item.name }))
    }
    if (form.applyScope === 'EMPLOYEE') {
      return employees.map(item => ({ id: item.id, label: `${item.employeeId} - ${item.firstName} ${item.lastName}`.trim() }))
    }
    return []
  }, [branches, departments, employees, form.applyScope])

  const selectedTargets = form.applyScope === 'EMPLOYEE' ? form.employeeIds : form.targetIds

  const fetchData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [holidayRes, departmentRes, branchRes, employeeRes] = await Promise.all([
        holidayPermissionApi.getAll(),
        departmentApi.getAll(),
        branchApi.getAll(),
        employeeApi.getAll(0, 500),
      ])

      setItems(holidayRes.data.data ?? [])
      setDepartments(departmentRes.data.data ?? [])
      setBranches(branchRes.data.data ?? [])
      setEmployees(employeeRes.data.content ?? [])
    } catch (e: any) {
      setError(e?.response?.data?.message || e?.message || 'Bayram icazələri yüklənmədi')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void fetchData()
  }, [])

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm)
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (item: HolidayPermission) => {
    const scope = normalizeScope(item.applyScope)
    setEditing(item)
    setForm({
      name: item.name,
      description: item.description ?? '',
      startDate: item.startDate,
      endDate: item.endDate,
      applyScope: scope,
      targetIds: scope === 'DEPARTMENT' || scope === 'BRANCH' ? item.targetIds ?? [] : [],
      employeeIds: scope === 'EMPLOYEE' ? item.employeeIds ?? [] : [],
      status: item.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
    })
    setFormError(null)
    setShowModal(true)
  }

  const closeModal = () => {
    setShowModal(false)
    setEditing(null)
    setForm(emptyForm)
    setFormError(null)
  }

  const toggleTarget = (id: number) => {
    setForm((prev) => {
      const selected = prev.applyScope === 'EMPLOYEE' ? prev.employeeIds : prev.targetIds
      const next = selected.includes(id) ? selected.filter(item => item !== id) : [...selected, id]
      if (prev.applyScope === 'EMPLOYEE') {
        return { ...prev, employeeIds: next }
      }
      return { ...prev, targetIds: next }
    })
  }

  const validate = () => {
    if (!form.name.trim()) {
      setFormError('Ad mütləqdir')
      return false
    }
    if (!form.startDate || !form.endDate) {
      setFormError('Başlanğıc və bitiş tarixləri mütləqdir')
      return false
    }
    if (form.endDate < form.startDate) {
      setFormError('Bitiş tarixi başlanğıcdan əvvəl ola bilməz')
      return false
    }
    if ((form.applyScope === 'DEPARTMENT' || form.applyScope === 'BRANCH') && form.targetIds.length === 0) {
      setFormError('Hədəf seçimi mütləqdir')
      return false
    }
    if (form.applyScope === 'EMPLOYEE' && form.employeeIds.length === 0) {
      setFormError('İşçi seçimi mütləqdir')
      return false
    }
    setFormError(null)
    return true
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!validate()) return

    setSaving(true)
    const payload: Partial<HolidayPermission> = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      startDate: form.startDate,
      endDate: form.endDate,
      applyScope: form.applyScope,
      targetIds: form.applyScope === 'DEPARTMENT' || form.applyScope === 'BRANCH' ? form.targetIds : [],
      employeeIds: form.applyScope === 'EMPLOYEE' ? form.employeeIds : [],
      status: form.status,
    }

    try {
      if (editing) {
        await holidayPermissionApi.update(editing.id, payload)
      } else {
        await holidayPermissionApi.create(payload)
      }
      closeModal()
      await fetchData()
    } catch (e: any) {
      setFormError(e?.response?.data?.message || e?.message || 'Yadda saxlama mümkün olmadı')
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async () => {
    if (!deleting) return
    try {
      await holidayPermissionApi.remove(deleting.id)
      setDeleting(null)
      await fetchData()
    } catch (e: any) {
      setError(e?.response?.data?.message || e?.message || 'Silmə mümkün olmadı')
    }
  }

  return (
    <Layout>
      <div className="p-8 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Bayram icazəsi</h1>
            <p className="text-sm text-gray-500">Bayram adını və tarix aralığını idarə edin</p>
          </div>
          <button onClick={openCreate} className="px-4 py-2 rounded-lg text-white text-sm" style={{ background: '#a855f7' }}>
            + Bayram icazəsi əlavə et
          </button>
        </div>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="bg-white border rounded-xl overflow-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-3 py-2 text-left">Ad</th>
                <th className="px-3 py-2 text-left">Başlanğıc</th>
                <th className="px-3 py-2 text-left">Bitiş</th>
                <th className="px-3 py-2 text-left">Sahə</th>
                <th className="px-3 py-2 text-left">Status</th>
                <th className="px-3 py-2 text-right">Əməliyyat</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={6} className="px-3 py-8 text-center text-gray-500">Yüklənir...</td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={6} className="px-3 py-8 text-center text-gray-500">Məlumat yoxdur</td></tr>
              ) : items.map((item) => (
                <tr key={item.id} className="border-t">
                  <td className="px-3 py-2">{item.name}</td>
                  <td className="px-3 py-2">{item.startDate}</td>
                  <td className="px-3 py-2">{item.endDate}</td>
                  <td className="px-3 py-2">{scopeLabels[normalizeScope(item.applyScope)]}</td>
                  <td className="px-3 py-2">{item.status}</td>
                  <td className="px-3 py-2 text-right space-x-2">
                    <button onClick={() => openEdit(item)} className="text-purple-600">Redaktə et</button>
                    <button onClick={() => setDeleting(item)} className="text-red-600">Sil</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {showModal && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl w-full max-w-2xl overflow-hidden">
              <div className="px-6 py-4 text-white" style={{ background: '#1e2a4a' }}>
                <h2 className="font-semibold">{editing ? 'Bayram icazəsini redaktə et' : 'Bayram icazəsi əlavə et'}</h2>
              </div>
              <form onSubmit={onSubmit} className="p-6 space-y-4">
                {formError && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{formError}</div>}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <label className="text-sm">Ad
                    <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="mt-1 w-full border rounded-lg px-3 py-2" />
                  </label>
                  <label className="text-sm">Status
                    <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as 'ACTIVE' | 'INACTIVE' })} className="mt-1 w-full border rounded-lg px-3 py-2">
                      <option value="ACTIVE">Aktiv</option>
                      <option value="INACTIVE">Deaktiv</option>
                    </select>
                  </label>
                  <label className="text-sm">Başlanğıc
                    <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} className="mt-1 w-full border rounded-lg px-3 py-2" />
                  </label>
                  <label className="text-sm">Bitiş
                    <input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} className="mt-1 w-full border rounded-lg px-3 py-2" />
                  </label>
                  <label className="text-sm md:col-span-2">Tətbiq sahəsi
                    <select value={form.applyScope} onChange={(e) => setForm({ ...form, applyScope: e.target.value as Scope, targetIds: [], employeeIds: [] })} className="mt-1 w-full border rounded-lg px-3 py-2">
                      <option value="COMPANY">Şirkət</option>
                      <option value="DEPARTMENT">Departament</option>
                      <option value="BRANCH">Filial</option>
                      <option value="EMPLOYEE">Əməkdaş</option>
                    </select>
                  </label>
                </div>

                {(form.applyScope === 'DEPARTMENT' || form.applyScope === 'BRANCH' || form.applyScope === 'EMPLOYEE') && (
                  <div className="border rounded-lg p-3 max-h-40 overflow-auto">
                    {targetOptions.map((option) => (
                      <label key={option.id} className="flex items-center gap-2 text-sm py-1">
                        <input
                          type="checkbox"
                          checked={selectedTargets.includes(option.id)}
                          onChange={() => toggleTarget(option.id)}
                        />
                        <span>{option.label}</span>
                      </label>
                    ))}
                    {targetOptions.length === 0 && <p className="text-sm text-gray-500">Seçim üçün məlumat yoxdur</p>}
                  </div>
                )}

                <label className="text-sm block">Açıqlama
                  <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="mt-1 w-full border rounded-lg px-3 py-2" rows={3} />
                </label>

                <div className="flex justify-end gap-2">
                  <button type="button" onClick={closeModal} className="px-4 py-2 border rounded-lg">Ləğv et</button>
                  <button type="submit" disabled={saving} className="px-4 py-2 rounded-lg text-white" style={{ background: '#a855f7' }}>
                    {saving ? 'Yadda saxlanılır...' : 'Yadda saxla'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {deleting && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl w-full max-w-md p-6">
              <h3 className="font-semibold text-gray-900">Silmək istədiyinizə əminsiniz?</h3>
              <p className="text-sm text-gray-500 mt-2">{deleting.name}</p>
              <div className="mt-5 flex justify-end gap-2">
                <button onClick={() => setDeleting(null)} className="px-4 py-2 border rounded-lg">Ləğv et</button>
                <button onClick={() => void onDelete()} className="px-4 py-2 rounded-lg bg-red-600 text-white">Sil</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </Layout>
  )
}
