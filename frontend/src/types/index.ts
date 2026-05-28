export interface User {
  id: number
  username: string
  email: string
  userType: 'HEAD_OFFICE_HR' | 'OFFICE_HR' | 'DEPARTMENT_HR' | 'EMPLOYEE'
  branchId?: number
  departmentId?: number
}

export interface Branch {
  id: number
  name: string
  code?: string
  city?: string
  address?: string
  status?: 'ACTIVE' | 'INACTIVE' | string
  isHeadOffice?: boolean
}

export interface Department {
  id: number
  departmentName: string
  description?: string
  branchId?: number
  parentDepartmentId?: number
  parentDepartmentName?: string
  employeeCount?: number
  createdAt?: string
  calculateOvertime?: boolean
  flexShift?: boolean
  timetable?: string
  timetableId?: number
}

export interface Position {
  id: number
  positionName: string
  description?: string
  departmentId: number
  departmentName?: string
  employeeCount?: number
  createdAt?: string
}

export interface Employee {
  id: number
  employeeId: string
  firstName: string
  lastName: string
  fatherName?: string
  birthDate?: string
  gender?: string
  mobilePhone?: string
  email?: string
  finNumber?: string
  faceId?: string
  cardId?: string
  serialNumber?: string
  contractNumber?: string
  branchId?: number
  branchName?: string
  contractEndDate?: string
  annualLeaveDuration?: number
  annualLeaveBalance?: number
  groupName?: string
  salary?: number
  hourlyRate?: number
  allowance?: string
  emergencyContact?: string
  address?: string
  notes?: string
  faceImageUrl?: string
  departmentId?: number
  departmentName?: string
  positionId?: number
  positionName?: string
  hireDate?: string
  area?: string
  shiftType?: string
  timetableId?: number
  deviceIds?: number[]
  employmentStatus: 'ACTIVE' | 'INACTIVE' | 'ON_LEAVE'
  createdAt?: string
  updatedAt?: string
}

export interface AttendanceLog {
  id: number
  employeeId: number
  checkInTime?: string
  checkOutTime?: string
  deviceId?: string
  eventType?: string
  status?: string
  createdAt?: string
}

export interface DoorAttendanceSyncResult {
  totalPunches: number
  matchedSessions: number
  createdLogs: number
  skippedEmployees: number
  recalculatedDays: number
}

export interface AccessLog {
  id: number
  deviceId?: number
  employeeNo?: string
  punchTime?: string
  rawEventId?: number
}

export interface DailyAttendanceSummary {
  id: number
  employeeId: number
  attendanceDate: string
  checkInTime?: string
  checkOutTime?: string
  hoursWorked?: number
  attendanceStatus?: 'PRESENT' | 'ABSENT' | 'LATE' | 'EARLY_LEAVE' | 'ON_LEAVE'
}

export interface LeaveRequest {
  id: number
  employeeId: number
  leaveTypeId: number
  startDate: string
  endDate: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approvedBy?: number
  approvalDate?: string
  createdAt?: string
}

export interface LeaveType {
  id: number
  leaveCode: string
  leaveName: string
  annualEntitlement?: number
  isPaid?: boolean
}

export interface Timetable {
  id: number
  tenantId?: number
  name: string
  description?: string
  startTime: string
  endTime: string
  crossesMidnight?: boolean
  allowedLateMinutes?: number
  allowedEarlyLeaveMinutes?: number
  shiftType?: string
}

export interface Holiday {
  id: number
  tenantId?: number
  name: string
  description?: string
  holidayDate: string
  applyScope?: string
  targetIds?: number[]
  scopeType?: string
}

export interface Permission {
  id: number
  tenantId?: number
  name: string
  description?: string
  leaveType: string
  applyType: string
  targetId?: number
  startDate: string
  endDate: string
  status: string
}

export interface PermissionType {
  id: number
  tenantId?: number
  code: string
  name: string
  isCustom?: boolean
}

export interface EmployeeShiftAssignment {
  id: number
  tenantId?: number
  employeeId: number
  timetableId: number
  effectiveStartDate: string
  effectiveEndDate?: string
  assignedBy?: number
  assignedAt?: string
  status: 'ACTIVE' | 'INACTIVE'
}

export interface EmployeePermission {
  id: number
  tenantId?: number
  employeeId: number
  permissionTypeId: number
  startDate: string
  endDate: string
  reason?: string
  status: 'ACTIVE' | 'INACTIVE' | 'APPROVED' | 'PENDING' | 'REJECTED'
  approvedBy?: number
  approvalDate?: string
  createdAt?: string
  updatedAt?: string
}

export interface AttendanceReportRow {
  employeePk: number
  employeeId: string
  photoUrl?: string
  fullName: string
  fin?: string
  department?: string
  position?: string
  area?: string
  date: string
  checkInTime?: string
  shiftType?: string
}

export interface AttendanceReportFilters {
  start: string
  end: string
  shiftType?: string
  employeeId?: string
  name?: string
  fin?: string
  position?: string
  department?: string
  area?: string
}

export interface DeviceConfig {
  id: number
  deviceId: string
  deviceName?: string
  deviceIp: string
  devicePort?: number
  username?: string
  branchId?: number
  status?: string
  lastSyncTime?: string
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  timestamp?: string
}

export interface LoginResponse {
  token: string
  refreshToken: string
  user: User
}
