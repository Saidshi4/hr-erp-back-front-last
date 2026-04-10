import { useEffect, useState } from 'react'
import Layout from '../components/Layout'
import { useEmployeeStore } from '../store/employeeStore'
import { Employee } from '../types'

export default function EmployeesPage() {
  const { employees, loading, error, fetchEmployees, totalPages, currentPage } = useEmployeeStore()
  const [search, setSearch] = useState('')

  useEffect(() => {
    fetchEmployees(0, 20)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <Layout>
      <div className="p-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Employees</h1>
        </div>

        <div className="bg-white rounded-xl shadow-sm">
          <div className="p-4 border-b">
            <input
              type="text"
              placeholder="Search employees..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full max-w-sm px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {loading ? (
            <div className="p-8 text-center text-gray-500">Loading...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500">{error}</div>
          ) : (
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Employee ID</th>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Name</th>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Department</th>
                  <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {employees
                  .filter((e: Employee) =>
                    `${e.firstName} ${e.lastName}`.toLowerCase().includes(search.toLowerCase())
                  )
                  .map((emp: Employee) => (
                    <tr key={emp.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 text-sm text-gray-600">{emp.employeeId}</td>
                      <td className="px-6 py-4 text-sm font-medium text-gray-900">
                        {emp.firstName} {emp.lastName}
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-600">{emp.departmentName || '-'}</td>
                      <td className="px-6 py-4">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                          emp.employmentStatus === 'ACTIVE'
                            ? 'bg-green-100 text-green-700'
                            : emp.employmentStatus === 'ON_LEAVE'
                            ? 'bg-yellow-100 text-yellow-700'
                            : 'bg-red-100 text-red-700'
                        }`}>
                          {emp.employmentStatus}
                        </span>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          )}

          {totalPages > 1 && (
            <div className="p-4 border-t flex items-center justify-between">
              <button
                onClick={() => fetchEmployees(currentPage - 1)}
                disabled={currentPage === 0}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50"
              >
                Previous
              </button>
              <span className="text-sm text-gray-500">Page {currentPage + 1} of {totalPages}</span>
              <button
                onClick={() => fetchEmployees(currentPage + 1)}
                disabled={currentPage >= totalPages - 1}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50"
              >
                Next
              </button>
            </div>
          )}
        </div>
      </div>
    </Layout>
  )
}
