import client from './client'

export const attendanceApi = {
  getLogs: (employeeId: number, start: string, end: string) =>
    client.get(`/attendance/employee/${employeeId}?start=${start}&end=${end}`),
  getRange: (start: string, end: string) =>
    client.get(`/attendance/range?start=${start}&end=${end}`),
  getSummary: (employeeId: number, start: string, end: string) =>
    client.get(`/attendance/summary/${employeeId}?start=${start}&end=${end}`),
  log: (data: object) => client.post('/attendance/log', data),
}
