import { FormEvent, useEffect, useMemo, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { AnnualLeaveBalance, Employee } from '../types'
import { annualLeaveApi } from '../api/annualLeaveApi.ts'
import { employeeApi } from '../api/employeeApi.ts'

interface LeaveForm {
  entitlementDays: number
  usedDays: number
  carryoverDays: number
  status: 'ACTIVE' | 'INACTIVE'
}

const emptyForm: LeaveForm = {
  entitlementDays: 0,
  usedDays: 0,
  carryoverDays: 0,
  status: 'ACTIVE',
}

const calcRemaining = (entitlement: number, carryover: number, used: number) => Math.max(entitlement + carryover - used, 0)

export default function AnnualLeavePage() {
  const [year, setYear] = useState(new Date().getFullYear())
  const [employeeFilter, setEmployeeFilter] = useState<string>('')
  const [employees, setEmployees] = useState<Employee[]>([])
  const [items, setItems] = useState<AnnualLeaveBalance[]>([])

  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const [showModal, setShowModal] = useState(false)
  const [editing, setEditing] = useState<AnnualLeaveBalance | null>(null)
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<number | ''>('')
  const [form, setForm] = useState<LeaveForm>(emptyForm)

  const remainingPreview = useMemo(() => calcRemaining(form.entitlementDays, form.carryoverDays, form.usedDays), [form])

  const loadEmployees = async () => {
    try {
      const res = await employeeApi.getAll(0, 500)
      setEmployees(res.data.content ?? [])
    } catch {
      setEmployees([])
    }
  }

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await annualLeaveApi.getAll({ year, employeeId: employeeFilter ? Number(employeeFilter) : undefined })
      setItems(res.data.data ?? [])
    } catch (e: any) {
      setError(e?.response?.data?.message || e?.message || 'Mezuniyyətlər yüklənmədi')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadEmployees()
  }, [])

  useEffect(() => {
    void loadData()
  }, [year, employeeFilter])

  const openCreate = () => {
    setEditing(null)
    setSelectedEmployeeId(employeeFilter ? Number(employeeFilter) : '')
    setForm(emptyForm)
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (item: AnnualLeaveBalance) => {
    setEditing(item)
    setSelectedEmployeeId(item.employeeId)
    setForm({
      entitlementDays: item.entitlementDays,
      usedDays: item.usedDays,
      carryoverDays: item.carryoverDays,
      status: item.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
    })
    setFormError(null)
    setShowModal(true)
  }

  const closeModal = () => {
    setShowModal(false)
    setEditing(null)
    setSelectedEmployeeId('')
    setForm(emptyForm)
    setFormError(null)
  }

  const validate = () => {
    if (!selectedEmployeeId) {
      setFormError('İşçi seçimi mütləqdir')
      return false
    }
    if (form.entitlementDays < 0 || form.usedDays < 0 || form.carryoverDays < 0) {
      setFormError('Mənfi dəyər daxil etmək olmaz')
      return false
    }
    if (form.usedDays > form.entitlementDays + form.carryoverDays) {
      setFormError('Used gün entitlement + carryover-dan çox ola bilməz')
      return false
    }
    setFormError(null)
    return true
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!validate()) return

    setSaving(true)
    const payload: Partial<AnnualLeaveBalance> = {
      employeeId: Number(selectedEmployeeId),
      year,
      entitlementDays: form.entitlementDays,
      usedDays: form.usedDays,
      carryoverDays: form.carryoverDays,
      remainingDays: calcRemaining(form.entitlementDays, form.carryoverDays, form.usedDays),
      status: form.status,
    }

    try {
      if (editing) {
        await annualLeaveApi.update(editing.id, payload)
      } else {
        await annualLeaveApi.create(payload)
      }
      closeModal()
      await loadData()
    } catch (e: any) {
      setFormError(e?.response?.data?.message || e?.message || 'Yadda saxlama mümkün olmadı')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Layout>
      <div className="p-8 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Mezuniyyətlər</h1>
            <p className="text-sm text-gray-500">İllik entitlement, used, carryover və remaining idarəetməsi</p>
          </div>
          <button onClick={openCreate} className="px-4 py-2 rounded-lg text-white text-sm" style={{ background: '#a855f7' }}>
            + Mezuniyyət əlavə et
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 bg-white border rounded-xl p-4">
          <label className="text-sm">İl
            <input type="number" value={year} onChange={(e) => setYear(Number(e.target.value) || new Date().getFullYear())} className="mt-1 w-full border rounded-lg px-3 py-2" />
          </label>
          <label className="text-sm">İşçi (opsional)
            <select value={employeeFilter} onChange={(e) => setEmployeeFilter(e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2">
              <option value="">Hamısı</option>
              {employees.map((employee) => (
                <option key={employee.id} value={employee.id}>{employee.employeeId} - {employee.firstName} {employee.lastName}</option>
              ))}
            </select>
          </label>
        </div>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="bg-white border rounded-xl overflow-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-3 py-2 text-left">İşçi</th>
                <th className="px-3 py-2 text-left">Hüquq</th>
                <th className="px-3 py-2 text-left">İstifadə</th>
                <th className="px-3 py-2 text-left">Köçürülən</th>
                <th className="px-3 py-2 text-left">Qalıq</th>
                <th className="px-3 py-2 text-left">Status</th>
                <th className="px-3 py-2 text-right">Əməliyyat</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={7} className="px-3 py-8 text-center text-gray-500">Yüklənir...</td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={7} className="px-3 py-8 text-center text-gray-500">Məlumat yoxdur</td></tr>
              ) : items.map((item) => (
                <tr key={item.id} className="border-t">
                  <td className="px-3 py-2">{item.employeeName || item.employeeId}</td>
                  <td className="px-3 py-2">{item.entitlementDays}</td>
                  <td className="px-3 py-2">{item.usedDays}</td>
                  <td className="px-3 py-2">{item.carryoverDays}</td>
                  <td className="px-3 py-2 font-semibold">{item.remainingDays}</td>
                  <td className="px-3 py-2">{item.status}</td>
                  <td className="px-3 py-2 text-right">
                    <button onClick={() => openEdit(item)} className="text-purple-600">Redaktə et</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {showModal && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl w-full max-w-xl overflow-hidden">
              <div className="px-6 py-4 text-white" style={{ background: '#1e2a4a' }}>
                <h2 className="font-semibold">{editing ? 'Mezuniyyəti redaktə et' : 'Mezuniyyət əlavə et'}</h2>
              </div>
              <form onSubmit={onSubmit} className="p-6 space-y-4">
                {formError && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{formError}</div>}
                <label className="text-sm block">İşçi
                  <select value={selectedEmployeeId} onChange={(e) => setSelectedEmployeeId(e.target.value ? Number(e.target.value) : '')} className="mt-1 w-full border rounded-lg px-3 py-2" disabled={Boolean(editing)}>
                    <option value="">Seçin</option>
                    {employees.map((employee) => (
                      <option key={employee.id} value={employee.id}>{employee.employeeId} - {employee.firstName} {employee.lastName}</option>
                    ))}
                  </select>
                </label>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <label className="text-sm">Hüquq gün
                    <input type="number" min={0} value={form.entitlementDays} onChange={(e) => setForm({ ...form, entitlementDays: Number(e.target.value) })} className="mt-1 w-full border rounded-lg px-3 py-2" />
                  </label>
                  <label className="text-sm">İstifadə gün
                    <input type="number" min={0} value={form.usedDays} onChange={(e) => setForm({ ...form, usedDays: Number(e.target.value) })} className="mt-1 w-full border rounded-lg px-3 py-2" />
                  </label>
                  <label className="text-sm">Köçürülən gün
                    <input type="number" min={0} value={form.carryoverDays} onChange={(e) => setForm({ ...form, carryoverDays: Number(e.target.value) })} className="mt-1 w-full border rounded-lg px-3 py-2" />
                  </label>
                  <label className="text-sm">Status
                    <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as 'ACTIVE' | 'INACTIVE' })} className="mt-1 w-full border rounded-lg px-3 py-2">
                      <option value="ACTIVE">Aktiv</option>
                      <option value="INACTIVE">Deaktiv</option>
                    </select>
                  </label>
                </div>
                <div className="bg-purple-50 border border-purple-100 rounded-lg px-3 py-2 text-sm">
                  Qalıq gün (auto): <span className="font-semibold">{remainingPreview}</span>
                </div>
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
      </div>
    </Layout>
  )
}
