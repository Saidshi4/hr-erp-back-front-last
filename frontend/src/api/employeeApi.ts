import client from './client.ts'
import { ApiResponse, Employee, EmployeeSearchResult, PaginatedResponse } from '../types'

export const employeeApi = {
  getAll: (page = 0, size = 20, branchId?: number) =>
    client.get<PaginatedResponse<Employee>>(
      `/employees?page=${page}&size=${size}${branchId ? `&branchId=${branchId}` : ''}`
    ),
  getById: (id: number) => client.get<{ data: Employee }>(`/employees/${id}`),
  create: (data: Partial<Employee>) => client.post<{ data: Employee }>('/employees', data),
  update: (id: number, data: Partial<Employee>) => client.put<{ data: Employee }>(`/employees/${id}`, data),
  delete: (id: number) => client.delete(`/employees/${id}`),
  search: (q: string, page = 0, size = 20) =>
    client.get<PaginatedResponse<Employee>>(`/employees/search?q=${q}&page=${page}&size=${size}`),
  searchEmployees: (q: string) =>
    client.get<ApiResponse<EmployeeSearchResult[]>>(`/employees/search?q=${encodeURIComponent(q)}`),
  getByBranch: (branchId: number, page = 0, size = 20) =>
    client.get<PaginatedResponse<Employee>>(`/employees/branch/${branchId}?page=${page}&size=${size}`),
  getByDepartment: (departmentId: number) =>
    client.get<{ data: Employee[] }>(`/employees/department/${departmentId}`),
  getDoors: (id: number) =>
    client.get<{ data: string[] }>(`/employees/${id}/doors`),
  getDistinctAreas: () =>
    client.get<{ data: string[] }>('/employees/areas'),
}
