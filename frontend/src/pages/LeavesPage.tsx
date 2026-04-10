import { useEffect } from 'react'
import Layout from '../components/Layout'
import { useLeaveStore } from '../store/leaveStore'
import { LeaveRequest } from '../types'

export default function LeavesPage() {
  const { leaves, loading, error, fetchLeaves, updateStatus } = useLeaveStore()

  useEffect(() => {
    fetchLeaves()
  }, [fetchLeaves])

  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Leave Requests</h1>

        <div className="bg-white rounded-xl shadow-sm">
          {loading ? (
            <div className="p-8 text-center text-gray-500">Loading...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500">{error}</div>
          ) : (
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Employee</th>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Start Date</th>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">End Date</th>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Status</th>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {leaves.map((leave: LeaveRequest) => (
                  <tr key={leave.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 text-sm text-gray-900">Employee #{leave.employeeId}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{leave.startDate}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{leave.endDate}</td>
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
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </Layout>
  )
}
