import { create } from 'zustand'
import { attendanceApi } from '../api/attendanceApi.ts'
import { AttendanceReportFilters, AttendanceReportRow, ApiResponse, PaginatedResponse } from '../types'
import { monthStartInAppTimeZone, todayInAppTimeZone } from '../utils/dateTime.ts'

interface AttendanceReportState {
  rows: AttendanceReportRow[]
  filters: AttendanceReportFilters
  page: number
  size: number
  totalElements: number
  totalPages: number
  loading: boolean
  error: string | null
  lastSyncedRange: string | null
  setFilters: (filters: AttendanceReportFilters) => void
  setPage: (page: number) => void
  fetchReport: () => Promise<void>
}

const defaultFilters: AttendanceReportFilters = {
  // Match Attendance page: Asia/Baku month-to-date (never UTC via toISOString).
  start: monthStartInAppTimeZone(),
  end: todayInAppTimeZone(),
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
  lastSyncedRange: null,
  setFilters: (filters) => set({ filters, page: 0 }),
  setPage: (page) => set({ page }),
  fetchReport: async () => {
    set({ loading: true, error: null })
    try {
      const { filters, page, size, lastSyncedRange } = get()
      const rangeKey = `${filters.start}|${filters.end}`
      // Sync once per date range so pagination does not re-hit devices every page flip.
      if (lastSyncedRange !== rangeKey) {
        try {
          await attendanceApi.syncAll({
            start: `${filters.start}T00:00:00`,
            end: `${filters.end}T23:59:59`,
          })
          set({ lastSyncedRange: rangeKey })
        } catch {
          // Still attempt to load existing local rows if sync is temporarily unavailable.
        }
      }
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
