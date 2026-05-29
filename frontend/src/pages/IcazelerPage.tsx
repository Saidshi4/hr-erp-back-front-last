import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Layout from '../components/Layout.tsx'
import { permissionApi } from '../api/permissionApi.ts'
import { employeeApi } from '../api/employeeApi.ts'
import { Employee, Permission, PermissionType } from '../types'
import { useDebounce } from '../hooks/useSearch.ts'

type PermissionForm = {
  permissionTypeId: number | ''
  applyType: 'EMPLOYEE' | 'DEPARTMENT' | 'BRANCH' | 'GROUP' | 'COMPANY'
  targetId: number | ''
  startDate: string
  endDate: string
  reason: string
  status: string
}

const emptyForm: PermissionForm = {
  permissionTypeId: '',
  applyType: 'EMPLOYEE',
  targetId: '',
  startDate: new Date().toISOString().split('T')[0],
  endDate: new Date().toISOString().split('T')[0],
  reason: '',
  status: 'PENDING',
}

export default function IcazelerPage() {
  const [searchParams] = useSearchParams()
  const employeeHistoryPk = searchParams.get('employeePk')
  const historyYear = Number(searchParams.get('year') || new Date().getFullYear())

  const [permissions, setPermissions] = useState<Permission[]>([])
  const [history, setHistory] = useState<Permission[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [types, setTypes] = useState<PermissionType[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search, 300)
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [selected, setSelected] = useState<Permission | null>(null)
  const [editing, setEditing] = useState<Permission | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [form, setForm] = useState<PermissionForm>(emptyForm)

  const fetchData = async () => {
    setLoading(true)
    setError('')
    try {
      const [permissionRes, typeRes, employeeRes] = await Promise.all([
        permissionApi.getAll({ search: debouncedSearch, status: status || undefined, type: type || undefined, start: start || undefined, end: end || undefined, page: 0, size: 200 }),
        permissionApi.getTypes(),
        employeeApi.getAll(0, 500),
      ])
      setPermissions(permissionRes.data?.data?.content ?? [])
      setTypes(typeRes.data?.data ?? [])
      setEmployees(employeeRes.data?.content ?? [])
    } catch (e: unknown) {
      setError((e as Error).message || 'İcazələr yüklənmədi')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void fetchData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch, status, type, start, end])

  useEffect(() => {
    if (!employeeHistoryPk) {
      setHistory([])
      return
    }
    permissionApi.getEmployeeHistory(Number(employeeHistoryPk), historyYear)
      .then((res) => setHistory(res.data?.data ?? []))
      .catch(() => setHistory([]))
  }, [employeeHistoryPk, historyYear])

  const employeeMap = useMemo(() => {
    const map = new Map<number, Employee>()
    employees.forEach((employee) => map.set(employee.id, employee))
    return map
  }, [employees])

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm)
    setFormError('')
    setShowForm(true)
  }

  const openEdit = (permission: Permission) => {
    setEditing(permission)
    setForm({
      permissionTypeId: types.find((item) => item.name === permission.leaveType || item.code === permission.leaveType)?.id ?? '',
      applyType: (permission.applyType as PermissionForm['applyType']) || 'EMPLOYEE',
      targetId: permission.targetId ?? '',
      startDate: permission.startDate,
      endDate: permission.endDate,
      reason: permission.reason || '',
      status: permission.status || 'PENDING',
    })
    setFormError('')
    setShowForm(true)
  }

  const save = async () => {
    if (!form.permissionTypeId) { setFormError('İcazə növü seçin'); return }
    if (!form.startDate || !form.endDate) { setFormError('Tarix aralığını seçin'); return }
    if (form.endDate < form.startDate) { setFormError('Bitmə tarixi başlanğıcdan kiçik ola bilməz'); return }
    if (form.applyType !== 'COMPANY' && !form.targetId) { setFormError('Hədəf seçin'); return }

    setSaving(true)
    setFormError('')
    try {
      const typeName = types.find((item) => item.id === Number(form.permissionTypeId))?.name
      const payload = {
        permissionTypeId: Number(form.permissionTypeId),
        leaveType: typeName,
        applyType: form.applyType,
        targetId: form.applyType === 'COMPANY' ? undefined : Number(form.targetId),
        startDate: form.startDate,
        endDate: form.endDate,
        reason: form.reason || undefined,
        status: form.status,
      }
      if (editing) {
        await permissionApi.update(editing.id, payload)
      } else {
        await permissionApi.create(payload)
      }
      setShowForm(false)
      await fetchData()
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Saxlama alınmadı')
    } finally {
      setSaving(false)
    }
  }

  const approve = async (id: number) => {
    await permissionApi.approve(id)
    await fetchData()
  }

  const reject = async (id: number) => {
    await permissionApi.reject(id)
    await fetchData()
  }

  const remove = async (id: number) => {
    await permissionApi.remove(id)
    await fetchData()
  }

  const targetLabel = (permission: Permission) => {
    if (permission.applyType === 'COMPANY') return 'COMPANY'
    if (permission.applyType === 'EMPLOYEE') {
      const employee = permission.targetId ? employeeMap.get(permission.targetId) : null
      return employee ? `${employee.employeeId} — ${employee.firstName} ${employee.lastName}` : `${permission.employeeId || ''} ${permission.employeeName || ''}`.trim()
    }
    return permission.targetId ? `${permission.applyType} #${permission.targetId}` : permission.applyType
  }

  return (
    <Layout>
      <div className="p-8 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">İcazələr</h1>
            <p className="text-sm text-gray-500">Audit məlumatları ilə icazə idarəetməsi</p>
          </div>
          <button onClick={openCreate} className="px-4 py-2 rounded-lg text-white text-sm" style={{ background: '#a855f7' }}>Yeni icazə</button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Ad, employeeId, FIN, səbəb..." className="border rounded-lg px-3 py-2 text-sm" />
          <select value={status} onChange={(e) => setStatus(e.target.value)} className="border rounded-lg px-3 py-2 text-sm">
            <option value="">Bütün statuslar</option>
            {['PENDING', 'APPROVED', 'REJECTED', 'ACTIVE', 'INACTIVE'].map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
          <select value={type} onChange={(e) => setType(e.target.value)} className="border rounded-lg px-3 py-2 text-sm">
            <option value="">Bütün növlər</option>
            {types.map((item) => <option key={item.id} value={item.name}>{item.name}</option>)}
          </select>
          <input type="date" value={start} onChange={(e) => setStart(e.target.value)} className="border rounded-lg px-3 py-2 text-sm" />
          <input type="date" value={end} onChange={(e) => setEnd(e.target.value)} className="border rounded-lg px-3 py-2 text-sm" />
        </div>

        {employeeHistoryPk && (
          <div className="bg-white border rounded-xl p-4">
            <h3 className="font-semibold text-gray-900 mb-2">İcazə tarixçəsi · {historyYear}</h3>
            <div className="text-sm text-gray-600 space-y-1">
              {history.length === 0 ? <p>Bu əməkdaş üçün qeyd yoxdur.</p> : history.map((item) => (
                <p key={item.id}>{item.startDate} → {item.endDate} · {item.leaveType} · {item.status}</p>
              ))}
            </div>
          </div>
        )}

        <div className="bg-white border rounded-xl overflow-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-3 py-2 text-left">#</th>
                <th className="px-3 py-2 text-left">Employee</th>
                <th className="px-3 py-2 text-left">Target</th>
                <th className="px-3 py-2 text-left">Növ</th>
                <th className="px-3 py-2 text-left">Səbəb</th>
                <th className="px-3 py-2 text-left">Start</th>
                <th className="px-3 py-2 text-left">End</th>
                <th className="px-3 py-2 text-left">Status</th>
                <th className="px-3 py-2 text-left">Approved by</th>
                <th className="px-3 py-2 text-left">Approval date</th>
                <th className="px-3 py-2 text-left">Created by</th>
                <th className="px-3 py-2 text-right">Əməliyyatlar</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={12} className="px-3 py-8 text-center text-gray-500">Yüklənir...</td></tr>
              ) : error ? (
                <tr><td colSpan={12} className="px-3 py-8 text-center text-red-500">{error}</td></tr>
              ) : permissions.length === 0 ? (
                <tr><td colSpan={12} className="px-3 py-8 text-center text-gray-500">Məlumat tapılmadı</td></tr>
              ) : permissions.map((permission, index) => (
                <tr key={permission.id} className="border-t hover:bg-gray-50 cursor-pointer" onClick={() => setSelected(permission)}>
                  <td className="px-3 py-2">{index + 1}</td>
                  <td className="px-3 py-2">{permission.employeeId || '—'} {permission.employeeName || ''}</td>
                  <td className="px-3 py-2">{targetLabel(permission) || '—'}</td>
                  <td className="px-3 py-2">{permission.leaveType}</td>
                  <td className="px-3 py-2">{permission.reason || '—'}</td>
                  <td className="px-3 py-2">{permission.startDate}</td>
                  <td className="px-3 py-2">{permission.endDate}</td>
                  <td className="px-3 py-2"><span className="px-2 py-0.5 rounded-full bg-gray-100">{permission.status}</span></td>
                  <td className="px-3 py-2">{permission.approvedBy ?? '—'}</td>
                  <td className="px-3 py-2">{permission.approvalDate || '—'}</td>
                  <td className="px-3 py-2">{permission.createdBy ?? '—'}</td>
                  <td className="px-3 py-2 text-right space-x-2">
                    <button onClick={(e) => { e.stopPropagation(); setSelected(permission) }} className="text-blue-600">Bax</button>
                    <button onClick={(e) => { e.stopPropagation(); openEdit(permission) }} className="text-purple-600">Redaktə et</button>
                    <button onClick={(e) => { e.stopPropagation(); void remove(permission.id) }} className="text-red-600">Sil</button>
                    <button onClick={(e) => { e.stopPropagation(); void approve(permission.id) }} className="text-green-600">Təsdiqlə</button>
                    <button onClick={(e) => { e.stopPropagation(); void reject(permission.id) }} className="text-amber-600">Rədd et</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {selected && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl p-5 w-full max-w-xl space-y-2">
            <h3 className="text-lg font-semibold">İcazə detalları</h3>
            <p><strong>Employee:</strong> {selected.employeeId || '—'} {selected.employeeName || ''}</p>
            <p><strong>Target:</strong> {targetLabel(selected)}</p>
            <p><strong>Növ:</strong> {selected.leaveType}</p>
            <p><strong>Səbəb:</strong> {selected.reason || '—'}</p>
            <p><strong>Tarix:</strong> {selected.startDate} → {selected.endDate}</p>
            <p><strong>Status:</strong> {selected.status}</p>
            <p><strong>Created by:</strong> {selected.createdBy ?? '—'}</p>
            <p><strong>Created at:</strong> {selected.createdAt || '—'}</p>
            <p><strong>Approved by:</strong> {selected.approvedBy ?? '—'}</p>
            <p><strong>Approval date:</strong> {selected.approvalDate || '—'}</p>
            <div className="text-right pt-2"><button onClick={() => setSelected(null)} className="px-3 py-2 border rounded-lg">Bağla</button></div>
          </div>
        </div>
      )}

      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl p-5 w-full max-w-xl space-y-3">
            <h3 className="text-lg font-semibold">{editing ? 'İcazəni redaktə et' : 'Yeni icazə'}</h3>
            {formError && <p className="text-sm text-red-600">{formError}</p>}
            <select value={form.permissionTypeId} onChange={(e) => setForm((prev) => ({ ...prev, permissionTypeId: e.target.value ? Number(e.target.value) : '' }))} className="w-full border rounded-lg px-3 py-2 text-sm">
              <option value="">Növ seçin</option>
              {types.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
            <select value={form.applyType} onChange={(e) => setForm((prev) => ({ ...prev, applyType: e.target.value as PermissionForm['applyType'], targetId: '' }))} className="w-full border rounded-lg px-3 py-2 text-sm">
              {['EMPLOYEE', 'DEPARTMENT', 'BRANCH', 'GROUP', 'COMPANY'].map((item) => <option key={item} value={item}>{item}</option>)}
            </select>

            {form.applyType === 'EMPLOYEE' ? (
              <select value={form.targetId} onChange={(e) => setForm((prev) => ({ ...prev, targetId: e.target.value ? Number(e.target.value) : '' }))} className="w-full border rounded-lg px-3 py-2 text-sm">
                <option value="">Əməkdaş seçin</option>
                {employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.employeeId} — {employee.firstName} {employee.lastName}</option>)}
              </select>
            ) : form.applyType !== 'COMPANY' ? (
              <input type="number" value={form.targetId} onChange={(e) => setForm((prev) => ({ ...prev, targetId: e.target.value ? Number(e.target.value) : '' }))} placeholder="targetId" className="w-full border rounded-lg px-3 py-2 text-sm" />
            ) : null}

            <div className="grid grid-cols-2 gap-3">
              <input type="date" value={form.startDate} onChange={(e) => setForm((prev) => ({ ...prev, startDate: e.target.value }))} className="border rounded-lg px-3 py-2 text-sm" />
              <input type="date" value={form.endDate} onChange={(e) => setForm((prev) => ({ ...prev, endDate: e.target.value }))} className="border rounded-lg px-3 py-2 text-sm" />
            </div>
            <textarea value={form.reason} onChange={(e) => setForm((prev) => ({ ...prev, reason: e.target.value }))} placeholder="Səbəb" className="w-full border rounded-lg px-3 py-2 text-sm h-20" />
            <select value={form.status} onChange={(e) => setForm((prev) => ({ ...prev, status: e.target.value }))} className="w-full border rounded-lg px-3 py-2 text-sm">
              {['PENDING', 'APPROVED', 'REJECTED', 'ACTIVE', 'INACTIVE'].map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
            <div className="text-right space-x-2">
              <button onClick={() => setShowForm(false)} className="px-3 py-2 border rounded-lg">Ləğv et</button>
              <button onClick={() => void save()} disabled={saving} className="px-3 py-2 rounded-lg text-white disabled:opacity-60" style={{ background: '#a855f7' }}>
                {saving ? 'Yadda saxlanır...' : 'Yadda saxla'}
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
