import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, Plus, ChevronRight, ToggleLeft, ToggleRight,
  CheckCircle, XCircle, Trash2, Loader, Copy, Check, AlertTriangle
} from 'lucide-react'
import { getCompanies, toggleCompany } from '../api/companies'
import { getProjects, createProject, toggleProject } from '../api/projects'
import { getSmtpConfigs, createSmtp, testSmtp, toggleSmtp, deleteSmtp } from '../api/smtp'
import { getApiKeys, createApiKey, revokeApiKey } from '../api/apiKeys'
import { getJobs } from '../api/jobs'
import Badge from '../components/ui/Badge'
import Modal from '../components/ui/Modal'
import { Spinner, EmptyState } from '../components/ui/index.jsx'
import { formatDistanceToNow, format } from 'date-fns'
import toast from 'react-hot-toast'
import clsx from 'clsx'

const TABS = ['Projects', 'SMTP Settings', 'API Keys', 'Jobs']
const ENVIRONMENTS = ['PRODUCTION', 'STAGING', 'DEV']
const PURPOSES = ['NOREPLY', 'SUPPORT', 'BILLING', 'ALERT', 'CUSTOM']
const PURPOSE_COLORS = {
  NOREPLY: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300',
  SUPPORT: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300',
  BILLING: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300',
  ALERT:   'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300',
  CUSTOM:  'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
}

