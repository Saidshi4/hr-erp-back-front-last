import { useEffect, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { useLeaveStore } from '../store/leaveStore.ts'
import { useEmployeeStore } from '../store/employeeStore.ts'
import { LeaveRequest, LeaveType, Employee } from '../types'
import client from '../api/client.ts'

interface LeaveFormData {
  employeeId: number | ''
  leaveTypeId: number | ''
  startDate: string
  endDate: string
}

const defaultForm: LeaveFormData = {
  employeeId: '',
  leaveTypeId: '',
  startDate: new Date().toISOString().split('T')[0],
  endDate: new Date().toISOString().split('T')[0],
}

const MILLIS_PER_DAY = 86_400_000

const calcDays = (start: string, end: string) =>
  Math.ceil((new Date(end).getTime() - new Date(start).getTime()) / MILLIS_PER_DAY) + 1

export default function LeavesPage() {
  const { leaves, loading, error, fetchLeaves, createLeave, updateStatus } = useLeaveStore()
  const { employees, fetchEmployees } = useEmployeeStore()

  const [leaveTypes, setLeaveTypes] = useState<LeaveType[]>([])
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState<LeaveFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<string>('ALL')

  useEffect(() => {
    fetchLeaves()
    fetchEmployees(0, 200)
    client.get<{ data: LeaveType[] }>('/leaves/types')
      .then((res) => setLeaveTypes(res.data?.data ?? []))
      .catch(() => setLeaveTypes([]))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setForm(defaultForm)
    setFormError(null)
    setShowModal(true)
  }

  const handleSave = async () => {
    if (!form.employeeId) { setFormError('Employee is required.'); return }
    if (!form.leaveTypeId) { setFormError('Leave type is required.'); return }
    if (!form.startDate || !form.endDate) { setFormError('Start and end dates are required.'); return }
    if (form.endDate < form.startDate) { setFormError('End date cannot be before start date.'); return }
    setSaving(true)
    setFormError(null)
    try {
      await createLeave({
        employeeId: Number(form.employeeId),
        leaveTypeId: Number(form.leaveTypeId),
        startDate: form.startDate,
        endDate: form.endDate,
      })
      setShowModal(false)
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Failed to create leave request')
    } finally {
      setSaving(false)
    }
  }

  const getEmployeeName = (id: number) => {
    const emp = employees.find((e: Employee) => e.id === id)
    return emp ? `${emp.firstName} ${emp.lastName}` : `Employee #${id}`
  }

  const getLeaveTypeName = (id: number) => {
    const lt = leaveTypes.find((t) => t.id === id)
    return lt ? lt.leaveName : `Type #${id}`
  }

  const filteredLeaves = statusFilter === 'ALL'
    ? leaves
    : leaves.filter((l: LeaveRequest) => l.status === statusFilter)

  const pendingCount = leaves.filter((l: LeaveRequest) => l.status === 'PENDING').length
  const approvedCount = leaves.filter((l: LeaveRequest) => l.status === 'APPROVED').length

  const statusStyles: Record<string, { bg: string; color: string }> = {
    APPROVED: { bg: '#d1fae5', color: '#065f46' },
    PENDING: { bg: '#fef3c7', color: '#92400e' },
    REJECTED: { bg: '#fee2e2', color: '#991b1b' },
  }

  return (
    <Layout>
      <div className="p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex items-start justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Leave Requests</h1>
            <p className="text-sm text-gray-500 mt-1">
              {pendingCount} pending · {approvedCount} approved
            </p>
          </div>
          <button
            onClick={openCreate}
            className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-lg transition-colors"
            style={{ background: '#a855f7' }}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Request
          </button>
        </div>

        {/* Status Tabs */}
        <div className="flex gap-2 mb-5">
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className="px-4 py-2 rounded-lg text-sm font-medium transition-all"
              style={statusFilter === s
                ? { background: '#a855f7', color: '#fff' }
                : { background: '#fff', color: '#6b7280', border: '1px solid #e5e7eb' }}
            >
              {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
              {s === 'PENDING' && pendingCount > 0 && (
                <span className="ml-1.5 bg-yellow-400 text-yellow-900 rounded-full px-1.5 py-0.5 text-xs">
                  {pendingCount}
                </span>
              )}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Loading leave requests...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : filteredLeaves.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            No leave requests found.
          </div>
        ) : (
          <div className="space-y-3">
            {filteredLeaves.map((leave: LeaveRequest) => {
              const days = leave.startDate && leave.endDate ? calcDays(leave.startDate, leave.endDate) : 0
              const statusStyle = statusStyles[leave.status] ?? { bg: '#f3f4f6', color: '#6b7280' }
              return (
                <div key={leave.id} className="bg-white rounded-xl shadow-sm p-5 flex items-center gap-5">
                  {/* Icon */}
                  <div className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: '#faf5ff' }}>
                    <svg className="w-5 h-5" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-gray-900">{getEmployeeName(leave.employeeId)}</p>
                    <p className="text-xs text-gray-400 mt-0.5">{getLeaveTypeName(leave.leaveTypeId)}</p>
                  </div>

                  {/* Dates */}
                  <div className="hidden md:block text-center min-w-[160px]">
                    <p className="text-xs text-gray-400 mb-0.5">Duration</p>
                    <p className="text-sm text-gray-700">{leave.startDate} → {leave.endDate}</p>
                    <p className="text-xs text-gray-400">{days > 0 ? `${days} day${days !== 1 ? 's' : ''}` : '—'}</p>
                  </div>

                  {/* Status */}
                  <span
                    className="px-2.5 py-1 rounded-full text-xs font-medium flex-shrink-0"
                    style={statusStyle}
                  >
                    {leave.status.charAt(0) + leave.status.slice(1).toLowerCase()}
                  </span>

                  {/* Actions */}
                  <div className="flex-shrink-0">
                    {leave.status === 'PENDING' ? (
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => updateStatus(leave.id, 'APPROVED')}
                          className="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
                          style={{ background: '#d1fae5', color: '#065f46' }}
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => updateStatus(leave.id, 'REJECTED')}
                          className="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
                          style={{ background: '#fee2e2', color: '#991b1b' }}
                        >
                          Reject
                        </button>
                      </div>
                    ) : (
                      <span className="text-xs text-gray-400">
                        {leave.approvalDate ? `${leave.approvalDate}` : '—'}
                      </span>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Create Leave Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">New Leave Request</h2>
            {formError && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">
                {formError}
              </div>
            )}
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Employee *</label>
                <select
                  value={form.employeeId}
                  onChange={(e) => setForm({ ...form, employeeId: e.target.value ? Number(e.target.value) : '' })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Select employee...</option>
                  {employees.map((emp: Employee) => (
                    <option key={emp.id} value={emp.id}>
                      {emp.firstName} {emp.lastName} ({emp.employeeId})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Leave Type *</label>
                <select
                  value={form.leaveTypeId}
                  onChange={(e) => setForm({ ...form, leaveTypeId: e.target.value ? Number(e.target.value) : '' })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Select leave type...</option>
                  {leaveTypes.map((lt) => (
                    <option key={lt.id} value={lt.id}>
                      {lt.leaveName} {lt.annualEntitlement ? `(${lt.annualEntitlement} days)` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Start Date *</label>
                  <input
                    type="date"
                    value={form.startDate}
                    onChange={(e) => setForm({ ...form, startDate: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">End Date *</label>
                  <input
                    type="date"
                    value={form.endDate}
                    min={form.startDate}
                    onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                  />
                </div>
              </div>
              {form.startDate && form.endDate && form.endDate >= form.startDate && (
                <p className="text-sm" style={{ color: '#a855f7' }}>
                  Duration: {calcDays(form.startDate, form.endDate)} day(s)
                </p>
              )}
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
                {saving ? 'Submitting...' : 'Submit Request'}
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
