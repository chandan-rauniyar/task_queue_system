import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Mail, Plus, CheckCircle, XCircle, Loader } from 'lucide-react'
import { getClientSmtp, createClientSmtp, testClientSmtp } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Modal from '../components/ui/Modal'
import toast from 'react-hot-toast'
import clsx from 'clsx'

const PURPOSES = ['NOREPLY', 'SUPPORT', 'BILLING', 'ALERT', 'CUSTOM']
const PURPOSE_COLORS = {
  NOREPLY: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300',
  SUPPORT: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300',
  BILLING: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300',
  ALERT:   'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300',
  CUSTOM:  'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
}

export default function MySmtp() {
  const qc = useQueryClient()
  const { user } = useAuth()
  const [showModal, setShowModal] = useState(false)
  const [testingId, setTestingId] = useState(null)
  const [form, setForm] = useState({ purpose: 'NOREPLY', label: '', fromEmail: '', fromName: '', host: 'smtp.gmail.com', port: 587, username: '', password: '', useTls: true })

  const { data: configs = [], isLoading } = useQuery({ queryKey: ['client-smtp'], queryFn: getClientSmtp })

  const createMut = useMutation({
    mutationFn: createClientSmtp,
    onSuccess: () => { qc.invalidateQueries(['client-smtp']); setShowModal(false); toast.success('SMTP config added') },
  })

  const handleTest = async (id) => {
    setTestingId(id)
    try { await testClientSmtp(id); qc.invalidateQueries(['client-smtp']); toast.success('SMTP verified!') }
    catch (err) { toast.error(err.response?.data?.error || 'Connection failed') }
    finally { setTestingId(null) }
  }

  return (
    <div>
      <PageHeader
        title="SMTP Settings"
        description="Configure email providers for your company"
        action={<button className="btn-primary" onClick={() => setShowModal(true)}><Plus className="w-4 h-4" /> Add Config</button>}
      />
      {isLoading ? <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      : configs.length === 0 ? (
        <div className="card">
          <EmptyState icon={Mail} title="No SMTP configs" description="Add your email provider to start sending emails"
            action={<button className="btn-primary" onClick={() => setShowModal(true)}><Plus className="w-4 h-4" /> Add Config</button>}
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {configs.map(cfg => (
            <div key={cfg.id} className="card p-5">
              <div className="mb-3">
                <span className={clsx('text-xs font-semibold px-2.5 py-1 rounded-full', PURPOSE_COLORS[cfg.purpose])}>{cfg.purpose}</span>
                <h4 className="font-semibold text-gray-900 dark:text-gray-100 mt-2">{cfg.label}</h4>
              </div>
              <div className="space-y-1 text-xs text-gray-500 dark:text-gray-400 mb-3">
                <p>{cfg.fromName} &lt;{cfg.fromEmail}&gt;</p>
                <p>{cfg.host}:{cfg.port}</p>
              </div>
              <div className="flex items-center justify-between pt-3 border-t border-gray-100 dark:border-gray-800">
                <div className="flex items-center gap-1.5">
                  {cfg.isVerified
                    ? <><CheckCircle className="w-3.5 h-3.5 text-green-500" /><span className="text-xs text-green-600 dark:text-green-400 font-medium">Verified</span></>
                    : <><XCircle className="w-3.5 h-3.5 text-gray-400" /><span className="text-xs text-gray-500">Not verified</span></>
                  }
                </div>
                <button onClick={() => handleTest(cfg.id)} disabled={testingId === cfg.id}
                  className="text-xs font-medium text-primary-500 hover:text-primary-600 disabled:opacity-50 flex items-center gap-1">
                  {testingId === cfg.id ? <><Loader className="w-3 h-3 animate-spin" /> Testing...</> : 'Test'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Add SMTP Config" size="lg">
        <form onSubmit={e => { e.preventDefault(); createMut.mutate({ ...form, companyId: user?.companyId }) }} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div><label className="label">Purpose</label><select className="input" value={form.purpose} onChange={e => setForm(f => ({ ...f, purpose: e.target.value }))}>{PURPOSES.map(p => <option key={p} value={p}>{p}</option>)}</select></div>
            <div><label className="label">Label</label><input className="input" value={form.label} onChange={e => setForm(f => ({ ...f, label: e.target.value }))} placeholder="e.g. Gmail No-Reply" required /></div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="label">From Email</label><input className="input" type="email" value={form.fromEmail} onChange={e => setForm(f => ({ ...f, fromEmail: e.target.value }))} placeholder="noreply@company.com" required /></div>
            <div><label className="label">From Name</label><input className="input" value={form.fromName} onChange={e => setForm(f => ({ ...f, fromName: e.target.value }))} placeholder="My Company" required /></div>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="col-span-2"><label className="label">SMTP Host</label><input className="input" value={form.host} onChange={e => setForm(f => ({ ...f, host: e.target.value }))} required /></div>
            <div><label className="label">Port</label><input className="input" type="number" value={form.port} onChange={e => setForm(f => ({ ...f, port: Number(e.target.value) }))} required /></div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="label">Username</label><input className="input" value={form.username} onChange={e => setForm(f => ({ ...f, username: e.target.value }))} required /></div>
            <div><label className="label">Password</label><input className="input" type="password" value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))} required /></div>
          </div>
          <div className="flex items-center gap-2">
            <input type="checkbox" id="tls" checked={form.useTls} onChange={e => setForm(f => ({ ...f, useTls: e.target.checked }))} />
            <label htmlFor="tls" className="text-sm text-gray-700 dark:text-gray-300">Use TLS</label>
          </div>
          <div className="flex gap-3 pt-1">
            <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={createMut.isPending}>{createMut.isPending ? 'Saving...' : 'Save'}</button>
          </div>
        </form>
      </Modal>
    </div>
  )
}