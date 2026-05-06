import client from './client'

export interface DeviceUserCreateRequest {
  employeeNo: string
  name: string
  userType?: string
  gender?: string
  beginTime?: string
  endTime?: string
  faceDataUrl?: string
}

export interface DeviceUserUpdateRequest {
  name?: string
  userType?: string
  gender?: string
  beginTime?: string
  endTime?: string
  faceDataUrl?: string
}

export const deviceUserApi = {
  /** List all users registered on the device (via ISAPI). */
  listUsers: (deviceId: number) =>
    client.get(`/devices/${deviceId}/users`),

  /** Get a single device user by ISAPI-assigned user ID. */
  getUser: (deviceId: number, userId: number) =>
    client.get(`/devices/${deviceId}/users/${userId}`),

  /** Create a new device user and sync to physical device. */
  createUser: (deviceId: number, data: DeviceUserCreateRequest) =>
    client.post(`/devices/${deviceId}/users`, data),

  /** Update an existing device user. */
  updateUser: (deviceId: number, userId: number, data: DeviceUserUpdateRequest) =>
    client.put(`/devices/${deviceId}/users/${userId}`, data),

  /** Delete a device user from ISAPI and the physical device. */
  deleteUser: (deviceId: number, userId: number) =>
    client.delete(`/devices/${deviceId}/users/${userId}`),

  /** Trigger an immediate sync of the user to the physical device. */
  syncUser: (deviceId: number, userId: number) =>
    client.post(`/devices/${deviceId}/users/${userId}/sync`),

  /** Upload a face image for the device user (multipart). */
  uploadFace: (deviceId: number, userId: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return client.post(`/devices/${deviceId}/users/${userId}/face`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