// ── Projects Tab ──────────────────────────────────────────────────────────────
function ProjectsTab({ companyId, companyName }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ name: '', description: '', environment: 'PRODUCTION' })

  const { data: projects = [], isLoading } = useQuery({
    queryKey: ['projects', companyId],
    queryFn: () => getProjects(companyId),
  })

  const createMut = useMutation({
    mutationFn: createProject,
    onSuccess: () => {
      qc.invalidateQueries(['projects', companyId])
      setShowModal(false)
      setForm({ name: '', description: '', environment: 'PRODUCTION' })
      toast.success('Project created')
    },
  })

  const toggleMut = useMutation({
    mutationFn: toggleProject,
    onSuccess: () => qc.invalidateQueries(['projects', companyId]),
  })

  const grouped = ENVIRONMENTS.reduce((acc, env) => {
    const items = projects.filter(p => p.environment === env)
    if (items.length) acc[env] = items
    return acc
  }, {})

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <p className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
          {projects.length} Projects
        </p>
        <button className="btn-primary text-xs px-3 py-1.5" onClick={() => setShowModal(true)}>
          <Plus className="w-3.5 h-3.5" /> New Project
        </button>
      </div>

      {isLoading ? <div className="flex justify-center py-10"><Spinner /></div>
      : projects.length === 0 ? (
        <EmptyState title="No projects yet" description="Create the first project for this company"
          action={<button className="btn-primary" onClick={() => setShowModal(true)}><Plus className="w-4 h-4" /> New Project</button>}
        />
      ) : (
        <div className="space-y-2">
          {Object.entries(grouped).map(([env, items]) => (
            <div key={env}>
              <p className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-2 mt-4 first:mt-0">{env}</p>
              {items.map(project => (
                <div key={project.id} className="flex items-center justify-between p-4 rounded-xl border border-gray-200 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 mb-2 transition-colors">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-semibold text-gray-900 dark:text-gray-100">{project.name}</span>
                      <Badge type="env" value={project.environment} />
                    </div>
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                      {project.description || 'No description'} · Created {formatDistanceToNow(new Date(project.createdAt), { addSuffix: true })}
                    </p>
                  </div>
                  <div className="flex items-center gap-3 ml-4 flex-shrink-0">
                    <button
                      onClick={() => toggleMut.mutate(project.id)}
                      className={clsx('text-xs font-medium px-2 py-1 rounded-md transition-colors',
                        project.isActive
                          ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400'
                          : 'bg-gray-100 dark:bg-gray-800 text-gray-500'
                      )}
                    >
                      {project.isActive ? 'Active' : 'Inactive'}
                    </button>
                    <button
                      onClick={() => navigate(`/projects/${project.id}`)}
                      className="flex items-center gap-1 text-xs text-primary-500 hover:text-primary-600 font-medium"
                    >
                      View <ChevronRight className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      <Modal open={showModal} onClose={() => setShowModal(false)} title="Create Project">
        <form onSubmit={e => { e.preventDefault(); createMut.mutate({ ...form, companyId }) }} className="space-y-4">
          <div>
            <label className="label">Project name</label>
            <input className="input" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Order Service" required />
          </div>
          <div>
            <label className="label">Description <span className="text-gray-400">(optional)</span></label>
            <input className="input" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="What does this project do?" />
          </div>
          <div>
            <label className="label">Environment</label>
            <select className="input" value={form.environment} onChange={e => setForm(f => ({ ...f, environment: e.target.value }))}>
              {ENVIRONMENTS.map(e => <option key={e} value={e}>{e}</option>)}
            </select>
          </div>
          <div className="flex gap-3 pt-1">
            <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={createMut.isPending}>
              {createMut.isPending ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

// ── SMTP Tab ──────────────────────────────────────────────────────────────────
function SmtpTab({ companyId }) {
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)
  const [testingId, setTestingId] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [form, setForm] = useState({
    purpose: 'NOREPLY', label: '', fromEmail: '', fromName: '',
    host: 'smtp.gmail.com', port: 587, username: '', password: '', useTls: true,
  })

  const { data: configs = [], isLoading } = useQuery({
    queryKey: ['smtp', companyId],
    queryFn: () => getSmtpConfigs(companyId),
  })

  const createMut = useMutation({
    mutationFn: createSmtp,
    onSuccess: () => {
      qc.invalidateQueries(['smtp', companyId])
      setShowModal(false)
      setForm({ purpose: 'NOREPLY', label: '', fromEmail: '', fromName: '', host: 'smtp.gmail.com', port: 587, username: '', password: '', useTls: true })
      toast.success('SMTP config added')
    },
  })

  const toggleMut = useMutation({
    mutationFn: toggleSmtp,
    onSuccess: () => qc.invalidateQueries(['smtp', companyId]),
  })

  const deleteMut = useMutation({
    mutationFn: deleteSmtp,
    onSuccess: () => { qc.invalidateQueries(['smtp', companyId]); setConfirmDelete(null); toast.success('Deleted') },
  })

  const handleTest = async (id) => {
    setTestingId(id)
    try {
      await testSmtp(id)
      qc.invalidateQueries(['smtp', companyId])
      toast.success('SMTP connection verified!')
    } catch (err) {
      toast.error(err.response?.data?.error || 'Connection failed')
    } finally {
      setTestingId(null)
    }
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <p className="text-sm text-gray-500 dark:text-gray-400">
          SMTP configs are shared across all projects in this company.
        </p>
        <button className="btn-primary text-xs px-3 py-1.5" onClick={() => setShowModal(true)}>
          <Plus className="w-3.5 h-3.5" /> Add Config
        </button>
      </div>

      {isLoading ? <div className="flex justify-center py-10"><Spinner /></div>
      : configs.length === 0 ? (
        <EmptyState title="No SMTP configs" description="Add an SMTP config to enable email sending"
          action={<button className="btn-primary" onClick={() => setShowModal(true)}><Plus className="w-4 h-4" /> Add Config</button>}
        />
      ) : (
        <div className="space-y-3">
          {configs.map(cfg => (
            <div key={cfg.id} className="p-4 rounded-xl border border-gray-200 dark:border-gray-800">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <span className={clsx('text-xs font-semibold px-2.5 py-1 rounded-full', PURPOSE_COLORS[cfg.purpose])}>
                    {cfg.purpose}
                  </span>
                  <h4 className="font-semibold text-gray-900 dark:text-gray-100 mt-2">{cfg.label}</h4>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => toggleMut.mutate(cfg.id)} className="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400">
                    {cfg.isActive ? <ToggleRight className="w-4 h-4 text-green-500" /> : <ToggleLeft className="w-4 h-4" />}
                  </button>
                  <button onClick={() => setConfirmDelete(cfg)} className="p-1.5 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-500">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
              <div className="space-y-1 text-xs text-gray-500 dark:text-gray-400 mb-3">
                <p>{cfg.fromName} &lt;{cfg.fromEmail}&gt;</p>
                <p>{cfg.host}:{cfg.port}</p>
              </div>
              <div className="flex items-center justify-between pt-3 border-t border-gray-100 dark:border-gray-800">
                <div className="flex items-center gap-1.5">
                  {cfg.isVerified
                    ? <><CheckCircle className="w-3.5 h-3.5 text-green-500" /><span className="text-xs text-green-600 dark:text-green-400 font-medium">Verified · Active</span></>
                    : <><XCircle className="w-3.5 h-3.5 text-gray-400" /><span className="text-xs text-red-500 dark:text-red-400">Not verified yet</span></>
                  }
                </div>
                <button onClick={() => handleTest(cfg.id)} disabled={testingId === cfg.id}
                  className="text-xs font-medium text-primary-500 hover:text-primary-600 disabled:opacity-50 flex items-center gap-1">
                  {testingId === cfg.id ? <><Loader className="w-3 h-3 animate-spin" /> Testing...</> : 'Test Connection'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Add SMTP Config" size="lg">
        <form onSubmit={e => { e.preventDefault(); createMut.mutate({ ...form, companyId }) }} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">Purpose</label>
              <select className="input" value={form.purpose} onChange={e => setForm(f => ({ ...f, purpose: e.target.value }))}>
                {PURPOSES.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </div>
            <div>
              <label className="label">Label</label>
              <input className="input" value={form.label} onChange={e => setForm(f => ({ ...f, label: e.target.value }))} placeholder="e.g. Gmail No-Reply" required />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">From Email</label>
              <input className="input" type="email" value={form.fromEmail} onChange={e => setForm(f => ({ ...f, fromEmail: e.target.value }))} placeholder="noreply@company.com" required />
            </div>
            <div>
              <label className="label">From Name</label>
              <input className="input" value={form.fromName} onChange={e => setForm(f => ({ ...f, fromName: e.target.value }))} placeholder="My Company" required />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="col-span-2">
              <label className="label">SMTP Host</label>
              <input className="input" value={form.host} onChange={e => setForm(f => ({ ...f, host: e.target.value }))} required />
            </div>
            <div>
              <label className="label">Port</label>
              <input className="input" type="number" value={form.port} onChange={e => setForm(f => ({ ...f, port: Number(e.target.value) }))} required />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">Username</label>
              <input className="input" value={form.username} onChange={e => setForm(f => ({ ...f, username: e.target.value }))} required />
            </div>
            <div>
              <label className="label">Password / App Password</label>
              <input className="input" type="password" value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))} required />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <input type="checkbox" id="tls" checked={form.useTls} onChange={e => setForm(f => ({ ...f, useTls: e.target.checked }))} />
            <label htmlFor="tls" className="text-sm text-gray-700 dark:text-gray-300">Use TLS (STARTTLS)</label>
          </div>
          <div className="flex gap-3 pt-1">
            <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={createMut.isPending}>
              {createMut.isPending ? 'Saving...' : 'Save Config'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={!!confirmDelete} onClose={() => setConfirmDelete(null)} title="Delete SMTP Config" size="sm">
        {confirmDelete && (
          <div className="space-y-4">
            <p className="text-sm text-gray-600 dark:text-gray-300">
              Delete <strong>{confirmDelete.purpose}</strong> config for <strong>{confirmDelete.fromEmail}</strong>?
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

// ── API Keys Tab ──────────────────────────────────────────────────────────────
function ApiKeysTab({ companyId }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { data: projects = [] } = useQuery({ queryKey: ['projects', companyId], queryFn: () => getProjects(companyId) })
  const [projectId, setProjectId] = useState('')
  const [showModal, setShowModal] = useState(false)
  const [newKey, setNewKey] = useState(null)
  const [copied, setCopied] = useState(false)
  const [confirmRevoke, setConfirmRevoke] = useState(null)
  const [form, setForm] = useState({ label: '', rateLimitPerMin: 100 })

  const { data: keys = [], isLoading } = useQuery({
    queryKey: ['keys', projectId],
    queryFn: () => getApiKeys(projectId),
    enabled: !!projectId,
  })

  const selectedProject = projects.find(p => p.id === projectId)
  const envPrefix = selectedProject?.environment === 'PRODUCTION' ? 'live'
    : selectedProject?.environment === 'STAGING' ? 'staging' : 'dev'

  const createMut = useMutation({
    mutationFn: createApiKey,
    onSuccess: (data) => { qc.invalidateQueries(['keys', projectId]); setShowModal(false); setNewKey(data) },
  })

  const revokeMut = useMutation({
    mutationFn: revokeApiKey,
    onSuccess: () => { qc.invalidateQueries(['keys', projectId]); setConfirmRevoke(null); toast.success('Key revoked') },
  })

  const copyKey = () => { navigator.clipboard.writeText(newKey.rawKey); setCopied(true); setTimeout(() => setCopied(false), 2000) }

  return (
    <div>
      <div className="flex items-center gap-4 mb-4 flex-wrap">
        <select className="input max-w-xs text-sm" value={projectId} onChange={e => setProjectId(e.target.value)}>
          <option value="">Select project to view keys...</option>
          {projects.map(p => <option key={p.id} value={p.id}>{p.name} ({p.environment})</option>)}
        </select>
        {projectId && (
          <button className="btn-primary text-xs px-3 py-1.5" onClick={() => setShowModal(true)}>
            <Plus className="w-3.5 h-3.5" /> New Key
          </button>
        )}
      </div>

      {!projectId ? (
        <p className="text-sm text-gray-500 dark:text-gray-400 py-6 text-center">Select a project to see its API keys</p>
      ) : isLoading ? <div className="flex justify-center py-10"><Spinner /></div>
      : keys.length === 0 ? (
        <EmptyState title="No API keys" description="Create the first key for this project"
          action={<button className="btn-primary" onClick={() => setShowModal(true)}><Plus className="w-4 h-4" /> New Key</button>}
        />
      ) : (
        <div className="space-y-2">
          {keys.map(key => (
            <div key={key.id} className="flex items-center justify-between p-3 rounded-xl border border-gray-200 dark:border-gray-800">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <code className="text-xs bg-gray-100 dark:bg-gray-800 px-2 py-0.5 rounded font-mono">
                    {key.keyPrefix}{key.keyHint}
                  </code>
                  <span className={clsx('text-xs font-medium px-2 py-0.5 rounded-md',
                    key.isActive ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'
                  )}>
                    {key.isActive ? 'Active' : 'Revoked'}
                  </span>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  {key.label} · {key.rateLimitPerMin}/min ·{' '}
                  {key.lastUsedAt ? `Last used ${formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true })}` : 'Never used'}
                </p>
              </div>
              {key.isActive && (
                <button onClick={() => setConfirmRevoke(key)} className="p-1.5 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-500">
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <Modal open={showModal} onClose={() => setShowModal(false)} title="Create API Key">
        <form onSubmit={e => { e.preventDefault(); createMut.mutate({ projectId, label: form.label, rateLimitPerMin: form.rateLimitPerMin, environment: envPrefix }) }} className="space-y-4">
          <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800 text-sm">
            Key prefix: <code className="font-mono text-primary-500">tq_{envPrefix}_</code>
            <span className="text-xs text-gray-400 ml-2">(auto from project environment)</span>
          </div>
          <div>
            <label className="label">Label</label>
            <input className="input" value={form.label} onChange={e => setForm(f => ({ ...f, label: e.target.value }))} placeholder="e.g. Production Key Jan 2025" required />
          </div>
          <div>
            <label className="label">Rate limit (req/min)</label>
            <input className="input" type="number" min="1" max="10000" value={form.rateLimitPerMin} onChange={e => setForm(f => ({ ...f, rateLimitPerMin: Number(e.target.value) }))} />
          </div>
          <div className="flex gap-3 pt-1">
            <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={createMut.isPending}>
              {createMut.isPending ? 'Creating...' : 'Create Key'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={!!newKey} onClose={() => setNewKey(null)} title="Save Your API Key">
        {newKey && (
          <div className="space-y-4">
            <div className="flex items-start gap-3 p-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800">
              <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
              <p className="text-sm text-amber-700 dark:text-amber-300 font-medium">
                Shown <strong>only once</strong>. Copy it now.
              </p>
            </div>
            <div className="flex gap-2">
              <code className="flex-1 input font-mono text-xs break-all">{newKey.rawKey}</code>
              <button onClick={copyKey} className={clsx('btn-secondary px-3 flex-shrink-0', copied && 'text-green-500')}>
                {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
            <button className="btn-primary w-full justify-center" onClick={() => setNewKey(null)}>I've saved the key</button>
          </div>
        )}
      </Modal>

      <Modal open={!!confirmRevoke} onClose={() => setConfirmRevoke(null)} title="Revoke API Key" size="sm">
        {confirmRevoke && (
          <div className="space-y-4">
            <p className="text-sm text-gray-600 dark:text-gray-300">
              Revoke <code className="font-mono text-xs bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{confirmRevoke.keyPrefix}{confirmRevoke.keyHint}</code>? This is immediate.
            </p>
            <div className="flex gap-3">
              <button className="btn-secondary flex-1 justify-center" onClick={() => setConfirmRevoke(null)}>Cancel</button>
              <button className="btn-danger flex-1 justify-center" onClick={() => revokeMut.mutate(confirmRevoke.id)} disabled={revokeMut.isPending}>
                {revokeMut.isPending ? 'Revoking...' : 'Revoke'}
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

// ── Jobs Tab ──────────────────────────────────────────────────────────────────
function JobsTab({ companyId }) {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const { data: projects = [] } = useQuery({ queryKey: ['projects', companyId], queryFn: () => getProjects(companyId) })
  const [projectId, setProjectId] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', companyId, projectId, status, page],
    queryFn: () => getJobs({ projectId: projectId || undefined, status: status || undefined, page, size: 10 }),
  })

  const jobs = data?.content || []
  const total = data?.totalElements || 0
  const totalPages = data?.totalPages || 1

  return (
    <div>
      <div className="flex gap-3 mb-4 flex-wrap">
        <select className="input flex-1 min-w-36 text-sm" value={projectId} onChange={e => setProjectId(e.target.value)}>
          <option value="">All projects</option>
          {projects.map(p => <option key={p.id} value={p.id}>{p.name} ({p.environment})</option>)}
        </select>
        <select className="input flex-1 min-w-36 text-sm" value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {['QUEUED','RUNNING','SUCCESS','FAILED','DEAD'].map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {isLoading ? <div className="flex justify-center py-10"><Spinner /></div>
      : jobs.length === 0 ? <EmptyState title="No jobs found" description="No jobs match your filters" />
      : (
        <>
          <div className="space-y-2">
            {jobs.map(job => (
              <div key={job.jobId}
                className="flex items-center justify-between p-3 rounded-xl border border-gray-200 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 cursor-pointer transition-colors"
                onClick={() => navigate(`/jobs/${job.jobId}`)}
              >
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <code className="text-xs font-semibold text-gray-700 dark:text-gray-300">{job.type}</code>
                    <Badge type="status" value={job.status} />
                    <Badge type="priority" value={job.priority} />
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {job.projectName} · {formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
                  </p>
                </div>
                <ChevronRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
              </div>
            ))}
          </div>
          <div className="flex items-center justify-between mt-4 text-xs text-gray-500 dark:text-gray-400">
            <span>{total} total jobs</span>
            <div className="flex gap-2">
              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>Prev</button>
              <span className="px-2 py-1.5">Page {page + 1} / {totalPages}</span>
              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>Next</button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

// ── Main CompanyDetail page ───────────────────────────────────────────────────
export default function CompanyDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState('Projects')

  const { data: companies = [] } = useQuery({ queryKey: ['companies'], queryFn: getCompanies })
  const company = companies.find(c => c.id === id)

  const toggleMut = useMutation({
    mutationFn: toggleCompany,
    onSuccess: () => qc.invalidateQueries(['companies']),
  })

  if (!company && companies.length > 0) return (
    <div className="text-center py-20 text-gray-500">Company not found</div>
  )

  return (
    <div>
      {/* Back */}
      <button onClick={() => navigate('/companies')} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 mb-5 transition-colors">
        <ArrowLeft className="w-4 h-4" /> Back to Companies
      </button>

      {/* Header card */}
      <div className="card mb-6">
        <div className="px-6 py-5 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-start justify-between">
            <div>
              <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100">
                {company?.name || '...'}
              </h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                slug: {company?.slug} · owner: {company?.ownerEmail} ·{' '}
                <button
                  onClick={() => company && toggleMut.mutate(company.id)}
                  className={clsx('font-medium transition-colors', company?.isActive ? 'text-green-600 dark:text-green-400' : 'text-gray-400')}
                >
                  {company?.isActive ? 'Active' : 'Inactive'}
                </button>
              </p>
            </div>
          </div>

          {/* Tabs */}
          <div className="flex gap-0 mt-5 -mb-5 border-b-0">
            {TABS.map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={clsx(
                  'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                  activeTab === tab
                    ? 'border-primary-500 text-primary-600 dark:text-primary-400'
                    : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                )}
              >
                {tab}
              </button>
            ))}
          </div>
        </div>

        {/* Tab content */}
        <div className="p-6">
          {!company ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : activeTab === 'Projects' ? (
            <ProjectsTab companyId={id} companyName={company.name} />
          ) : activeTab === 'SMTP Settings' ? (
            <SmtpTab companyId={id} />
          ) : activeTab === 'API Keys' ? (
            <ApiKeysTab companyId={id} />
          ) : (
            <JobsTab companyId={id} />
          )}
        </div>
      </div>
    </div>
  )
}