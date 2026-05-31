import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Layout from '../components/Layout.tsx'
import EmployeeDetailModal from '../components/EmployeeDetailModal.tsx'
import { useEmployeeStore } from '../store/employeeStore.ts'
import { useBranchStore } from '../store/branchStore.ts'
import { Department, DeviceConfig, Employee, Position, Timetable } from '../types'
import { employeeApi } from '../api/employeeApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { positionApi } from '../api/positionApi.ts'
import { deviceApi } from '../api/deviceApi.ts'
import { deviceUserApi } from '../api/deviceUserApi.ts'
import { timetableApi } from '../api/timetableApi.ts'

interface EmployeeFormData {
  firstName: string
  lastName: string
  fatherName: string
  email: string
  mobilePhone: string
  gender: string
  finNumber: string
  serialNumber: string
  birthDate: string
  positionId: number | ''
  departmentId: number | ''
  contractNumber: string
  branchId: number | ''
  hireDate: string
  contractEndDate: string
  annualLeaveDuration: number | ''
  annualLeaveBalance: number | ''
  employmentStatus: string
  timetableId: number | ''
  shiftType: string
  cardId: string
  faceId: string
  groupName: string
  salary: number | ''
  hourlyRate: number | ''
  allowance: string
  emergencyContact: string
  address: string
  notes: string
  area: string
}

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const extractStatusCode = (value: unknown): number | undefined => {
  if (!isRecord(value)) return undefined
  const response = value.response
  if (!isRecord(response)) return undefined
  return typeof response.status === 'number' ? response.status : undefined
}

const normalizeDevice = (item: Record<string, unknown>): DeviceConfig => {
  const parsedId = typeof item.id === 'number' ? item.id : Number(item.id)
  const id = Number.isFinite(parsedId) ? parsedId : 0
  const fallbackDeviceId = id > 0 ? String(id) : 'unknown'
  const deviceId = typeof item.deviceId === 'string' && item.deviceId.trim() !== '' ? item.deviceId : fallbackDeviceId
  const deviceName = typeof item.deviceName === 'string' ? item.deviceName : typeof item.name === 'string' ? item.name : undefined
  const deviceIp = typeof item.deviceIp === 'string' ? item.deviceIp : typeof item.ip === 'string' ? item.ip : ''
  const devicePort = typeof item.devicePort === 'number' ? item.devicePort : undefined
  const username = typeof item.username === 'string' ? item.username : undefined
  const branchId = typeof item.branchId === 'number' ? item.branchId : undefined
  const status = typeof item.status === 'string'
    ? item.status
    : typeof item.running === 'boolean'
      ? (item.running ? 'ACTIVE' : 'INACTIVE')
      : typeof item.enabled === 'boolean'
        ? (item.enabled ? 'ACTIVE' : 'INACTIVE')
        : undefined
  const lastSyncTime = typeof item.lastSyncTime === 'string' ? item.lastSyncTime : undefined

  return {
    id,
    deviceId,
    deviceName,
    deviceIp,
    devicePort,
    username,
    branchId,
    status,
    lastSyncTime,
  }
}

const extractDevices = (payload: unknown): DeviceConfig[] => {
  const list = Array.isArray(payload)
    ? payload
    : isRecord(payload)
      ? payload.data
      : undefined

  return Array.isArray(list) ? list.filter(isRecord).map(normalizeDevice).filter((d) => d.id > 0) : []
}

const defaultForm: EmployeeFormData = {
  firstName: '',
  lastName: '',
  fatherName: '',
  email: '',
  mobilePhone: '',
  gender: '',
  finNumber: '',
  serialNumber: '',
  birthDate: '',
  positionId: '',
  departmentId: '',
  contractNumber: '',
  branchId: '',
  hireDate: new Date().toISOString().split('T')[0],
  contractEndDate: '',
  annualLeaveDuration: 30,
  annualLeaveBalance: 30,
  employmentStatus: 'ACTIVE',
  timetableId: '',
  shiftType: '',
  cardId: '',
  faceId: '',
  groupName: '',
  salary: '',
  hourlyRate: '',
  allowance: '',
  emergencyContact: '',
  address: '',
  notes: '',
  area: '',
}

const AVATAR_COLORS = ['#6366f1', '#a855f7', '#10b981', '#f59e0b', '#ef4444', '#3b82f6']

function getAvatarColor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

