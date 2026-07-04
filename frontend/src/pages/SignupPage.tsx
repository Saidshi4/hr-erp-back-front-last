import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/authApi.ts'
import { t } from '../i18n/index.ts'

const AUTH_BG = 'linear-gradient(135deg, #1e1b4b 0%, #2d2472 100%)'
const INPUT_CLS = 'w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent'

const HR_ROLES = [
  { value: 'OFFICE_HR', label: 'Ofis HR' },
  { value: 'DEPARTMENT_HR', label: 'Departament HR' },
  { value: 'EMPLOYEE', label: 'Əməkdaş' },
]

export default function SignupPage() {
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [role, setRole] = useState('EMPLOYEE')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (password.length < 8) {
      setError(t('signup.passwordMinLength'))
      return
    }
    if (!/\d/.test(password)) {
      setError(t('signup.passwordNeedsDigit'))
      return
    }
    if (password !== passwordConfirm) {
      setError(t('signup.passwordMismatch'))
      return
    }

    setLoading(true)
    try {
      await authApi.signup({ email, firstName, lastName, password, role })
      setSuccess(true)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 400) {
        const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        setError(msg ?? t('signup.error'))
      } else if (status === 403) {
        setError(t('signup.forbidden'))
      } else {
        setError(t('signup.error'))
      }
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4" style={{ background: AUTH_BG }}>
        <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-8 text-center">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-full mb-4" style={{ background: 'rgba(168,85,247,0.12)' }}>
            <svg className="w-8 h-8" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-bold mb-2" style={{ color: '#1e1b4b' }}>{t('signup.success')}</h2>
          <button
            onClick={() => navigate('/employees')}
            className="mt-6 px-6 py-2.5 rounded-lg text-white font-semibold transition-opacity hover:opacity-90"
            style={{ background: '#a855f7' }}
          >
            {t('signup.backToList')}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4" style={{ background: AUTH_BG }}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-8">
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-xl mb-4" style={{ background: '#a855f7' }}>
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold" style={{ color: '#1e1b4b' }}>{t('signup.title')}</h1>
          <p className="text-gray-500 mt-1 text-sm">{t('signup.subtitle')}</p>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-5 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSignup} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('signup.email')}</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={INPUT_CLS}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('signup.firstName')}</label>
              <input
                type="text"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                className={INPUT_CLS}
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('signup.lastName')}</label>
              <input
                type="text"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                className={INPUT_CLS}
                required
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('signup.password')}</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={INPUT_CLS}
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('signup.passwordConfirm')}</label>
            <input
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              className={INPUT_CLS}
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('signup.role')}</label>
            <select
              value={role}
              onChange={(e) => setRole(e.target.value)}
              className={INPUT_CLS}
            >
              {HR_ROLES.map((r) => (
                <option key={r.value} value={r.value}>{r.label}</option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full text-white py-2.5 px-4 rounded-lg font-semibold transition-opacity disabled:opacity-50 disabled:cursor-not-allowed hover:opacity-90 mt-2"
            style={{ background: '#a855f7' }}
          >
            {loading ? t('signup.signingUp') : t('signup.signUp')}
          </button>
        </form>
      </div>
    </div>
  )
}
