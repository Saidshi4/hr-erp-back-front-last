import { useEffect, useState } from 'react'
import Layout from '../components/Layout'
import { useDeviceStore } from '../store/deviceStore'
import { DeviceConfig, Branch } from '../types'
import { branchApi } from '../api/branchApi'

interface DeviceFormData {
  deviceId: string
  deviceName: string
  deviceIp: string
  devicePort: number | ''
  username: string
  password: string
  branchId: number | ''
  status: string
}

const defaultForm: DeviceFormData = {
  deviceId: '',
  deviceName: '',
  deviceIp: '',
  devicePort: 80,
  username: 'admin',
  password: '',
  branchId: '',
  status: 'ACTIVE',
}

export default function DevicesPage() {
  const { devices, loading, error, fetchDevices, syncDevice, createDevice, updateDevice, deleteDevice } = useDeviceStore()
  const [branches, setBranches] = useState<Branch[]>([])
  const [showModal, setShowModal] = useState(false)
  const [editingDevice, setEditingDevice] = useState<DeviceConfig | null>(null)
  const [form, setForm] = useState<DeviceFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<DeviceConfig | null>(null)
  const [syncingId, setSyncingId] = useState<number | null>(null)

  useEffect(() => {
    fetchDevices()
    branchApi.getAll().then((res) => setBranches(res.data?.data ?? []))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditingDevice(null)
    setForm(defaultForm)
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (device: DeviceConfig) => {
    setEditingDevice(device)
    setForm({
      deviceId: device.deviceId,
      deviceName: device.deviceName || '',
      deviceIp: device.deviceIp,
      devicePort: device.devicePort || 80,
      username: device.username || 'admin',
      password: '',
      branchId: device.branchId || '',
      status: device.status || 'ACTIVE',
    })
    setFormError(null)
    setShowModal(true)
  }

  const handleSave = async () => {
    if (!form.deviceId.trim()) { setFormError('Device ID is required.'); return }
    if (!form.deviceIp.trim()) { setFormError('Device IP is required.'); return }
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        ...form,
        devicePort: form.devicePort ? Number(form.devicePort) : 80,
        branchId: form.branchId ? Number(form.branchId) : undefined,
      }
      if (editingDevice) {
        await updateDevice(editingDevice.id, payload)
      } else {
        await createDevice(payload)
      }
      setShowModal(false)
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Failed to save device')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteConfirm) return
    try {
      await deleteDevice(deleteConfirm.id)
      setDeleteConfirm(null)
    } catch {
      // handled by store
    }
  }

  const handleSync = async (id: number) => {
    setSyncingId(id)
    try {
      await syncDevice(id)
    } finally {
      setSyncingId(null)
    }
  }

  const activeCount = devices.filter((d: DeviceConfig) => d.status === 'ACTIVE').length
  const inactiveCount = devices.length - activeCount

  return (
    <Layout>
      <div className="p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex items-start justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Devices</h1>
            <p className="text-sm text-gray-500 mt-1">
              <span className="inline-flex items-center gap-1.5 mr-3">
                <span className="w-2 h-2 rounded-full bg-green-500 inline-block"></span>
                {activeCount} online
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-red-400 inline-block"></span>
                {inactiveCount} offline
              </span>
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => fetchDevices()}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Refresh
            </button>
            <button
              onClick={openCreate}
              className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-lg transition-colors"
              style={{ background: '#a855f7' }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Add Device
            </button>
          </div>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-3 gap-4 mb-6">
          <div className="bg-white rounded-xl p-4 shadow-sm flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg flex items-center justify-center" style={{ background: '#f3e8ff' }}>
              <svg className="w-5 h-5" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <div>
              <p className="text-xs text-gray-400">Total Devices</p>
              <p className="text-lg font-bold text-gray-900">{devices.length}</p>
            </div>
          </div>
          <div className="bg-white rounded-xl p-4 shadow-sm flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg flex items-center justify-center bg-green-50">
              <span className="w-3 h-3 rounded-full bg-green-500 inline-block"></span>
            </div>
            <div>
              <p className="text-xs text-gray-400">Online</p>
              <p className="text-lg font-bold text-gray-900">{activeCount}</p>
            </div>
          </div>
          <div className="bg-white rounded-xl p-4 shadow-sm flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg flex items-center justify-center bg-red-50">
              <span className="w-3 h-3 rounded-full bg-red-400 inline-block"></span>
            </div>
            <div>
              <p className="text-xs text-gray-400">Offline</p>
              <p className="text-lg font-bold text-gray-900">{inactiveCount}</p>
            </div>
          </div>
        </div>

        {/* Device List */}
        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Loading devices...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : devices.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center">
            <div className="w-16 h-16 rounded-full bg-purple-50 flex items-center justify-center mx-auto mb-4">
              <svg className="w-8 h-8 text-purple-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <p className="text-gray-500 text-sm">No devices configured yet.</p>
            <button onClick={openCreate} className="mt-3 text-sm font-medium" style={{ color: '#a855f7' }}>
              + Add your first device
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            {devices.map((device: DeviceConfig) => (
              <div key={device.id} className="bg-white rounded-xl shadow-sm p-5 flex items-center gap-5">
                {/* Icon */}
                <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: '#f3e8ff' }}>
                  <svg className="w-6 h-6" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                </div>

                {/* Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <p className="font-semibold text-gray-900 text-sm">{device.deviceName || device.deviceId}</p>
                    <span
                      className="px-2 py-0.5 rounded-full text-xs font-medium"
                      style={device.status === 'ACTIVE'
                        ? { background: '#d1fae5', color: '#065f46' }
                        : { background: '#fee2e2', color: '#991b1b' }}
                    >
                      <span className={`inline-block w-1.5 h-1.5 rounded-full mr-1 ${device.status === 'ACTIVE' ? 'bg-green-500' : 'bg-red-400'}`}></span>
                      {device.status === 'ACTIVE' ? 'Online' : 'Offline'}
                    </span>
                  </div>
                  <p className="text-xs text-gray-400 font-mono">{device.deviceId}</p>
                </div>

                {/* IP */}
                <div className="hidden md:block text-center min-w-[130px]">
                  <p className="text-xs text-gray-400 mb-0.5">IP Address</p>
                  <p className="text-sm font-mono text-gray-700">
                    {device.deviceIp}{device.devicePort && device.devicePort !== 80 ? `:${device.devicePort}` : ''}
                  </p>
                </div>

                {/* Last Sync */}
                <div className="hidden lg:block text-center min-w-[150px]">
                  <p className="text-xs text-gray-400 mb-0.5">Last Sync</p>
                  <p className="text-sm text-gray-600">
                    {device.lastSyncTime ? new Date(device.lastSyncTime).toLocaleString() : 'Never'}
                  </p>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 flex-shrink-0">
                  <button
                    onClick={() => handleSync(device.id)}
                    disabled={syncingId === device.id}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors disabled:opacity-50"
                    style={{ color: '#a855f7', borderColor: '#e9d5ff', background: '#faf5ff' }}
                  >
                    <svg className={`w-3.5 h-3.5 ${syncingId === device.id ? 'animate-spin' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    {syncingId === device.id ? 'Syncing...' : 'Sync'}
                  </button>
                  <button
                    onClick={() => openEdit(device)}
                    className="px-3 py-1.5 text-xs font-medium text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => setDeleteConfirm(device)}
                    className="px-3 py-1.5 text-xs font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors"
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">
              {editingDevice ? 'Edit Device' : 'Add Device'}
            </h2>
            {formError && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">
                {formError}
              </div>
            )}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Device ID *</label>
                <input
                  type="text"
                  value={form.deviceId}
                  onChange={(e) => setForm({ ...form, deviceId: e.target.value })}
                  placeholder="e.g., HIK-001"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Device Name</label>
                <input
                  type="text"
                  value={form.deviceName}
                  onChange={(e) => setForm({ ...form, deviceName: e.target.value })}
                  placeholder="e.g., Main Entrance"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">IP Address *</label>
                <input
                  type="text"
                  value={form.deviceIp}
                  onChange={(e) => setForm({ ...form, deviceIp: e.target.value })}
                  placeholder="192.168.1.100"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Port</label>
                <input
                  type="number"
                  value={form.devicePort}
                  onChange={(e) => setForm({ ...form, devicePort: e.target.value ? Number(e.target.value) : '' })}
                  placeholder="80"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Username</label>
                <input
                  type="text"
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Password {editingDevice ? '(leave blank to keep)' : ''}
                </label>
                <input
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Branch</label>
                <select
                  value={form.branchId}
                  onChange={(e) => setForm({ ...form, branchId: e.target.value ? Number(e.target.value) : '' })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Select branch...</option>
                  {branches.map((b) => (
                    <option key={b.id} value={b.id}>{b.branchName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                <select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-4 py-2 text-sm text-white rounded-lg disabled:opacity-50 transition-colors"
                style={{ background: '#a855f7' }}
              >
                {saving ? 'Saving...' : editingDevice ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">Delete Device</h2>
            <p className="text-gray-600 mb-6 text-sm">
              Are you sure you want to delete <strong>{deleteConfirm.deviceName || deleteConfirm.deviceId}</strong>? This action cannot be undone.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeleteConfirm(null)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
