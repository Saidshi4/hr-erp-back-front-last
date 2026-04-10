import { useEffect } from 'react'
import Layout from '../components/Layout'
import { useDeviceStore } from '../store/deviceStore'
import { DeviceConfig } from '../types'

export default function DevicesPage() {
  const { devices, loading, error, fetchDevices, syncDevice } = useDeviceStore()

  useEffect(() => {
    fetchDevices()
  }, [fetchDevices])

  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Devices</h1>

        <div className="bg-white rounded-xl shadow-sm">
          {loading ? (
            <div className="p-8 text-center text-gray-500">Loading...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500">{error}</div>
          ) : (
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
                    <td className="px-6 py-4 text-sm text-gray-600">{device.deviceId}</td>
                    <td className="px-6 py-4 text-sm font-medium text-gray-900">{device.deviceName || '-'}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{device.deviceIp}</td>
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
                      <button
                        onClick={() => syncDevice(device.id)}
                        className="text-xs bg-blue-100 text-blue-700 px-3 py-1 rounded hover:bg-blue-200"
                      >
                        Sync
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {devices.length === 0 && !loading && (
            <div className="p-8 text-center text-gray-500">No devices configured.</div>
          )}
        </div>
      </div>
    </Layout>
  )
}
