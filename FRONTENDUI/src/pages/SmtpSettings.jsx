import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Mail, Plus, Trash2, CheckCircle, XCircle, ToggleLeft, ToggleRight } from 'lucide-react'
import { getCompanies } from '../api/companies'
import { getSmtpConfigs, createSmtp, testSmtp, toggleSmtp, deleteSmtp } from '../api/smtp'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Badge from '../components/ui/Badge'
import Modal from '../components/ui/Modal'
import toast from 'react-hot-toast'

const PURPOSES = ['NOREPLY', 'SUPPORT', 'BILLING', 'ALERT', 'CUSTOM']

const DEFAULT_FORM = {
  purpose: 'NOREPLY', label: '', fromEmail: '', fromName: '',
  host: 'smtp.gmail.com', port: 587, username: '', password: '', useTls: true,
}

export default function SmtpSettings() {
  const qc = useQueryClient()
  const [companyId, setCompanyId] = useState('')
  const [showModal, setShowModal]   = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [form, setForm] = useState(DEFAULT_FORM)
  const [testingId, setTestingId] = useState(null)

  const { data: companies = [] } = useQuery({ queryKey: ['companies'], queryFn: getCompanies })

  const { data: configs = [], isLoading } = useQuery({
    queryKey: ['smtp', companyId],
    queryFn: () => getSmtpConfigs(companyId),
    enabled: !!companyId,
  })

  const createMut = useMutation({
    mutationFn: createSmtp,
    onSuccess: () => {
      qc.invalidateQueries(['smtp', companyId])
      setShowModal(false)
      setForm(DEFAULT_FORM)
      toast.success('SMTP config saved')
    },
  })

  const testMut = useMutation({
    mutationFn: testSmtp,
    onSuccess: () => {
      qc.invalidateQueries(['smtp', companyId])
      setTestingId(null)
      toast.success('SMTP connection verified ✓')
    },
    onError: (err) => {
      setTestingId(null)
      toast.error(err.response?.data?.error || 'Connection failed')
    },
  })

  const toggleMut = useMutation({
    mutationFn: toggleSmtp,
    onSuccess: () => qc.invalidateQueries(['smtp', companyId]),
  })

  const deleteMut = useMutation({
    mutationFn: deleteSmtp,
    onSuccess: () => {
      qc.invalidateQueries(['smtp', companyId])
      setConfirmDelete(null)
      toast.success('SMTP config deleted')
    },
  })

  const handleTest = (id) => {
    setTestingId(id)
    testMut.mutate(id)
  }

  const f = (key) => (e) => setForm(prev => ({ ...prev, [key]: e.target.value }))

  // Used purposes — to show which are still available
  const usedPurposes = configs.map(c => c.purpose)

  return (
    <div>
      <PageHeader
        title="SMTP Settings"
        description="Email server configs per company"
        action={companyId && (
          <button className="btn-primary" onClick={() => setShowModal(true)}>
            <Plus className="w-4 h-4" /> Add SMTP Config
          </button>
        )}
      />

      {/* Company selector */}
      <div className="card p-4 mb-6">
        <label className="label">Select Company</label>
        <select className="input max-w-xs" value={companyId} onChange={e => setCompanyId(e.target.value)}>
          <option value="">Choose a company...</option>
          {companies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        {companyId && (
          <p className="text-xs text-gray-400 mt-2">
            SMTP configs are shared by all projects under this company.
          </p>
        )}
      </div>

      {!companyId ? (
        <div className="card">
          <EmptyState icon={Mail} title="Select a company" description="Choose a company above to manage its SMTP configs" />
        </div>
      ) : isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : configs.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Mail}
            title="No SMTP configs"
            description="Add at least one SMTP config so this company can send emails"
            action={
              <button className="btn-primary" onClick={() => setShowModal(true)}>
                <Plus className="w-4 h-4" /> Add SMTP Config
              </button>
            }
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {configs.map(config => (
            <div key={config.id} className="card p-5 flex flex-col gap-3">

              {/* Header */}
              <div className="flex items-start justify-between">
                <Badge type="purpose" value={config.purpose} />
                <div className="flex items-center gap-1.5">
                  {/* Verified badge */}
                  {config.isVerified
                    ? <CheckCircle className="w-4 h-4 text-green-500" title="Verified" />
                    : <XCircle className="w-4 h-4 text-gray-400" title="Not verified" />
                  }
                  {/* Toggle */}
                  <button
                    onClick={() => toggleMut.mutate(config.id)}
                    className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                    title={config.isActive ? 'Disable' : 'Enable'}
                  >
                    {config.isActive
                      ? <ToggleRight className="w-5 h-5 text-green-500" />
                      : <ToggleLeft className="w-5 h-5" />
                    }
                  </button>
                  {/* Delete */}
                  <button
                    onClick={() => setConfirmDelete(config)}
                    className="text-gray-400 hover:text-red-500 transition-colors"
                    title="Delete"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Details */}
              <div>
                <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{config.label}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{config.fromName} &lt;{config.fromEmail}&gt;</p>
              </div>

              <div className="text-xs text-gray-500 dark:text-gray-400 space-y-0.5">
                <p>Host: <span className="font-mono">{config.host}:{config.port}</span></p>
                <p>User: <span className="font-mono">{config.username}</span></p>
                <p>TLS: {config.useTls ? 'Enabled' : 'Disabled'}</p>
              </div>

              {/* Status line */}
              <div className="flex items-center gap-2 text-xs">
                {config.isVerified
                  ? <span className="text-green-600 dark:text-green-400 font-medium">✓ Verified</span>
                  : <span className="text-gray-400">Not verified</span>
                }
                <span className="text-gray-300 dark:text-gray-700">·</span>
                <span className={config.isActive ? 'text-green-600 dark:text-green-400' : 'text-gray-400'}>
                  {config.isActive ? 'Active' : 'Inactive'}
                </span>
              </div>

              {/* Test button */}
              <button
                onClick={() => handleTest(config.id)}
                disabled={testingId === config.id}
                className="btn-secondary w-full justify-center text-xs py-2"
              >
                {testingId === config.id ? 'Testing...' : 'Test Connection'}
              </button>
            </div>
          ))}

          {/* Add more card */}
          {PURPOSES.filter(p => !usedPurposes.includes(p)).length > 0 && (
            <button
              onClick={() => setShowModal(true)}
              className="card p-5 border-dashed flex flex-col items-center justify-center gap-2 text-gray-400 hover:text-primary-500 hover:border-primary-300 dark:hover:border-primary-700 transition-colors min-h-48"
            >
              <Plus className="w-6 h-6" />
              <span className="text-sm font-medium">Add SMTP Config</span>
              <span className="text-xs text-center">
                {PURPOSES.filter(p => !usedPurposes.includes(p)).join(', ')} available
              </span>
            </button>
          )}
        </div>
      )}

      {/* Add modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Add SMTP Config" size="lg">
        <form
          onSubmit={e => { e.preventDefault(); createMut.mutate({ ...form, companyId }) }}
          className="space-y-4"
        >
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">Purpose</label>
              <select className="input" value={form.purpose} onChange={f('purpose')}>
                {PURPOSES.filter(p => !usedPurposes.includes(p)).map(p =>
                  <option key={p} value={p}>{p}</option>
                )}
              </select>
            </div>
            <div>
              <label className="label">Label</label>
              <input className="input" value={form.label} onChange={f('label')} placeholder="e.g. Gmail No-Reply" required />
            </div>
            <div>
              <label className="label">From Email</label>
              <input className="input" type="email" value={form.fromEmail} onChange={f('fromEmail')} placeholder="noreply@company.com" required />
            </div>
            <div>
              <label className="label">From Name</label>
              <input className="input" value={form.fromName} onChange={f('fromName')} placeholder="My Company" required />
            </div>
            <div>
              <label className="label">SMTP Host</label>
              <input className="input" value={form.host} onChange={f('host')} placeholder="smtp.gmail.com" required />
            </div>
            <div>
              <label className="label">Port</label>
              <input className="input" type="number" value={form.port} onChange={f('port')} required />
            </div>
            <div>
              <label className="label">Username</label>
              <input className="input" value={form.username} onChange={f('username')} placeholder="your@gmail.com" required />
            </div>
            <div>
              <label className="label">Password / App Password</label>
              <input className="input" type="password" value={form.password} onChange={f('password')} placeholder="16-char app password" required />
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input
              id="tls"
              type="checkbox"
              checked={form.useTls}
              onChange={e => setForm(prev => ({ ...prev, useTls: e.target.checked }))}
              className="w-4 h-4 rounded border-gray-300 text-primary-500"
            />
            <label htmlFor="tls" className="text-sm text-gray-700 dark:text-gray-300">Use TLS (STARTTLS)</label>
          </div>

          <p className="text-xs text-gray-400">
            Password is encrypted before saving. It is never returned in any API response.
          </p>

          <div className="flex gap-3 pt-2">
            <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={createMut.isPending}>
              {createMut.isPending ? 'Saving...' : 'Save Config'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Confirm delete */}
      <Modal open={!!confirmDelete} onClose={() => setConfirmDelete(null)} title="Delete SMTP Config" size="sm">
        {confirmDelete && (
          <div className="space-y-4">
            <p className="text-sm text-gray-600 dark:text-gray-300">
              Delete the <strong>{confirmDelete.purpose}</strong> config ({confirmDelete.fromEmail})?
              Any jobs using this purpose will fail.
            </p>
            <div className="flex gap-3">
              <button className="btn-secondary flex-1 justify-center" onClick={() => setConfirmDelete(null)}>Cancel</button>
              <button className="btn-danger flex-1 justify-center" onClick={() => deleteMut.mutate(confirmDelete.id)} disabled={deleteMut.isPending}>
                {deleteMut.isPending ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}