import client from './client.ts'
import { Permission, PermissionType } from '../types'

export const permissionApi = {
  getAll: () => client.get('/permissions'),
  getById: (id: number) => client.get(`/permissions/${id}`),
  create: (data: Partial<Permission>) => client.post('/permissions', data),
  update: (id: number, data: Partial<Permission>) => client.put(`/permissions/${id}`, data),
  delete: (id: number) => client.delete(`/permissions/${id}`),
  getTypes: () => client.get<{ data: PermissionType[] }>('/permissions/types'),
  createType: (data: Partial<PermissionType>) => client.post('/permissions/types', data),
  deleteType: (id: number) => client.delete(`/permissions/types/${id}`),
}
