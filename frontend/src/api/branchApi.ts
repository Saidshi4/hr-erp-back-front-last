import client from './client'
import { Branch } from '../types'

export const branchApi = {
  getAll: () => client.get<{ data: Branch[] }>('/branches'),
  getById: (id: number) => client.get<{ data: Branch }>(`/branches/${id}`),
  create: (data: Partial<Branch>) => client.post<{ data: Branch }>('/branches', data),
  update: (id: number, data: Partial<Branch>) => client.put<{ data: Branch }>(`/branches/${id}`, data),
  delete: (id: number) => client.delete(`/branches/${id}`),
}
