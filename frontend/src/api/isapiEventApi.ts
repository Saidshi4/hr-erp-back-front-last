import client from './client'

export interface PunchRecord {
  id: number
  deviceId: number
  employeeNo: string
  punchTime: string
  rawEventId: number | null
}

export interface RawEventRecord {
  id: number
  deviceId: number
  serialNo: number | null
  eventTime: string
  majorEventType: number | null
  subEventType: number | null
  employeeNoString: string | null
  cardNo: string | null
  rawJson: string | null
}

export interface FailedAttemptRecord {
  id: number
  deviceId: number
  identity: string | null
  subEventType: number | null
  eventTime: string
  rawEventId: number | null
}

export const isapiEventApi = {
  /**
   * Returns attendance punch records from ISAPI.
   * @param deviceId  optional ISAPI device ID filter
   * @param employeeNo optional employee number filter
   * @param limit     max results (default 50, max 500)
   */
  getPunches: (params?: { deviceId?: number; employeeNo?: string; limit?: number }) =>
    client.get<{ data: PunchRecord[] }>('/isapi/punches', { params }),

  /**
   * Returns raw ACS event records from ISAPI.
   * @param deviceId      optional ISAPI device ID filter
   * @param major         optional major event type filter
   * @param minor         optional sub-event type filter
   * @param includeRawJson include raw JSON payload when true
   * @param limit         max results (default 50, max 500)
   */
  getRawEvents: (params?: {
    deviceId?: number
    major?: number
    minor?: number
    includeRawJson?: boolean
    limit?: number
  }) => client.get<{ data: RawEventRecord[] }>('/isapi/raw-events', { params }),

  /**
   * Returns failed access-attempt records from ISAPI.
   * @param deviceId optional ISAPI device ID filter
   * @param limit    max results (default 50, max 500)
   */
  getFailedAttempts: (params?: { deviceId?: number; limit?: number }) =>
    client.get<{ data: FailedAttemptRecord[] }>('/isapi/failed-attempts', { params }),
}
