import { useState } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, Plus, ChevronRight, Trash2, Copy, Check,
  AlertTriangle, CheckCircle, XCircle
} from 'lucide-react'
import { getProjects } from '../api/projects'
import { getCompanies } from '../api/companies'
import { getApiKeys, createApiKey, revokeApiKey } from '../api/apiKeys'
import { getSmtpConfigs } from '../api/smtp'
import { getJobs } from '../api/jobs'
import Badge from '../components/ui/Badge'
import Modal from '../components/ui/Modal'
import { Spinner, EmptyState } from '../components/ui/index.jsx'
import { formatDistanceToNow, format } from 'date-fns'
import toast from 'react-hot-toast'
import clsx from 'clsx'

const TABS = ['Overview', 'API Keys', 'Jobs']

// ── Overview Tab ──────────────────────────────────────────────────────────────
function OverviewTab({ project, companyId }) {
  const navigate = useNavigate()

  const { data: keys = [] } = useQuery({
    queryKey: ['keys', project.id],
    queryFn: () => getApiKeys(project.id),
  })

  const { data: smtpConfigs = [] } = useQuery({
    queryKey: ['smtp', companyId],
    queryFn: () => getSmtpConfigs(companyId),
  })

  const { data: jobsData } = useQuery({
    queryKey: ['jobs', 'overview', project.id],
    queryFn: () => getJobs({ projectId: project.id, page: 0, size: 1 }),
  })

  const total   = jobsData?.totalElements || 0
  const success = 0 // would need a separate metrics endpoint per project
  const activeKeys = keys.filter(k => k.isActive)

  return (
    <div className="space-y-6">
      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[
          { label: 'Total Jobs', value: total, color: 'text-gray-900 dark:text-gray-100' },
          { label: 'Active Keys', value: activeKeys.length, color: 'text-gray-900 dark:text-gray-100' },
          { label: 'SMTP Configs', value: smtpConfigs.length, color: 'text-gray-900 dark:text-gray-100' },
          { label: 'Environment', value: project.environment, color: 'text-gray-900 dark:text-gray-100' },
        ].map(({ label, value, color }) => (
          <div key={label} className="p-4 rounded-xl border border-gray-200 dark:border-gray-800 text-center">
            <p className={clsx('text-2xl font-bold', color)}>{value}</p>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{label}</p>
          </div>
        ))}
      </div>

      {/* API Keys section */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wider">
            API Keys ({keys.length})
          </h3>
        </div>
        {keys.length === 0 ? (
          <p className="text-sm text-gray-500 dark:text-gray-400 py-4">No API keys yet</p>
        ) : (
          <div className="space-y-2">
            {keys.map(key => (
              <div key={key.id} className="flex items-center justify-between p-3 rounded-xl border border-gray-200 dark:border-gray-800">
                <div>
                  <div className="flex items-center gap-2">
                    <code className="text-sm font-bold text-gray-900 dark:text-gray-100 font-mono">
                      {key.keyPrefix} · {key.keyHint}
                    </code>
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                    {key.label} · {key.rateLimitPerMin} req/min ·{' '}
                    {key.lastUsedAt
                      ? `Last used ${formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true })}`
                      : 'Never used'
                    }
                  </p>
                </div>
                <div className="flex items-center gap-1.5">
                  <div className={clsx('w-2 h-2 rounded-full', key.isActive ? 'bg-green-500' : 'bg-gray-400')} />
                  <span className={clsx('text-xs font-medium', key.isActive ? 'text-green-600 dark:text-green-400' : 'text-gray-400')}>
                    {key.isActive ? 'Active' : 'Revoked'}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* SMTP from company */}
      <div>
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wider mb-3">
          SMTP (From Company)
        </h3>
        <div className="p-4 rounded-xl border-l-4 border-primary-500 bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-800">
          {smtpConfigs.length === 0 ? (
            <p className="text-sm text-gray-500 dark:text-gray-400">
              No SMTP configs configured for this company yet.{' '}
              <button onClick={() => navigate(`/companies/${companyId}`)} className="text-primary-500 hover:text-primary-600 underline">
                Configure SMTP →
              </button>
            </p>
          ) : (
            <p className="text-sm text-gray-600 dark:text-gray-300">
              This project uses {project.companyName}'s company SMTP configs.{' '}
              {smtpConfigs.map((cfg, i) => (
                <span key={cfg.id}>
                  {cfg.purpose} → {cfg.fromEmail}{' '}
                  {cfg.isVerified
                    ? <span className="text-green-500">(verified)</span>
                    : <span className="text-red-400">(not verified)</span>
                  }
                  {i < smtpConfigs.length - 1 ? ' · ' : ''}
                </span>
              ))}
              {'  '}
              <button onClick={() => navigate(`/companies/${companyId}`)} className="text-primary-500 hover:text-primary-600 font-medium ml-1">
                Manage SMTP →
              </button>
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

// ── API Keys Tab ──────────────────────────────────────────────────────────────
function ApiKeysTab({ project }) {
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)
  const [newKey, setNewKey] = useState(null)
  const [copied, setCopied] = useState(false)
  const [confirmRevoke, setConfirmRevoke] = useState(null)
  const [form, setForm] = useState({ label: '', rateLimitPerMin: 100 })

  const envPrefix = project.environment === 'PRODUCTION' ? 'live'
    : project.environment === 'STAGING' ? 'staging' : 'dev'

  const { data: keys = [], isLoading } = useQuery({
    queryKey: ['keys', project.id],
    queryFn: () => getApiKeys(project.id),
  })

  const createMut = useMutation({
    mutationFn: createApiKey,
    onSuccess: (data) => { qc.invalidateQueries(['keys', project.id]); setShowModal(false); setNewKey(data) },
  })

  const revokeMut = useMutation({
    mutationFn: revokeApiKey,
    onSuccess: () => { qc.invalidateQueries(['keys', project.id]); setConfirmRevoke(null); toast.success('Key revoked') },
  })

  const copyKey = () => { navigator.clipboard.writeText(newKey.rawKey); setCopied(true); setTimeout(() => setCopied(false), 2000) }

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <p className="text-sm text-gray-500 dark:text-gray-400">
          Keys for <strong>{project.name}</strong> — prefix will be <code className="font-mono text-primary-500">tq_{envPrefix}_</code>
        </p>
        <button className="btn-primary text-xs px-3 py-1.5" onClick={() => setShowModal(true)}>
          <Plus className="w-3.5 h-3.5" /> New Key
        </button>
      </div>

      {isLoading ? <div className="flex justify-center py-10"><Spinner /></div>
      : keys.length === 0 ? (
        <EmptyState title="No API keys" description="Create the first key for this project"
          action={<button className="btn-primary" onClick={() => setShowModal(true)}><Plus className="w-4 h-4" /> New Key</button>}
        />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800/50">
              <tr>
                <th className="table-th">Key</th>
                <th className="table-th">Label</th>
                <th className="table-th">Rate limit</th>
                <th className="table-th">Last used</th>
                <th className="table-th">Status</th>
                <th className="table-th" />
              </tr>
            </thead>
            <tbody>
              {keys.map(key => (
                <tr key={key.id} className="table-row">
                  <td className="table-td">
                    <code className="text-xs bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded font-mono">
                      {key.keyPrefix}{key.keyHint}
                    </code>
                  </td>
                  <td className="table-td font-medium text-gray-900 dark:text-gray-100">{key.label}</td>
                  <td className="table-td text-gray-500">{key.rateLimitPerMin}/min</td>
                  <td className="table-td text-gray-500 text-xs">
                    {key.lastUsedAt ? formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true }) : 'Never'}
                  </td>
                  <td className="table-td">
                    <span className={clsx('text-xs font-medium px-2 py-0.5 rounded-md',
                      key.isActive ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'
                    )}>
                      {key.isActive ? 'Active' : 'Revoked'}
                    </span>
                  </td>
                  <td className="table-td">
                    {key.isActive && (
                      <button onClick={() => setConfirmRevoke(key)} className="p-1.5 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-500">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={showModal} onClose={() => setShowModal(false)} title="Create API Key">
        <form onSubmit={e => { e.preventDefault(); createMut.mutate({ projectId: project.id, label: form.label, rateLimitPerMin: form.rateLimitPerMin, environment: envPrefix }) }} className="space-y-4">
          <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800 text-sm">
            Prefix: <code className="font-mono text-primary-500">tq_{envPrefix}_</code>
          </div>
          <div>
            <label className="label">Label</label>
            <input className="input" value={form.label} onChange={e => setForm(f => ({ ...f, label: e.target.value }))} placeholder="e.g. Production Key" required />
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
              <p className="text-sm text-amber-700 dark:text-amber-300 font-medium">Shown only once. Copy it now.</p>
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

      <Modal open={!!confirmRevoke} onClose={() => setConfirmRevoke(null)} title="Revoke Key" size="sm">
        {confirmRevoke && (
          <div className="space-y-4">
            <p className="text-sm text-gray-600 dark:text-gray-300">Revoke <code className="font-mono text-xs">{confirmRevoke.keyPrefix}{confirmRevoke.keyHint}</code>? Immediate effect.</p>
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
function JobsTab({ project }) {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', 'project', project.id, status, page],
    queryFn: () => getJobs({ projectId: project.id, status: status || undefined, page, size: 10 }),
  })

  const jobs = data?.content || []
  const total = data?.totalElements || 0
  const totalPages = data?.totalPages || 1

  return (
    <div>
      <div className="flex gap-3 mb-4">
        <select className="input max-w-48 text-sm" value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {['QUEUED','RUNNING','SUCCESS','FAILED','DEAD'].map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <span className="text-sm text-gray-500 dark:text-gray-400 self-center">{total} total jobs</span>
      </div>

      {isLoading ? <div className="flex justify-center py-10"><Spinner /></div>
      : jobs.length === 0 ? <EmptyState title="No jobs found" />
      : (
        <>
          <div className="card overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 dark:bg-gray-800/50">
                <tr>
                  <th className="table-th">Type</th>
                  <th className="table-th">Status</th>
                  <th className="table-th">Priority</th>
                  <th className="table-th">Created</th>
                  <th className="table-th" />
                </tr>
              </thead>
              <tbody>
                {jobs.map(job => (
                  <tr key={job.jobId} className="table-row cursor-pointer" onClick={() => navigate(`/jobs/${job.jobId}`)}>
                    <td className="table-td font-mono text-xs">{job.type}</td>
                    <td className="table-td"><Badge type="status" value={job.status} /></td>
                    <td className="table-td"><Badge type="priority" value={job.priority} /></td>
                    <td className="table-td text-xs text-gray-500">{formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}</td>
                    <td className="table-td"><ChevronRight className="w-4 h-4 text-gray-400" /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex justify-between items-center mt-4 text-xs text-gray-500">
            <span>{total} jobs</span>
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

// ── Main ProjectDetail page ───────────────────────────────────────────────────
export default function ProjectDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('Overview')

  // Find project from all companies' projects
  const { data: companies = [] } = useQuery({ queryKey: ['companies'], queryFn: getCompanies })

  // We need to find this project — query all companies' projects
  // Use a dedicated query that fetches project by searching through companies
  const [project, setProject] = useState(null)
  const [companyId, setCompanyId] = useState(null)

  useQuery({
    queryKey: ['project-detail', id, companies.map(c => c.id).join(',')],
    queryFn: async () => {
      for (const company of companies) {
        const { default: api } = await import('../api/axios')
        const res = await api.get(`/admin/companies/${company.id}/projects`)
        const projects = res.data.data || []
        const found = projects.find(p => p.id === id)
        if (found) {
          setProject(found)
          setCompanyId(company.id)
          return found
        }
      }
      return null
    },
    enabled: companies.length > 0,
  })

  if (!project) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  return (
    <div>
      {/* Back */}
      <button
        onClick={() => navigate(companyId ? `/companies/${companyId}` : '/companies')}
        className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 mb-5 transition-colors"
      >
        <ArrowLeft className="w-4 h-4" />
        Companies → {project.companyName} → Projects
      </button>

      {/* Header card */}
      <div className="card mb-6">
        <div className="px-6 py-5 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-3 mb-4">
            <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100">{project.name}</h1>
            <Badge type="env" value={project.environment} />
          </div>

          {/* Tabs */}
          <div className="flex gap-0">
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

        <div className="p-6">
          {activeTab === 'Overview' ? (
            <OverviewTab project={project} companyId={companyId} />
          ) : activeTab === 'API Keys' ? (
            <ApiKeysTab project={project} />
          ) : (
            <JobsTab project={project} />
          )}
        </div>
      </div>
    </div>
  )
}