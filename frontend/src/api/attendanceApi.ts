import client from './client.ts'

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
}
