import client from './client.ts'
import { LeaveRequest, LeaveType } from '../types'

export const leaveApi = {
  getAll: () => client.get<{ data: LeaveRequest[] }>('/leaves'),
  getByEmployee: (employeeId: number) => client.get<{ data: LeaveRequest[] }>(`/leaves/employee/${employeeId}`),
  getTypes: () => client.get<{ data: LeaveType[] }>('/leaves/types'),
  create: (data: Partial<LeaveRequest>) => client.post<{ data: LeaveRequest }>('/leaves', data),
  updateStatus: (id: number, status: string, approvedBy?: number) =>
    client.put(`/leaves/${id}/status?status=${status}${approvedBy ? `&approvedBy=${approvedBy}` : ''}`),
  delete: (id: number) => client.delete(`/leaves/${id}`),
}
