import client from './client.ts'
import { AnnualLeaveBalance } from '../types'

interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

export const annualLeaveApi = {
  getAll: (params?: { year?: number; employeeId?: number }) =>
    client.get<ApiResponse<AnnualLeaveBalance[]>>('/annual-leave', { params }),
  create: (payload: Partial<AnnualLeaveBalance>) =>
    client.post<ApiResponse<AnnualLeaveBalance>>('/annual-leave', payload),
  update: (id: number, payload: Partial<AnnualLeaveBalance>) =>
    client.put<ApiResponse<AnnualLeaveBalance>>(`/annual-leave/${id}`, payload),
  recalculate: (employeeId: number, year: number) =>
    client.post<ApiResponse<AnnualLeaveBalance>>(`/annual-leave/${employeeId}/${year}/recalculate`),
}
