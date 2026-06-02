import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { useAuthStore } from './store/authStore.ts'
import { t } from './i18n/index.ts'

const LoginPage = lazy(() => import('./pages/LoginPage.tsx'))
const DashboardPage = lazy(() => import('./pages/DashboardPage.tsx'))
const EmployeesPage = lazy(() => import('./pages/EmployeesPage.tsx'))
const BranchesPage = lazy(() => import('./pages/BranchesPage.tsx'))
const DepartmentsPage = lazy(() => import('./pages/DepartmentsPage.tsx'))
const PositionsPage = lazy(() => import('./pages/PositionsPage.tsx'))
const AttendancePage = lazy(() => import('./pages/AttendancePage.tsx'))
const ReportsPage = lazy(() => import('./pages/ReportsPage.tsx'))
const TabelPage = lazy(() => import('./pages/TabelPage.tsx'))
const WorkSchedulePage = lazy(() => import('./pages/WorkSchedulePage.tsx'))
const LeavesPage = lazy(() => import('./pages/LeavesPage.tsx'))
const HolidayPermissionsPage = lazy(() => import('./pages/HolidayPermissionsPage.tsx'))
const AnnualLeavePage = lazy(() => import('./pages/AnnualLeavePage.tsx'))
const DevicesPage = lazy(() => import('./pages/DevicesPage.tsx'))
const AccessLogsPage = lazy(() => import('./pages/AccessLogsPage.tsx'))
const SettingsPage = lazy(() => import('./pages/SettingsPage.tsx'))

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50">
      {children}
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<div className="flex items-center justify-center h-screen">{t('app.loading')}</div>}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<ProtectedRoute><AppLayout><DashboardPage /></AppLayout></ProtectedRoute>} />
          <Route path="/employees" element={<ProtectedRoute><AppLayout><EmployeesPage /></AppLayout></ProtectedRoute>} />
          <Route path="/branches" element={<ProtectedRoute><AppLayout><BranchesPage /></AppLayout></ProtectedRoute>} />
          <Route path="/departments" element={<ProtectedRoute><AppLayout><DepartmentsPage /></AppLayout></ProtectedRoute>} />
          <Route path="/positions" element={<ProtectedRoute><AppLayout><PositionsPage /></AppLayout></ProtectedRoute>} />
          <Route path="/attendance" element={<ProtectedRoute><AppLayout><AttendancePage /></AppLayout></ProtectedRoute>} />
          <Route path="/reports" element={<ProtectedRoute><AppLayout><ReportsPage /></AppLayout></ProtectedRoute>} />
          <Route path="/tabel" element={<ProtectedRoute><AppLayout><TabelPage /></AppLayout></ProtectedRoute>} />
          <Route path="/work-schedule" element={<ProtectedRoute><AppLayout><WorkSchedulePage /></AppLayout></ProtectedRoute>} />
          <Route path="/leaves" element={<ProtectedRoute><AppLayout><LeavesPage /></AppLayout></ProtectedRoute>} />
          <Route path="/holiday-permissions" element={<ProtectedRoute><AppLayout><HolidayPermissionsPage /></AppLayout></ProtectedRoute>} />
          <Route path="/annual-leave" element={<ProtectedRoute><AppLayout><AnnualLeavePage /></AppLayout></ProtectedRoute>} />
          <Route path="/devices" element={<ProtectedRoute><AppLayout><DevicesPage /></AppLayout></ProtectedRoute>} />
          <Route path="/access-logs" element={<ProtectedRoute><AppLayout><AccessLogsPage /></AppLayout></ProtectedRoute>} />
          <Route path="/settings" element={<ProtectedRoute><AppLayout><SettingsPage /></AppLayout></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
