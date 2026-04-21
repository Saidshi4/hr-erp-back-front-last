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

  return (
    <Layout>
      <div className="p-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Devices</h1>
            <p className="text-sm text-gray-500 mt-1">{activeCount} active of {devices.length} total</p>
          </div>
          <button
            onClick={openCreate}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
          >
            + Add Device
          </button>
        </div>

        <div className="bg-white rounded-xl shadow-sm">
          {loading ? (
            <div className="p-8 text-center text-gray-500">Loading...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500">{error}</div>
          ) : devices.length === 0 ? (
            <div className="p-8 text-center text-gray-500">
              No devices configured. Click "Add Device" to get started.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Device ID</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Name</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">IP Address</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Status</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Last Sync</th>
                    <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {devices.map((device: DeviceConfig) => (
                    <tr key={device.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 text-sm text-gray-600 font-mono">{device.deviceId}</td>
                      <td className="px-6 py-4 text-sm font-medium text-gray-900">{device.deviceName || '-'}</td>
                      <td className="px-6 py-4 text-sm text-gray-600 font-mono">
                        {device.deviceIp}{device.devicePort && device.devicePort !== 80 ? `:${device.devicePort}` : ''}
                      </td>
                      <td className="px-6 py-4">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                          device.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                        }`}>
                          {device.status || 'UNKNOWN'}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-600">
                        {device.lastSyncTime ? new Date(device.lastSyncTime).toLocaleString() : 'Never'}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex gap-2">
                          <button
                            onClick={() => handleSync(device.id)}
                            disabled={syncingId === device.id}
                            className="text-xs bg-blue-100 text-blue-700 px-3 py-1 rounded hover:bg-blue-200 disabled:opacity-50"
                          >
                            {syncingId === device.id ? 'Syncing...' : 'Sync'}
                          </button>
                          <button
                            onClick={() => openEdit(device)}
                            className="text-xs bg-gray-100 text-gray-700 px-3 py-1 rounded hover:bg-gray-200"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => setDeleteConfirm(device)}
                            className="text-xs bg-red-100 text-red-700 px-3 py-1 rounded hover:bg-red-200"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
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
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Device Name</label>
                <input
                  type="text"
                  value={form.deviceName}
                  onChange={(e) => setForm({ ...form, deviceName: e.target.value })}
                  placeholder="e.g., Main Entrance"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">IP Address *</label>
                <input
                  type="text"
                  value={form.deviceIp}
                  onChange={(e) => setForm({ ...form, deviceIp: e.target.value })}
                  placeholder="192.168.1.100"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Port</label>
                <input
                  type="number"
                  value={form.devicePort}
                  onChange={(e) => setForm({ ...form, devicePort: e.target.value ? Number(e.target.value) : '' })}
                  placeholder="80"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Username</label>
                <input
                  type="text"
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Branch</label>
                <select
                  value={form.branchId}
                  onChange={(e) => setForm({ ...form, branchId: e.target.value ? Number(e.target.value) : '' })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
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
            <p className="text-gray-600 mb-6">
              Are you sure you want to delete <strong>{deleteConfirm.deviceName || deleteConfirm.deviceId}</strong>?
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
