import { create } from 'zustand'
import { attendanceApi } from '../api/attendanceApi.ts'
import { AttendanceReportFilters, AttendanceReportRow, ApiResponse, PaginatedResponse } from '../types'

interface AttendanceReportState {
  rows: AttendanceReportRow[]
  filters: AttendanceReportFilters
  page: number
  size: number
  totalElements: number
  totalPages: number
  loading: boolean
  error: string | null
  setFilters: (filters: AttendanceReportFilters) => void
  setPage: (page: number) => void
  fetchReport: () => Promise<void>
}

const defaultFilters: AttendanceReportFilters = {
  start: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
  end: new Date().toISOString().split('T')[0],
  shiftType: '',   // empty = no filter, show all shifts
}

export const useAttendanceReportStore = create<AttendanceReportState>((set, get) => ({
  rows: [],
  filters: defaultFilters,
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  loading: false,
  error: null,
  setFilters: (filters) => set({ filters, page: 0 }),
  setPage: (page) => set({ page }),
  fetchReport: async () => {
    set({ loading: true, error: null })
    try {
      const { filters, page, size } = get()
      const res = await attendanceApi.getReport({ ...filters, page, size })
      const payload: ApiResponse<PaginatedResponse<AttendanceReportRow>> = res.data
      const data = payload?.data
      set({
        rows: data?.content ?? [],
        totalElements: data?.totalElements ?? 0,
        totalPages: data?.totalPages ?? 0,
        loading: false,
      })
    } catch (e: unknown) {
      set({ error: (e as Error).message ?? 'Failed to load report', loading: false })
    }
  },
}))
