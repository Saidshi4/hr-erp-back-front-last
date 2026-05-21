import client from './client.ts'

export interface DeviceUser {
  id: number
  employeeNo: string
  name: string
}

export const deviceUserApi = {
  getAll: (deviceId: number) => client.get<DeviceUser[]>(`/devices/${deviceId}/users`),
  uploadFace: (deviceId: number, userId: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return client.post(`/devices/${deviceId}/users/${userId}/face`, formData)
  },
}
