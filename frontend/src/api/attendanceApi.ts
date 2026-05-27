import client from './client.ts'
import { AttendanceReportFilters } from '../types'

export const attendanceApi = {
  getLogs: (employeeId: number, start: string, end: string) =>
    client.get(`/attendance/employee/${employeeId}?start=${start}&end=${end}`),
  getRange: (start: string, end: string) =>
    client.get(`/attendance/range?start=${start}&end=${end}`),
  getAccessLogs: (params?: { deviceId?: number; employeeNo?: string; limit?: number }) =>
    client.get('/logs/attendance', { params }),
  getSummary: (employeeId: number, start: string, end: string) =>
    client.get(`/attendance/summary/${employeeId}?start=${start}&end=${end}`),
  log: (data: object) => client.post('/attendance/log', data),
  getReport: (params: AttendanceReportFilters & { page?: number; size?: number }) =>
    client.get('/attendance/report', { params }),
  exportExcel: async (params: AttendanceReportFilters) =>
    client.get('/attendance/report/export', { params, responseType: 'blob' }),
}
