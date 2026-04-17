import { create } from 'zustand'
import { DeviceConfig } from '../types'
import { deviceApi } from '../api/deviceApi'

interface DeviceState {
  devices: DeviceConfig[]
  loading: boolean
  error: string | null
  fetchDevices: () => Promise<void>
  syncDevice: (id: number) => Promise<void>
}

export const useDeviceStore = create<DeviceState>((set, get) => ({
  devices: [],
  loading: false,
  error: null,
  fetchDevices: async () => {
    set({ loading: true, error: null })
    try {
      const res = await deviceApi.getAll()
      set({ devices: res.data?.data || [], loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  syncDevice: async (id) => {
    await deviceApi.sync(id)
    await get().fetchDevices()
  },
}))
