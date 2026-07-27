/** Asia/Baku wall-clock helpers for attendance timestamps. */
export const APP_TIME_ZONE = 'Asia/Baku'

/**
 * Format an attendance timestamp for display (HH:mm).
 * - Offset/Z values are converted to Asia/Baku.
 * - Bare LocalDateTime strings (no zone) are treated as Asia/Baku wall clock — never as UTC.
 */
export function formatAttendanceTime(value?: string | null): string {
  if (!value) return '—'

  const hasOffset = value.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(value)
  if (!hasOffset) {
    const match = value.match(/T(\d{2}):(\d{2})/)
    if (match) {
      return `${match[1]}:${match[2]}`
    }
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('az-AZ', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: APP_TIME_ZONE,
  })
}

/** Format date+time for dashboards; same zone rules as {@link formatAttendanceTime}. */
export function formatAttendanceDateTime(value?: string | null): string {
  if (!value) return '—'

  const hasOffset = value.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(value)
  if (!hasOffset) {
    const match = value.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/)
    if (match) {
      return `${match[1]} ${match[2]}:${match[3]}`
    }
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('az-AZ', { timeZone: APP_TIME_ZONE })
}
