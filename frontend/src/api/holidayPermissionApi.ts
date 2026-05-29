import client from './client.ts'
import { HolidayPermission } from '../types'

interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

export const holidayPermissionApi = {
  getAll: () => client.get<ApiResponse<HolidayPermission[]>>('/holiday-permissions'),
  getById: (id: number) => client.get<ApiResponse<HolidayPermission>>(`/holiday-permissions/${id}`),
  create: (payload: Partial<HolidayPermission>) => client.post<ApiResponse<HolidayPermission>>('/holiday-permissions', payload),
  update: (id: number, payload: Partial<HolidayPermission>) => client.put<ApiResponse<HolidayPermission>>(`/holiday-permissions/${id}`, payload),
  remove: (id: number) => client.delete<ApiResponse<void>>(`/holiday-permissions/${id}`),
  getRange: (start: string, end: string) =>
    client.get<ApiResponse<HolidayPermission[]>>('/holiday-permissions/range', { params: { start, end } }),
}
