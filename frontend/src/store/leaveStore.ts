import { create } from 'zustand'
import { LeaveRequest } from '../types'
import { leaveApi } from '../api/leaveApi.ts'

interface LeaveState {
  leaves: LeaveRequest[]
  loading: boolean
  error: string | null
  fetchLeaves: () => Promise<void>
  createLeave: (data: Partial<LeaveRequest>) => Promise<void>
  updateStatus: (id: number, status: string, approvedBy?: number) => Promise<void>
}

export const useLeaveStore = create<LeaveState>((set, get) => ({
  leaves: [],
  loading: false,
  error: null,
  fetchLeaves: async () => {
    set({ loading: true, error: null })
    try {
      const res = await leaveApi.getAll()
      set({ leaves: res.data?.data || [], loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  createLeave: async (data) => {
    await leaveApi.create(data)
    await get().fetchLeaves()
  },
  updateStatus: async (id, status, approvedBy) => {
    await leaveApi.updateStatus(id, status, approvedBy)
    await get().fetchLeaves()
  },
}))
