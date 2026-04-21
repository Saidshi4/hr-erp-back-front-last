import { useEffect, useState } from 'react'
import Layout from '../components/Layout'
import { useEmployeeStore } from '../store/employeeStore'
import { Employee, Department } from '../types'
import { departmentApi } from '../api/departmentApi'

interface EmployeeFormData {
  firstName: string
  lastName: string
  email: string
  mobilePhone: string
  gender: string
  departmentId: number | ''
  finNumber: string
  hireDate: string
}

const defaultForm: EmployeeFormData = {
  firstName: '',
  lastName: '',
  email: '',
  mobilePhone: '',
  gender: '',
  departmentId: '',
  finNumber: '',
  hireDate: new Date().toISOString().split('T')[0],
}

const AVATAR_COLORS = ['#6366f1', '#a855f7', '#10b981', '#f59e0b', '#ef4444', '#3b82f6']

function getAvatarColor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

export default function EmployeesPage() {
  const { employees, loading, error, fetchEmployees, createEmployee, updateEmployee, deleteEmployee, totalPages, currentPage } = useEmployeeStore()
  const [search, setSearch] = useState('')
  const [departments, setDepartments] = useState<Department[]>([])
  const [showModal, setShowModal] = useState(false)
  const [editingEmployee, setEditingEmployee] = useState<Employee | null>(null)
  const [form, setForm] = useState<EmployeeFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<Employee | null>(null)

  useEffect(() => {
    fetchEmployees(0, 20)
    departmentApi.getAll().then((res) => setDepartments(res.data?.data ?? []))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditingEmployee(null)
    setForm(defaultForm)
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (emp: Employee) => {
    setEditingEmployee(emp)
    setForm({
      firstName: emp.firstName,
      lastName: emp.lastName,
      email: emp.email || '',
      mobilePhone: emp.mobilePhone || '',
      gender: emp.gender || '',
      departmentId: emp.departmentId || '',
      finNumber: emp.finNumber || '',
      hireDate: emp.hireDate || defaultForm.hireDate,
    })
    setFormError(null)
    setShowModal(true)
  }

  const handleSave = async () => {
    if (!form.firstName.trim() || !form.lastName.trim()) {
      setFormError('First name and last name are required.')
      return
    }
    if (!form.departmentId) {
      setFormError('Department is required.')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        ...form,
        departmentId: Number(form.departmentId),
      }
      if (editingEmployee) {
        await updateEmployee(editingEmployee.id, payload)
      } else {
        await createEmployee(payload)
      }
      setShowModal(false)
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Failed to save employee')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteConfirm) return
    try {
      await deleteEmployee(deleteConfirm.id)
      setDeleteConfirm(null)
    } catch {
      // handled by store
    }
  }

  const filtered = employees.filter((e: Employee) =>
    `${e.firstName} ${e.lastName}`.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <Layout>
      <div className="p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Employees</h1>
            <p className="text-sm text-gray-500 mt-1">{employees.length} total employees</p>
          </div>
          <button
            onClick={openCreate}
            className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-lg transition-colors"
            style={{ background: '#a855f7' }}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Add Employee
          </button>
        </div>

        {/* Search */}
        <div className="bg-white rounded-xl shadow-sm p-4 mb-4 flex items-center gap-3">
          <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            type="text"
            placeholder="Search employees..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="flex-1 outline-none text-sm text-gray-700 placeholder-gray-400"
          />
          {search && (
            <button onClick={() => setSearch('')} className="text-gray-400 hover:text-gray-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>

        {/* Employee List */}
        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Loading employees...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : filtered.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            {search ? 'No employees match your search.' : 'No employees yet.'}
          </div>
        ) : (
          <div className="space-y-3">
            {filtered.map((emp: Employee) => {
              const name = `${emp.firstName} ${emp.lastName}`
              const initials = `${emp.firstName.charAt(0)}${emp.lastName.charAt(0)}`.toUpperCase()
              const avatarColor = getAvatarColor(name)
              return (
                <div key={emp.id} className="bg-white rounded-xl shadow-sm p-5 flex items-center gap-4">
                  {/* Avatar */}
                  <div
                    className="w-11 h-11 rounded-full flex items-center justify-center text-white text-sm font-bold flex-shrink-0"
                    style={{ background: avatarColor }}
                  >
                    {initials}
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-gray-900 text-sm">{name}</p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      {emp.departmentName || 'No department'} · {emp.employeeId}
                    </p>
                  </div>

                  {/* Email */}
                  <div className="hidden md:block min-w-[180px]">
                    <p className="text-xs text-gray-400 mb-0.5">Email</p>
                    <p className="text-sm text-gray-600 truncate">{emp.email || '—'}</p>
                  </div>

                  {/* Status */}
                  <div className="flex-shrink-0">
                    <span
                      className="px-2.5 py-1 rounded-full text-xs font-medium"
                      style={emp.employmentStatus === 'ACTIVE'
                        ? { background: '#d1fae5', color: '#065f46' }
                        : emp.employmentStatus === 'ON_LEAVE'
                        ? { background: '#fef3c7', color: '#92400e' }
                        : { background: '#fee2e2', color: '#991b1b' }}
                    >
                      {emp.employmentStatus === 'ACTIVE' ? 'Active' :
                       emp.employmentStatus === 'ON_LEAVE' ? 'On Leave' : 'Inactive'}
                    </span>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <button
                      onClick={() => openEdit(emp)}
                      className="px-3 py-1.5 text-xs font-medium text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => setDeleteConfirm(emp)}
                      className="px-3 py-1.5 text-xs font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <button
              onClick={() => fetchEmployees(currentPage - 1)}
              disabled={currentPage === 0}
              className="px-4 py-2 text-sm border border-gray-300 bg-white rounded-lg disabled:opacity-50 hover:bg-gray-50"
            >
              Previous
            </button>
            <span className="text-sm text-gray-500">Page {currentPage + 1} of {totalPages}</span>
            <button
              onClick={() => fetchEmployees(currentPage + 1)}
              disabled={currentPage >= totalPages - 1}
              className="px-4 py-2 text-sm border border-gray-300 bg-white rounded-lg disabled:opacity-50 hover:bg-gray-50"
            >
              Next
            </button>
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">
              {editingEmployee ? 'Edit Employee' : 'Add Employee'}
            </h2>
            {formError && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">
                {formError}
              </div>
            )}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">First Name *</label>
                <input
                  type="text"
                  value={form.firstName}
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Last Name *</label>
                <input
                  type="text"
                  value={form.lastName}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Phone</label>
                <input
                  type="text"
                  value={form.mobilePhone}
                  onChange={(e) => setForm({ ...form, mobilePhone: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Gender</label>
                <select
                  value={form.gender}
                  onChange={(e) => setForm({ ...form, gender: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Select...</option>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Department *</label>
                <select
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value ? Number(e.target.value) : '' })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Select...</option>
                  {departments.map((d) => (
                    <option key={d.id} value={d.id}>{d.departmentName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">FIN Number</label>
                <input
                  type="text"
                  value={form.finNumber}
                  onChange={(e) => setForm({ ...form, finNumber: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Hire Date</label>
                <input
                  type="date"
                  value={form.hireDate}
                  onChange={(e) => setForm({ ...form, hireDate: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-4 py-2 text-sm text-white rounded-lg disabled:opacity-50 transition-colors"
                style={{ background: '#a855f7' }}
              >
                {saving ? 'Saving...' : editingEmployee ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">Delete Employee</h2>
            <p className="text-gray-600 mb-6 text-sm">
              Are you sure you want to delete <strong>{deleteConfirm.firstName} {deleteConfirm.lastName}</strong>? This action cannot be undone.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeleteConfirm(null)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
