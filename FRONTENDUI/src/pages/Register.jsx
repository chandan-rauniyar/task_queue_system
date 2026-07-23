import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Zap, Eye, EyeOff, User, Mail, Lock, Building2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { register as registerApi } from '../api/auth'
import toast from 'react-hot-toast'
import clsx from 'clsx'

export default function Register() {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', companyName: '' })
  const [showPass, setShowPass]   = useState(false)
  const [loading, setLoading]     = useState(false)
  const [error, setError]         = useState('')
  const { login } = useAuth()
  const navigate  = useNavigate()

  const set = (field) => (e) => setForm(f => ({ ...f, [field]: e.target.value }))

  // Slug preview
  const slugPreview = form.companyName
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/, '')

  // Password strength
  const strength = form.password.length === 0 ? 0
    : form.password.length < 8  ? 1
    : form.password.length < 12 ? 2
    : 3
  const strengthColor = ['', 'bg-red-400', 'bg-amber-400', 'bg-green-400'][strength]
  const strengthLabel = ['', 'Too short', 'Good', 'Strong'][strength]

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (form.password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }
    setLoading(true)
    try {
      const data = await registerApi(
        form.fullName, form.email,
        form.password, form.companyName
      )
      // Login and go straight to dashboard — no onboarding step
      login(data.token, {
        email:       data.email,
        name:        data.name,
        role:        data.role,
        companyId:   data.companyId,
        companyName: data.companyName,
      })
      toast.success(`Welcome! "${data.companyName}" is ready.`)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Registration failed. Please try again.')
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
          <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Create account</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Free to get started</p>
        </div>

        <div className="card p-6 shadow-sm">
          <form onSubmit={handleSubmit} className="space-y-4">

            {/* Full name */}
            <div>
              <label className="label">Full name</label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input className="input pl-9" value={form.fullName}
                  onChange={set('fullName')} placeholder="Rahul Sharma"
                  required autoFocus />
              </div>
            </div>

            {/* Email */}
            <div>
              <label className="label">Email address</label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input type="email" className="input pl-9" value={form.email}
                  onChange={set('email')} placeholder="rahul@company.com" required />
              </div>
            </div>

            {/* Password */}
            <div>
              <label className="label">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input type={showPass ? 'text' : 'password'} className="input pl-9 pr-10"
                  value={form.password} onChange={set('password')}
                  placeholder="At least 8 characters" required />
                <button type="button" onClick={() => setShowPass(s => !s)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                  {showPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {form.password.length > 0 && (
                <div className="flex items-center gap-2 mt-1.5">
                  <div className="flex gap-1 flex-1">
                    {[1, 2, 3].map(i => (
                      <div key={i} className={clsx('h-1 flex-1 rounded-full transition-colors',
                        strength >= i ? strengthColor : 'bg-gray-200 dark:bg-gray-700'
                      )} />
                    ))}
                  </div>
                  <span className="text-xs text-gray-400">{strengthLabel}</span>
                </div>
              )}
            </div>

            {/* Divider */}
            <div className="border-t border-gray-100 dark:border-gray-800 pt-1">
              <p className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">
                Your Company
              </p>
            </div>

            {/* Company name */}
            <div>
              <label className="label">Company name</label>
              <div className="relative">
                <Building2 className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input className="input pl-9" value={form.companyName}
                  onChange={set('companyName')}
                  placeholder="Swiggy, Razorpay, My Startup..."
                  required />
              </div>
              {slugPreview && (
                <p className="text-xs text-gray-400 mt-1">
                  Slug: <code className="font-mono text-primary-500">{slugPreview}</code>
                </p>
              )}
            </div>

            {/* Error */}
            {error && (
              <div className="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
                <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
              </div>
            )}

            <button type="submit" disabled={loading}
              className="btn-primary w-full justify-center py-2.5">
              {loading ? 'Creating account...' : 'Create account'}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 dark:text-gray-400 mt-4">
            Already have an account?{' '}
            <Link to="/login" className="text-primary-500 hover:text-primary-600 font-medium">
              Sign in
            </Link>
          </p>
        </div>

      </div>
    </div>
  )
}