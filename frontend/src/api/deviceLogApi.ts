import client from './client.ts'
import { ApiResponse } from '../types'

// ─────────────────────────────────────────────────────────────────────────────
// Type definitions
// ─────────────────────────────────────────────────────────────────────────────

export interface DeviceLogSearchParams {
  deviceId: number
  employeeId?: string
  name?: string
  cardNo?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

export interface DeviceLogEvent {
  employeeId?: string
  name?: string
  cardNo?: string
  eventDescription?: string
  time?: string
  hasPicture: boolean
  pictureURL?: string
  verifyMode?: string
}

export interface DeviceLogSearchResult {
  items: DeviceLogEvent[]
  totalMatches: number
  numOfMatches: number
  page: number
  pageSize: number
  /** "OK" = last page, "MORE" = more results exist, "NO MATCHES" = empty */
  responseStatus?: string
}

// ─────────────────────────────────────────────────────────────────────────────
// API functions
// ─────────────────────────────────────────────────────────────────────────────

export const deviceLogApi = {
  /**
   * Search for Access Control events on a specific Hikvision device.
   * The backend queries the device's ISAPI endpoint in real time — nothing
   * is read from or written to the application database.
   */
  search: (params: DeviceLogSearchParams) =>
    client.get<ApiResponse<DeviceLogSearchResult>>('/device-logs/search', { params }),

  /**
   * Fetch a picture from the Hikvision device via the backend proxy.
   * Returns a blob URL suitable for use in an <img> src attribute.
   *
   * The full pictureURL (including the trailing @WEB… device token) must be
   * passed exactly as returned by the search endpoint.
   */
  fetchPicture: async (pictureUrl: string, deviceId: number): Promise<string> => {
    // Pictures are served by the backend proxy endpoint which requires JWT auth.
    // Since <img src> cannot carry Authorization headers, we use fetch() with
    // the token from local storage and convert the response to a blob URL.
    const authStorage = localStorage.getItem('auth-storage')
    let token = ''
    if (authStorage) {
      try {
        const { state } = JSON.parse(authStorage)
        token = state?.token ?? ''
      } catch {
        // ignore parse errors
      }
    }

    const apiBase = (import.meta.env.VITE_API_URL as string | undefined) ?? ''
    const proxyUrl =
      `${apiBase}/api/device-logs/picture` +
      `?url=${encodeURIComponent(pictureUrl)}&deviceId=${deviceId}`

    const response = await fetch(proxyUrl, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })

    if (!response.ok) {
      throw new Error(`Failed to fetch picture (HTTP ${response.status})`)
    }

    const blob = await response.blob()
    return URL.createObjectURL(blob)
  },
}
