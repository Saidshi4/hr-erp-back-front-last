import client from './client'
import { DeviceConfig } from '../types'

export const deviceApi = {
  getAll: () => client.get<{ data: DeviceConfig[] }>('/devices'),
  create: (data: Partial<DeviceConfig> & { password?: string }) => client.post<{ data: DeviceConfig }>('/devices', data),
  update: (id: number, data: Partial<DeviceConfig>) => client.put<{ data: DeviceConfig }>(`/devices/${id}`, data),
  delete: (id: number) => client.delete(`/devices/${id}`),
  sync: (id: number) => client.post(`/devices/${id}/sync`),
  getHistory: (id: number) => client.get(`/devices/${id}/history`),
}