export default function EmployeesPage() {
  const navigate = useNavigate()
  const defaultDeviceId = Number(import.meta.env.VITE_DEFAULT_DEVICE_ID || 1)
  const { employees, loading, error, fetchEmployees, deleteEmployee, totalPages, currentPage, totalElements } = useEmployeeStore()
  const { branches, fetchBranches } = useBranchStore()
  const [search, setSearch] = useState('')
  const [departments, setDepartments] = useState<Department[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [timetables, setTimetables] = useState<Timetable[]>([])
  const [devices, setDevices] = useState<DeviceConfig[]>([])
  const [deviceSearch, setDeviceSearch] = useState('')
  const [selectedDeviceIds, setSelectedDeviceIds] = useState<number[]>([])
  const [filterDept, setFilterDept] = useState<string>('')
  const [filterStatus, setFilterStatus] = useState<string>('')
  const [filterShift, setFilterShift] = useState<string>('')
  const [filterArea, setFilterArea] = useState<string>('')
  const [showWizard, setShowWizard] = useState(false)
  const [currentStep, setCurrentStep] = useState(1)
  const [editingEmployee, setEditingEmployee] = useState<Employee | null>(null)
  const [form, setForm] = useState<EmployeeFormData>(defaultForm)
  const [departmentQuery, setDepartmentQuery] = useState('')
  const [positionQuery, setPositionQuery] = useState('')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<Employee | null>(null)
  const [uploadingFaceEmployeeId, setUploadingFaceEmployeeId] = useState<number | null>(null)
  const [deletingFaceEmployeeId, setDeletingFaceEmployeeId] = useState<number | null>(null)
  const [uploadFaceError, setUploadFaceError] = useState<string | null>(null)
  const [showProfileModal, setShowProfileModal] = useState(false)
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [selectedEmployee, setSelectedEmployee] = useState<Employee | null>(null)
  const [profileImageSrc, setProfileImageSrc] = useState<string | null>(null)
  const [wizardImagePreview, setWizardImagePreview] = useState<string | null>(null)
  const [wizardImageFile, setWizardImageFile] = useState<File | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    fetchEmployees(0, 20)
    departmentApi.getAll().then((res) => setDepartments(res.data?.data ?? []))
    positionApi.getAll().then((res) => setPositions(res.data?.data ?? []))
    fetchBranches()
    timetableApi.getAll().then((res) => setTimetables(res.data?.data ?? []))
    deviceApi.getAll().then((res) => setDevices(extractDevices(res.data)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!showWizard && wizardImagePreview) {
      URL.revokeObjectURL(wizardImagePreview)
      setWizardImagePreview(null)
      setWizardImageFile(null)
    }
  }, [showWizard, wizardImagePreview])

  const formatDate = (value?: string) => {
    if (!value) return '—'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleDateString('az-AZ')
  }

  const statusLabel = (status: Employee['employmentStatus']) => {
    if (status === 'ACTIVE') return 'Aktiv'
    if (status === 'ON_LEAVE') return 'Məzuniyyətdə'
    return 'Deaktiv'
  }

  const stepTitles = ['General Identity', 'Work Information', 'Card, Fingerprint & Photo', 'Compensation']

  const employeeIdPreview = useMemo(() => {
    if (editingEmployee?.employeeId) return editingEmployee.employeeId
    return `EMP${String((totalElements || employees.length) + 1).padStart(4, '0')}`
  }, [editingEmployee, totalElements, employees.length])

  const branchLabelById = (branchId?: number) => {
    if (!branchId) return '—'
    return branches.find((b) => b.id === branchId)?.name || '—'
  }

  const filteredDevices = devices.filter((d) => {
    const label = `${d.deviceName || ''} ${d.deviceId || ''}`.toLowerCase()
    return label.includes(deviceSearch.toLowerCase())
  })

  const selectedDeviceLabels = selectedDeviceIds
    .map((id) => {
      const device = devices.find((d) => d.id === id)
      return device ? (device.deviceName || device.deviceId) : ''
    })
    .filter(Boolean)

  const openCreate = () => {
    setEditingEmployee(null)
    setForm(defaultForm)
    setDepartmentQuery('')
    setPositionQuery('')
    setSelectedDeviceIds([])
    setCurrentStep(1)
    setFormError(null)
    setShowWizard(true)
  }

  const openEdit = (emp: Employee) => {
    setEditingEmployee(emp)
    setForm({
      firstName: emp.firstName,
      lastName: emp.lastName,
      fatherName: emp.fatherName || '',
      email: emp.email || '',
      mobilePhone: emp.mobilePhone || '',
      gender: emp.gender || '',
      finNumber: emp.finNumber || '',
      serialNumber: emp.serialNumber || '',
      birthDate: emp.birthDate || '',
      positionId: emp.positionId || '',
      departmentId: emp.departmentId || '',
      contractNumber: emp.contractNumber || '',
      branchId: emp.branchId || '',
      hireDate: emp.hireDate || defaultForm.hireDate,
      contractEndDate: emp.contractEndDate || '',
      annualLeaveDuration: emp.annualLeaveDuration ?? 30,
      annualLeaveBalance: emp.annualLeaveBalance ?? 30,
      employmentStatus: emp.employmentStatus || 'ACTIVE',
      timetableId: emp.timetableId || '',
      shiftType: emp.shiftType || '',
      cardId: emp.cardId || '',
      faceId: emp.faceId || '',
      groupName: emp.groupName || '',
      salary: emp.salary ?? '',
      hourlyRate: emp.hourlyRate ?? '',
      allowance: emp.allowance || '',
      emergencyContact: emp.emergencyContact || '',
      address: emp.address || '',
      notes: emp.notes || '',
      area: emp.area || '',
    })
    setDepartmentQuery(emp.departmentName || '')
    setPositionQuery(emp.positionName || '')
    const areaParts = (emp.area || '')
      .split(',')
      .map((x) => x.trim())
      .filter(Boolean)
    const matched = emp.deviceIds?.length
      ? emp.deviceIds
      : devices
          .filter((d) => areaParts.includes(d.deviceName || '') || areaParts.includes(d.deviceId || ''))
          .map((d) => d.id)
    setSelectedDeviceIds(matched)
    setCurrentStep(1)
    setFormError(null)
    setShowWizard(true)
  }

  const closeWizard = () => {
    setShowWizard(false)
    setCurrentStep(1)
    setFormError(null)
  }

  const openProfile = async (employee: Employee) => {
    setShowProfileModal(true)
    setProfileLoading(true)
    setProfileError(null)
    setSelectedEmployee(employee)
    try {
      let res = await employeeApi.getById(employee.id)
      if (res.data?.data) {
        let details = res.data.data
        if (!details.faceImageUrl) {
          const usersRes = await deviceUserApi.getAll(defaultDeviceId)
          const deviceUser = usersRes.data.find((u) => u.employeeNo === details.employeeId)
          if (deviceUser) {
            await deviceUserApi.syncFaceFromDevice(defaultDeviceId, deviceUser.id, details.id)
            res = await employeeApi.getById(employee.id)
            details = res.data?.data || details
          }
        }
        if (profileImageSrc) {
          URL.revokeObjectURL(profileImageSrc)
          setProfileImageSrc(null)
        }
        if (details.faceImageUrl) {
          const token = localStorage.getItem('token')
          const imageResponse = await fetch(details.faceImageUrl, {
            headers: token ? { Authorization: 'Bearer ' + token } : {},
          })
          if (imageResponse.ok) {
            const blob = await imageResponse.blob()
            setProfileImageSrc(URL.createObjectURL(blob))
          }
        }
        setSelectedEmployee(details)
      }
    } catch (e: unknown) {
      setProfileError((e as Error).message || 'Əməkdaş məlumatları yüklənmədi')
    } finally {
      setProfileLoading(false)
    }
  }

  const closeProfile = () => {
    if (profileImageSrc) {
      URL.revokeObjectURL(profileImageSrc)
    }
    setProfileImageSrc(null)
    setShowProfileModal(false)
    setProfileError(null)
  }

  const setFormField = <K extends keyof EmployeeFormData>(key: K, value: EmployeeFormData[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  const uploadFaceForEmployee = async (employee: Employee, file: File | null) => {
    if (!file) return
    const usersRes = await deviceUserApi.getAll(defaultDeviceId)
    const deviceUser = usersRes.data.find((u) => u.employeeNo === employee.employeeId)
    if (!deviceUser) {
      throw new Error(`Cihaz user tapılmadı (${employee.employeeId})`)
    }
    await deviceUserApi.uploadFace(defaultDeviceId, deviceUser.id, file, employee.id)
  }

  const handleSave = async () => {
    if (!form.firstName.trim() || !form.lastName.trim()) {
      setFormError('Ad və soyad mütləqdir.')
      return
    }
    if (!form.departmentId) {
      setFormError('Departament mütləqdir.')
      return
    }
    if (!form.timetableId) {
      setFormError('İş qrafiki seçilməsi mütləqdir.')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const payload: Partial<Employee> = {
        firstName: form.firstName,
        lastName: form.lastName,
        fatherName: form.fatherName,
        email: form.email,
        mobilePhone: form.mobilePhone,
        gender: form.gender,
        finNumber: form.finNumber,
        serialNumber: form.serialNumber,
        birthDate: form.birthDate || undefined,
        departmentId: Number(form.departmentId),
        positionId: form.positionId ? Number(form.positionId) : undefined,
        contractNumber: form.contractNumber,
        branchId: form.branchId ? Number(form.branchId) : undefined,
        hireDate: form.hireDate,
        contractEndDate: form.contractEndDate || undefined,
        annualLeaveDuration: form.annualLeaveDuration === '' ? undefined : Number(form.annualLeaveDuration),
        annualLeaveBalance: form.annualLeaveBalance === '' ? undefined : Number(form.annualLeaveBalance),
        employmentStatus: form.employmentStatus as 'ACTIVE' | 'INACTIVE' | 'ON_LEAVE',
        timetableId: Number(form.timetableId),
        shiftType: form.shiftType,
        cardId: form.cardId,
        faceId: form.faceId,
        groupName: form.groupName,
        salary: form.salary === '' ? undefined : Number(form.salary),
        hourlyRate: form.hourlyRate === '' ? undefined : Number(form.hourlyRate),
        allowance: form.allowance,
        emergencyContact: form.emergencyContact,
        address: form.address,
        notes: form.notes,
        area: selectedDeviceLabels.length ? selectedDeviceLabels.join(', ') : form.area,
        deviceIds: selectedDeviceIds,
      }

      let savedEmployee: Employee | undefined
      if (editingEmployee) {
        const res = await employeeApi.update(editingEmployee.id, payload)
        savedEmployee = res.data?.data
      } else {
        const res = await employeeApi.create(payload)
        savedEmployee = res.data?.data
      }

      if (savedEmployee && wizardImageFile) {
        await uploadFaceForEmployee(savedEmployee, wizardImageFile)
      }

      await fetchEmployees(currentPage, 20)
      closeWizard()
      if (showProfileModal && selectedEmployee?.id === savedEmployee?.id) {
        openProfile(savedEmployee)
      }
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Saxlamaq alınmadı')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteConfirm) return
    try {
      await deleteEmployee(deleteConfirm.id)
      setDeleteConfirm(null)
      if (selectedEmployee?.id === deleteConfirm.id) {
        closeProfile()
      }
    } catch {
      // handled by store
    }
  }

  const handleSearch = () => {
    if (!search.trim()) {
      fetchEmployees(0, 20)
    }
  }

  const handleFaceUpload = async (employee: Employee, file: File | undefined, input?: HTMLInputElement) => {
    if (!file) return
    setUploadFaceError(null)
    setUploadingFaceEmployeeId(employee.id)
    try {
      await uploadFaceForEmployee(employee, file)
      await fetchEmployees(currentPage, 20)
    } catch (e: unknown) {
      setUploadFaceError((e as Error).message || 'Şəkil yüklənmədi')
    } finally {
      setUploadingFaceEmployeeId(null)
      if (input) {
        input.value = ''
      }
    }
  }

  const handleFaceDelete = async (employee: Employee) => {
    setUploadFaceError(null)
    setDeletingFaceEmployeeId(employee.id)
    try {
      const usersRes = await deviceUserApi.getAll(defaultDeviceId)
      const deviceUser = usersRes.data.find((u) => u.employeeNo === employee.employeeId)
      if (!deviceUser) {
        throw new Error(`Cihaz user tapılmadı (${employee.employeeId})`)
      }
      await deviceUserApi.deleteFace(defaultDeviceId, deviceUser.id, employee.id)
      await fetchEmployees(currentPage, 20)
      if (selectedEmployee?.id === employee.id) {
        await openProfile(employee)
      }
    } catch (e: unknown) {
      if (extractStatusCode(e) === 404) {
        await fetchEmployees(currentPage, 20)
        if (selectedEmployee?.id === employee.id) {
          await openProfile(employee)
        }
      } else {
        setUploadFaceError((e as Error).message || 'Şəkil silinmədi')
      }
    } finally {
      setDeletingFaceEmployeeId(null)
    }
  }

  const captureFromCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true })
      const video = document.createElement('video')
      video.srcObject = stream
      await video.play()
      await new Promise((resolve) => setTimeout(resolve, 700))

      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth || 480
      canvas.height = video.videoHeight || 480
      const context = canvas.getContext('2d')
      if (!context) throw new Error('Kamera görüntüsü alınmadı')
      context.drawImage(video, 0, 0, canvas.width, canvas.height)

      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg'))
      stream.getTracks().forEach((track) => track.stop())

      if (!blob) throw new Error('Şəkil yaradılmadı')
      const file = new File([blob], `camera-${Date.now()}.jpg`, { type: 'image/jpeg' })
      if (wizardImagePreview) {
        URL.revokeObjectURL(wizardImagePreview)
      }
      setWizardImageFile(file)
      setWizardImagePreview(URL.createObjectURL(file))
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Kamera ilə şəkil çəkilmədi')
    }
  }

  const onWizardFileSelect = (file?: File) => {
    if (!file) return
    if (wizardImagePreview) {
      URL.revokeObjectURL(wizardImagePreview)
    }
    setWizardImageFile(file)
    setWizardImagePreview(URL.createObjectURL(file))
  }

  const filtered = employees.filter((e: Employee) => {
    const matchSearch = !search || `${e.firstName} ${e.lastName}`.toLowerCase().includes(search.toLowerCase()) || e.employeeId.toLowerCase().includes(search.toLowerCase())
    const matchDept = !filterDept || String(e.departmentId) === filterDept
    const matchStatus = !filterStatus || e.employmentStatus === filterStatus
    const matchShift = !filterShift || e.shiftType === filterShift
    const matchArea = !filterArea || e.area === filterArea
    return matchSearch && matchDept && matchStatus && matchShift && matchArea
  })

  const uniqueAreas = Array.from(new Set(employees.map((e) => e.area).filter(Boolean))) as string[]
  const uniqueShifts = Array.from(new Set(employees.map((e) => e.shiftType).filter(Boolean))) as string[]

  const activeCount = employees.filter((e) => e.employmentStatus === 'ACTIVE').length
  const onLeaveCount = employees.filter((e) => e.employmentStatus === 'ON_LEAVE').length

  return (
    <Layout>
      <div className="p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex items-start justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Bütün əməkdaşlar</h1>
            <p className="text-sm text-gray-500 mt-1">
              <span className="font-medium text-gray-700">{totalElements ?? employees.length}</span> ümumi ·&nbsp;
              <span className="text-green-600 font-medium">{activeCount} aktiv</span> ·&nbsp;
              <span className="text-yellow-600 font-medium">{onLeaveCount} məzuniyyətdə</span>
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => fetchEmployees(currentPage, 20)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Yenilə
            </button>
            <button
              onClick={openCreate}
              className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-lg"
              style={{ background: '#a855f7' }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              + Əməkdaş əlavə et
            </button>
          </div>
        </div>

        {/* Search + Filters */}
        <div className="bg-white rounded-xl shadow-sm p-4 mb-4 flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 flex-1 min-w-[200px] border border-gray-200 rounded-lg px-3 py-2">
            <svg className="w-4 h-4 text-gray-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Əməkdaş axtar..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className="flex-1 outline-none text-sm text-gray-700 placeholder-gray-400"
            />
            {search && (
              <button onClick={() => { setSearch(''); fetchEmployees(0, 20) }} className="text-gray-400 hover:text-gray-600">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            )}
          </div>

          <select value={filterDept} onChange={e => setFilterDept(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün departamentlər</option>
            {departments.map(d => <option key={d.id} value={String(d.id)}>{d.departmentName}</option>)}
          </select>

          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün statuslar</option>
            <option value="ACTIVE">Aktiv</option>
            <option value="INACTIVE">Deaktiv</option>
            <option value="ON_LEAVE">Məzuniyyətdə</option>
          </select>

          <select value={filterShift} onChange={e => setFilterShift(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün növbələr</option>
            {uniqueShifts.map(s => <option key={s} value={s}>{s}</option>)}
          </select>

          <select value={filterArea} onChange={e => setFilterArea(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün ərazilər</option>
            {uniqueAreas.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
        </div>

        {/* Table */}
        {uploadFaceError && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">{uploadFaceError}</div>
        )}
        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Yüklənir...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : filtered.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            {search ? 'Axtarışa uyğun əməkdaş tapılmadı.' : 'Hələ heç bir əməkdaş yoxdur.'}
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr style={{ background: '#f9fafb' }}>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ƏMƏLİYYATLAR</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ŞƏKİL</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">EMPLOYEE ID</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">AD VƏ SOYAD</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ATA ADI</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">DEPARTAMENT</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">VƏZİFƏ</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ƏRAZİ</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">STATUS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map((emp: Employee) => {
                  const name = `${emp.firstName} ${emp.lastName}`
                  const initials = `${emp.firstName.charAt(0)}${emp.lastName.charAt(0)}`.toUpperCase()
                  const avatarColor = getAvatarColor(name)
                  return (
                    <tr key={emp.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5">
                          <button
                            onClick={() => openProfile(emp)}
                            className="p-1.5 rounded hover:bg-violet-50 transition-colors"
                            title="Məlumatlara bax"
                          >
                            <svg className="w-4 h-4" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5s8.268 2.943 9.542 7c-1.274 4.057-5.065 7-9.542 7S3.732 16.057 2.458 12z" />
                            </svg>
                          </button>
                          <button
                            onClick={() => openEdit(emp)}
                            className="p-1.5 rounded hover:bg-purple-50 transition-colors"
                            title="Redaktə et"
                          >
                            <svg className="w-4 h-4" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                          </button>
                          <label className="p-1.5 rounded hover:bg-blue-50 transition-colors cursor-pointer" title="Şəkil yüklə">
                            <input
                              type="file"
                              accept="image/*"
                              className="hidden"
                              aria-label={`${emp.firstName} ${emp.lastName} üçün şəkil yüklə`}
                              onChange={(e) => {
                                handleFaceUpload(emp, e.target.files?.[0], e.currentTarget)
                              }}
                            />
                            <svg className={`w-4 h-4 ${uploadingFaceEmployeeId === emp.id ? 'animate-pulse' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ color: '#2563eb' }}>
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 16l4-4a3 3 0 014.243 0L15 15.757m-2-2 1.586-1.586a3 3 0 014.243 0L21 14m-6-10h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                            </svg>
                          </label>
                          <button
                            onClick={() => handleFaceDelete(emp)}
                            className="p-1.5 rounded hover:bg-amber-50 transition-colors"
                            title="Şəkili sil"
                            disabled={deletingFaceEmployeeId === emp.id}
                          >
                            <svg className={`w-4 h-4 ${deletingFaceEmployeeId === emp.id ? 'animate-pulse' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ color: '#d97706' }}>
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h10m-9 4h8m-7 4h6M9 3h6l1 2h4v2H4V5h4l1-2zM6 7h12l-1 13a2 2 0 01-2 2H9a2 2 0 01-2-2L6 7z" />
                            </svg>
                          </button>
                          <button
                            onClick={() => setDeleteConfirm(emp)}
                            className="p-1.5 rounded hover:bg-red-50 transition-colors"
                            title="Sil"
                          >
                            <svg className="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="w-9 h-9 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0" style={{ background: avatarColor }}>
                          {initials}
                        </div>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600">{emp.employeeId}</td>
                      <td className="px-4 py-3 font-semibold text-gray-800">{name}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.fatherName || '—'}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.departmentName || '—'}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.positionName || '—'}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.area || '—'}</td>
                      <td className="px-4 py-3">
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium"
                          style={emp.employmentStatus === 'ACTIVE'
                            ? { background: '#d1fae5', color: '#065f46' }
                            : emp.employmentStatus === 'ON_LEAVE'
                            ? { background: '#fef3c7', color: '#92400e' }
                            : { background: '#fee2e2', color: '#991b1b' }}
                        >
                          {emp.employmentStatus === 'ACTIVE' ? 'Aktiv' : emp.employmentStatus === 'ON_LEAVE' ? 'Məzuniyyətdə' : 'Deaktiv'}
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <button onClick={() => fetchEmployees(currentPage - 1)} disabled={currentPage === 0} className="px-4 py-2 text-sm border border-gray-300 bg-white rounded-lg disabled:opacity-50 hover:bg-gray-50">
              Əvvəlki
            </button>
            <span className="text-sm text-gray-500">Səhifə {currentPage + 1} / {totalPages}</span>
            <button onClick={() => fetchEmployees(currentPage + 1)} disabled={currentPage >= totalPages - 1} className="px-4 py-2 text-sm border border-gray-300 bg-white rounded-lg disabled:opacity-50 hover:bg-gray-50">
              Növbəti
            </button>
          </div>
        )}
      </div>

      {showWizard && (
        <div className="fixed inset-0 z-50 bg-black/50 p-4 md:p-6 overflow-y-auto">
          <div className="min-h-full rounded-2xl bg-white p-6 md:p-8">
            <div className="flex items-start justify-between mb-6">
              <div>
                <h2 className="text-2xl font-bold text-gray-900">{editingEmployee ? 'Əməkdaşı redaktə et' : 'Yeni əməkdaş əlavə et'}</h2>
                <p className="text-sm text-gray-500 mt-1">4 addımda əməkdaş məlumatlarını tamamlayın</p>
              </div>
              <button onClick={closeWizard} className="p-2 rounded-lg hover:bg-gray-100 text-gray-500">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 mb-6">
              {stepTitles.map((title, idx) => {
                const step = idx + 1
                const isActive = step === currentStep
                const isCompleted = step < currentStep
                return (
                  <button
                    type="button"
                    key={title}
                    onClick={() => setCurrentStep(step)}
                    className="border rounded-xl p-3 flex items-center gap-3 text-left cursor-pointer"
                    style={
                      isCompleted
                        ? { borderColor: '#86efac', background: '#f0fdf4' }
                        : isActive
                        ? { borderColor: '#a855f7', background: '#f5edff' }
                        : { borderColor: '#e5e7eb', background: '#ffffff' }
                    }
                  >
                    <div
                      className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold"
                      style={
                        isCompleted
                          ? { background: '#16a34a', color: '#ffffff' }
                          : isActive
                          ? { background: '#a855f7', color: '#ffffff' }
                          : { background: '#e5e7eb', color: '#6b7280' }
                      }
                    >
                      {isCompleted ? '✓' : step}
                    </div>
                    <div className="text-xs font-semibold text-gray-700">{title}</div>
                  </button>
                )
              })}
            </div>

            {formError && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">{formError}</div>
            )}

            {currentStep === 1 && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">INTERNAL EMPLOYEE ID</label>
                  <input value={employeeIdPreview} readOnly className="w-full border border-gray-300 rounded-lg px-3 py-2 bg-gray-50 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">FIN</label>
                  <input value={form.finNumber} onChange={(e) => setFormField('finNumber', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">FIRST NAME*</label>
                  <input value={form.firstName} onChange={(e) => setFormField('firstName', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">LAST NAME*</label>
                  <input value={form.lastName} onChange={(e) => setFormField('lastName', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">EMAIL</label>
                  <input type="email" value={form.email} onChange={(e) => setFormField('email', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">FATHER'S NAME</label>
                  <input value={form.fatherName} onChange={(e) => setFormField('fatherName', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">SERIAL NUMBER</label>
                  <input value={form.serialNumber} onChange={(e) => setFormField('serialNumber', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">DATE OF BIRTH</label>
                  <input type="date" value={form.birthDate} onChange={(e) => setFormField('birthDate', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
              </div>
            )}

            {currentStep === 2 && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">POSITION</label>
                  <input
                    list="positions-list"
                    value={positionQuery}
                    onChange={(e) => {
                      const value = e.target.value
                      setPositionQuery(value)
                      const found = positions.find((p) => p.positionName === value)
                      setFormField('positionId', found?.id ?? '')
                    }}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                  />
                  <datalist id="positions-list">
                    {positions
                      .filter((p) => !form.departmentId || p.departmentId === Number(form.departmentId))
                      .map((p) => (
                        <option key={p.id} value={p.positionName} />
                      ))}
                  </datalist>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">DEPARTMENT*</label>
                  <input
                    list="departments-list"
                    value={departmentQuery}
                    onChange={(e) => {
                      const value = e.target.value
                      setDepartmentQuery(value)
                      const found = departments.find((d) => d.departmentName === value)
                      setFormField('departmentId', found?.id ?? '')
                    }}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                  />
                  <datalist id="departments-list">
                    {departments.map((d) => (
                      <option key={d.id} value={d.departmentName} />
                    ))}
                  </datalist>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">CONTRACT NUMBER</label>
                  <input value={form.contractNumber} onChange={(e) => setFormField('contractNumber', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">BRANCH / OFFICE LOCATION</label>
                  <select value={form.branchId} onChange={(e) => setFormField('branchId', e.target.value ? Number(e.target.value) : '')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                    <option value="">Seçin...</option>
                    {branches.map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">EMPLOYMENT START DATE</label>
                  <input type="date" value={form.hireDate} onChange={(e) => setFormField('hireDate', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">CONTRACT END DATE</label>
                  <input type="date" value={form.contractEndDate} onChange={(e) => setFormField('contractEndDate', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">ANNUAL LEAVE DURATION</label>
                  <input type="number" value={form.annualLeaveDuration} onChange={(e) => setFormField('annualLeaveDuration', e.target.value ? Number(e.target.value) : '')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">ANNUAL LEAVE BALANCE</label>
                  <input type="number" value={form.annualLeaveBalance} onChange={(e) => setFormField('annualLeaveBalance', e.target.value ? Number(e.target.value) : '')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">EMPLOYMENT STATUS</label>
                  <select value={form.employmentStatus} onChange={(e) => setFormField('employmentStatus', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                    <option value="ACTIVE">Aktiv</option>
                    <option value="INACTIVE">Deaktiv</option>
                    <option value="ON_LEAVE">Məzuniyyətdə</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">TIMETABLE</label>
                  <select
                    value={form.timetableId}
                    onChange={(e) => {
                      const value = e.target.value ? Number(e.target.value) : ''
                      setFormField('timetableId', value)
                      if (value !== '') {
                        const selectedTimetable = timetables.find((t) => t.id === value)
                        if (selectedTimetable?.shiftType) {
                          setFormField('shiftType', selectedTimetable.shiftType)
                        }
                      }
                    }}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                  >
                    <option value="">Seçin...</option>
                    {timetables.map((t) => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">SHIFT TYPE</label>
                  <input value={form.shiftType} readOnly className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-gray-50" />
                </div>
              </div>
            )}

            {currentStep === 3 && (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div>
                  <div className="flex gap-2 mb-4">
                    <button type="button" onClick={() => setFormField('cardId', `CARD-${Date.now()}`)} className="px-3 py-2 text-sm text-white rounded-lg" style={{ background: '#a855f7' }}>Add Card</button>
                    <button type="button" onClick={() => setFormField('faceId', `FP-${Date.now()}`)} className="px-3 py-2 text-sm text-gray-700 rounded-lg border border-gray-300 bg-gray-100">Add Fingerprint</button>
                  </div>
                  <div className="grid grid-cols-1 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-gray-500 mb-1">CARD ASSIGNMENT</label>
                      <input value={form.cardId} readOnly className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-gray-50" />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-500 mb-1">FINGERPRINT ASSIGNMENT</label>
                      <input value={form.faceId} readOnly className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-gray-50" />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-500 mb-1">EMPLOYMENT STATUS</label>
                      <select value={form.employmentStatus} onChange={(e) => setFormField('employmentStatus', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                        <option value="ACTIVE">Aktiv</option>
                        <option value="INACTIVE">Deaktiv</option>
                        <option value="ON_LEAVE">Məzuniyyətdə</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-500 mb-1">SHIFT TYPE</label>
                      <input value={form.shiftType} readOnly className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-gray-50" />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-500 mb-1">GROUP</label>
                      <input value={form.groupName} onChange={(e) => setFormField('groupName', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                    </div>
                  </div>

                  <div className="mt-5">
                    <h4 className="text-sm font-bold text-gray-700 mb-2">Area / Device Assignment</h4>
                    <input
                      value={deviceSearch}
                      onChange={(e) => setDeviceSearch(e.target.value)}
                      placeholder="Device axtar..."
                      className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm mb-2"
                    />
                    <div className="h-44 overflow-y-auto border border-gray-200 rounded-lg p-2 space-y-1">
                      {filteredDevices.map((d) => (
                        <label key={d.id} className="flex items-center gap-2 text-sm text-gray-700">
                          <input
                            type="checkbox"
                            checked={selectedDeviceIds.includes(d.id)}
                            onChange={(e) => {
                              setSelectedDeviceIds((prev) =>
                                e.target.checked ? [...prev, d.id] : prev.filter((id) => id !== d.id),
                              )
                            }}
                          />
                          <span>{d.deviceName || d.deviceId}</span>
                        </label>
                      ))}
                      {!filteredDevices.length && <p className="text-xs text-gray-500 px-1 py-2">No devices found.</p>}
                    </div>
                    <p className="text-xs text-gray-500 mt-2">{selectedDeviceIds.length} devices selected</p>
                  </div>
                </div>

                <div>
                  <div className="w-full aspect-square border-2 border-dashed border-gray-300 rounded-xl overflow-hidden flex items-center justify-center bg-gray-50">
                    {wizardImagePreview ? (
                      <img src={wizardImagePreview} alt="Employee" className="w-full h-full object-cover" />
                    ) : (
                      <div className="text-center text-gray-400">
                        <svg className="w-10 h-10 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7h4l2-2h6l2 2h4v12H3V7zm9 3a4 4 0 100 8 4 4 0 000-8z" />
                        </svg>
                        <p>Şəkil seçilməyib</p>
                      </div>
                    )}
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2 mt-3">
                    <button type="button" onClick={captureFromCamera} className="px-3 py-2 text-sm text-white rounded-lg flex items-center justify-center gap-2" style={{ background: '#a855f7' }}>
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7h4l2-2h6l2 2h4v12H3V7zm9 3a4 4 0 100 8 4 4 0 000-8z" /></svg>
                      Cihazdan şəkil çək
                    </button>
                    <button type="button" onClick={() => fileInputRef.current?.click()} className="px-3 py-2 text-sm text-gray-700 rounded-lg flex items-center justify-center gap-2 border border-gray-300 bg-gray-100">
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01.88-7.903A5 5 0 0115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" /></svg>
                      Kompüterdən yüklə
                    </button>
                    <input ref={fileInputRef} type="file" accept="image/jpeg,image/png" className="hidden" onChange={(e) => onWizardFileSelect(e.target.files?.[0])} />
                  </div>
                </div>
              </div>
            )}

            {currentStep === 4 && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">SALARY</label>
                  <input type="number" value={form.salary} onChange={(e) => setFormField('salary', e.target.value ? Number(e.target.value) : '')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">HOURLY RATE</label>
                  <input type="number" value={form.hourlyRate} onChange={(e) => setFormField('hourlyRate', e.target.value ? Number(e.target.value) : '')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">OPTIONAL ALLOWANCE</label>
                  <input value={form.allowance} onChange={(e) => setFormField('allowance', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">EMERGENCY CONTACT</label>
                  <input value={form.emergencyContact} onChange={(e) => setFormField('emergencyContact', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">PHONE NUMBER</label>
                  <input value={form.mobilePhone} onChange={(e) => setFormField('mobilePhone', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">ADDRESS</label>
                  <input value={form.address} onChange={(e) => setFormField('address', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-xs font-semibold text-gray-500 mb-1">OPTIONAL NOTES</label>
                  <textarea value={form.notes} onChange={(e) => setFormField('notes', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm h-24 resize-none" />
                </div>
              </div>
            )}

            <div className="flex items-center justify-between mt-8">
              <button onClick={closeWizard} className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Ləğv et</button>
              <div className="flex gap-2">
                {currentStep > 1 && (
                  <button onClick={() => setCurrentStep((s) => s - 1)} className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Əvvəlki</button>
                )}
                {currentStep < 4 ? (
                  <button onClick={() => setCurrentStep((s) => s + 1)} className="px-4 py-2 text-sm text-white rounded-lg" style={{ background: '#a855f7' }}>Növbəti</button>
                ) : (
                  <button onClick={handleSave} disabled={saving} className="px-4 py-2 text-sm text-white rounded-lg disabled:opacity-50" style={{ background: '#a855f7' }}>
                    {saving ? 'Saxlanılır...' : 'Yadda saxla'}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {showProfileModal && selectedEmployee && (
        <EmployeeDetailModal
          employee={selectedEmployee}
          profileImageSrc={profileImageSrc}
          loading={profileLoading}
          error={profileError}
          branchLabel={branchLabelById(selectedEmployee.branchId)}
          onClose={closeProfile}
          onEdit={(emp) => { closeProfile(); openEdit(emp) }}
          onDelete={(emp) => setDeleteConfirm(emp)}
          onViewPermissionHistory={(emp) => {
            closeProfile()
            navigate(`/leaves?employeePk=${emp.id}&year=${new Date().getFullYear()}`)
          }}
        />
      )}

      {/* Delete Confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">Əməkdaşı sil</h2>
            <p className="text-gray-600 mb-6 text-sm">
              <strong>{deleteConfirm.firstName} {deleteConfirm.lastName}</strong> adlı əməkdaşı silmək istədiyinizdən əminsiniz? Bu əməliyyat geri alına bilməz.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setDeleteConfirm(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Ləğv et</button>
              <button onClick={handleDelete} className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700">Sil</button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
