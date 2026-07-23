import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Building2, Zap, ArrowRight } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'
import toast from 'react-hot-toast'

export default function Setup() {
  const [companyName, setCompanyName] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { user, login } = useAuth()
  const navigate = useNavigate()

  // Slug preview
  const slug = companyName
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/, '')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await api.post('/client/create-company', { name: companyName })
      const data = res.data.data

      // Update stored token and user with companyId
      login(data.token, {
        ...user,
        companyId:   data.companyId,
        companyName: data.companyName,
      })

      toast.success(`"${data.companyName}" is ready!`)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create company. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">

        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-12 h-12 rounded-2xl bg-primary-500 flex items-center justify-center mb-3 shadow-lg">
            <Zap className="w-6 h-6 text-white" />
          </div>
          <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
            Welcome, {user?.name?.split(' ')[0]}!
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Set up your company to get started
          </p>
        </div>

        <div className="card p-6 shadow-sm">
          <div className="flex items-center gap-3 p-3 rounded-lg bg-primary-50 dark:bg-primary-900/20 mb-5">
            <Building2 className="w-5 h-5 text-primary-500 flex-shrink-0" />
            <p className="text-sm text-primary-700 dark:text-primary-300">
              Your company is where your projects, API keys, and jobs live.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="label">Company name</label>
              <input
                className="input"
                value={companyName}
                onChange={e => setCompanyName(e.target.value)}
                placeholder="Swiggy, Razorpay, My Startup..."
                required
                autoFocus
              />
              {slug && (
                <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">
                  Slug: <code className="font-mono text-primary-500">{slug}</code>
                </p>
              )}
            </div>

            {error && (
              <div className="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
                <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !companyName.trim()}
              className="btn-primary w-full justify-center py-2.5"
            >
              {loading ? 'Creating...' : (
                <>Create company <ArrowRight className="w-4 h-4" /></>
              )}
            </button>
          </form>
        </div>

        <p className="text-xs text-center text-gray-400 dark:text-gray-600 mt-4">
          You can rename your company later from the settings
        </p>
      </div>
    </div>
  )
}