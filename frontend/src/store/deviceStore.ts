import { create } from 'zustand'
import { DeviceConfig } from '../types'
import { deviceApi } from '../api/deviceApi.ts'

interface DeviceState {
  devices: DeviceConfig[]
  loading: boolean
  error: string | null
  fetchDevices: () => Promise<void>
  createDevice: (data: Partial<DeviceConfig> & { password?: string }) => Promise<void>
  updateDevice: (id: number, data: Partial<DeviceConfig> & { password?: string }) => Promise<void>
  deleteDevice: (id: number) => Promise<void>
  syncDevice: (id: number) => Promise<void>
}

type DeviceApiItem = Partial<DeviceConfig> & {
  ip?: string
  name?: string
  enabled?: boolean
  running?: boolean
}

const normalizeDevice = (item: DeviceApiItem): DeviceConfig => ({
  id: Number(item.id),
  deviceId: item.deviceId ?? String(item.id ?? ''),
  deviceName: item.deviceName ?? item.name,
  deviceIp: item.deviceIp ?? item.ip ?? '',
  devicePort: item.devicePort,
  username: item.username,
  branchId: item.branchId,
  status: item.status ?? ((item.running ?? item.enabled) ? 'ACTIVE' : 'INACTIVE'),
  lastSyncTime: item.lastSyncTime,
})

const extractDevices = (payload: unknown): DeviceConfig[] => {
  // Backend may proxy device list directly as [] while older endpoints return { data: [] }.
  const list = Array.isArray(payload)
    ? payload
    : (payload as { data?: unknown })?.data

  return Array.isArray(list) ? list.map((item) => normalizeDevice(item as DeviceApiItem)) : []
}

export const useDeviceStore = create<DeviceState>((set, get) => ({
  devices: [],
  loading: false,
  error: null,
  fetchDevices: async () => {
    set({ loading: true, error: null })
    try {
      const res = await deviceApi.getAll()
      set({ devices: extractDevices(res.data), loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  createDevice: async (data) => {
    await deviceApi.create(data)
    await get().fetchDevices()
  },
  updateDevice: async (id, data) => {
    await deviceApi.update(id, data)
    await get().fetchDevices()
  },
  deleteDevice: async (id) => {
    await deviceApi.delete(id)
    await get().fetchDevices()
  },
  syncDevice: async (id) => {
    await deviceApi.sync(id)
    await get().fetchDevices()
  },
}))
