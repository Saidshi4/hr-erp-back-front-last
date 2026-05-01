import client from './client'
import { Department } from '../types'

export const departmentApi = {
  getAll: () => client.get<{ data: Department[] }>('/departments'),
  getByBranch: (branchId: number) => client.get<{ data: Department[] }>(`/departments/branch/${branchId}`),
  getById: (id: number) => client.get<{ data: Department }>(`/departments/${id}`),
  create: (data: Partial<Department>) => client.post<{ data: Department }>('/departments', data),
  update: (id: number, data: Partial<Department>) => client.put<{ data: Department }>(`/departments/${id}`, data),
  delete: (id: number) => client.delete(`/departments/${id}`),
}
