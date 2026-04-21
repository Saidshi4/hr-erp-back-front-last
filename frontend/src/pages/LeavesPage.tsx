import { useEffect, useState } from 'react'
import Layout from '../components/Layout'
import { useLeaveStore } from '../store/leaveStore'
import { useEmployeeStore } from '../store/employeeStore'
import { LeaveRequest, LeaveType, Employee } from '../types'
import client from '../api/client'

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

  return (
    <Layout>
      <div className="p-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Leave Requests</h1>
            <p className="text-sm text-gray-500 mt-1">
              {pendingCount} pending · {approvedCount} approved
            </p>
          </div>
          <button
            onClick={openCreate}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
          >
            + New Request
          </button>
        </div>

        {/* Status Filter Tabs */}
        <div className="flex gap-2 mb-4">
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                statusFilter === s
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-600 border border-gray-300 hover:bg-gray-50'
              }`}
            >
              {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
              {s === 'PENDING' && pendingCount > 0 && (
                <span className="ml-1 bg-yellow-400 text-yellow-900 rounded-full px-1.5 py-0.5 text-xs">
                  {pendingCount}
                </span>
              )}
            </button>
          ))}
        </div>

        <div className="bg-white rounded-xl shadow-sm">
          {loading ? (
            <div className="p-8 text-center text-gray-500">Loading...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500">{error}</div>
          ) : filteredLeaves.length === 0 ? (
            <div className="p-8 text-center text-gray-500">No leave requests found.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Employee</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Leave Type</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Start Date</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">End Date</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Days</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Status</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredLeaves.map((leave: LeaveRequest) => {
                    const days = leave.startDate && leave.endDate
                      ? calcDays(leave.startDate, leave.endDate)
                      : '-'
                    return (
                      <tr key={leave.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4 text-sm font-medium text-gray-900">
                          {getEmployeeName(leave.employeeId)}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-600">
                          {getLeaveTypeName(leave.leaveTypeId)}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-600">{leave.startDate}</td>
                        <td className="px-6 py-4 text-sm text-gray-600">{leave.endDate}</td>
                        <td className="px-6 py-4 text-sm text-gray-600">{days}</td>
                        <td className="px-6 py-4">
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                            leave.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
                            leave.status === 'REJECTED' ? 'bg-red-100 text-red-700' :
                            'bg-yellow-100 text-yellow-700'
                          }`}>
                            {leave.status}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          {leave.status === 'PENDING' && (
                            <div className="flex gap-2">
                              <button
                                onClick={() => updateStatus(leave.id, 'APPROVED')}
                                className="text-xs bg-green-100 text-green-700 px-2 py-1 rounded hover:bg-green-200"
                              >
                                Approve
                              </button>
                              <button
                                onClick={() => updateStatus(leave.id, 'REJECTED')}
                                className="text-xs bg-red-100 text-red-700 px-2 py-1 rounded hover:bg-red-200"
                              >
                                Reject
                              </button>
                            </div>
                          )}
                          {leave.status !== 'PENDING' && (
                            <span className="text-xs text-gray-400">
                              {leave.approvalDate ? `on ${leave.approvalDate}` : '—'}
                            </span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
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
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">End Date *</label>
                  <input
                    type="date"
                    value={form.endDate}
                    min={form.startDate}
                    onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>
              {form.startDate && form.endDate && form.endDate >= form.startDate && (
                <p className="text-sm text-blue-600">
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
                className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
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
