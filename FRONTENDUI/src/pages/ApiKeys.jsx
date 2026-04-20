import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Key, Plus, Copy, Check, Trash2, AlertTriangle } from 'lucide-react'
import { getCompanies } from '../api/companies'
import { getProjects } from '../api/projects'
import { getApiKeys, createApiKey, revokeApiKey } from '../api/apiKeys'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Modal from '../components/ui/Modal'
import Badge from '../components/ui/Badge'
import { formatDistanceToNow, format } from 'date-fns'
import toast from 'react-hot-toast'

export default function ApiKeys() {
  const qc = useQueryClient()
  const [companyId, setCompanyId]   = useState('')
  const [projectId, setProjectId]   = useState('')
  const [showModal, setShowModal]   = useState(false)
  const [newKey, setNewKey]         = useState(null)   // shown once after creation
  const [copied, setCopied]         = useState(false)
  const [confirmRevoke, setConfirmRevoke] = useState(null)
  const [form, setForm] = useState({ label: '', rateLimitPerMin: 100 })

  const { data: companies = [] } = useQuery({ queryKey: ['companies'], queryFn: getCompanies })
  const { data: projects = [] }  = useQuery({
    queryKey: ['projects', companyId], queryFn: () => getProjects(companyId), enabled: !!companyId,
  })
  const { data: keys = [], isLoading } = useQuery({
    queryKey: ['keys', projectId], queryFn: () => getApiKeys(projectId), enabled: !!projectId,
  })

  // Derive environment from project
  const selectedProject = projects.find(p => p.id === projectId)
  const envPrefix = selectedProject?.environment === 'PRODUCTION' ? 'live'
    : selectedProject?.environment === 'STAGING' ? 'staging' : 'dev'

  const createMut = useMutation({
    mutationFn: createApiKey,
    onSuccess: (data) => {
      qc.invalidateQueries(['keys', projectId])
      setShowModal(false)
      setNewKey(data)   // show the raw key modal
      setForm({ label: '', rateLimitPerMin: 100 })
    },
  })

  const revokeMut = useMutation({
    mutationFn: revokeApiKey,
    onSuccess: () => {
      qc.invalidateQueries(['keys', projectId])
      setConfirmRevoke(null)
      toast.success('Key revoked')
    },
  })

  const copyKey = () => {
    navigator.clipboard.writeText(newKey.rawKey)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div>
      <PageHeader
        title="API Keys"
        description="Manage API keys per project"
        action={projectId && (
          <button className="btn-primary" onClick={() => setShowModal(true)}>
            <Plus className="w-4 h-4" /> New Key
          </button>
        )}
      />

      {/* Selectors */}
      <div className="card p-4 mb-6 flex flex-wrap gap-4">
        <div className="flex-1 min-w-48">
          <label className="label">Company</label>
          <select className="input" value={companyId} onChange={e => { setCompanyId(e.target.value); setProjectId('') }}>
            <option value="">Select company...</option>
            {companies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <div className="flex-1 min-w-48">
          <label className="label">Project</label>
          <select className="input" value={projectId} onChange={e => setProjectId(e.target.value)} disabled={!companyId}>
            <option value="">Select project...</option>
            {projects.map(p => <option key={p.id} value={p.id}>{p.name} ({p.environment})</option>)}
          </select>
        </div>
      </div>

      {!projectId ? (
        <div className="card"><EmptyState icon={Key} title="Select a project" description="Choose company and project above" /></div>
      ) : isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : keys.length === 0 ? (
        <div className="card">
          <EmptyState icon={Key} title="No API keys" description="Create the first key for this project"
            action={<button className="btn-primary" onClick={() => setShowModal(true)}><Plus className="w-4 h-4" /> New Key</button>}
          />
        </div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800/50">
              <tr>
                <th className="table-th">Key</th>
                <th className="table-th">Label</th>
                <th className="table-th">Rate limit</th>
                <th className="table-th">Last used</th>
                <th className="table-th">Expires</th>
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
                  <td className="table-td text-gray-500 dark:text-gray-400 text-xs">
                    {key.lastUsedAt ? formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true }) : 'Never'}
                  </td>
                  <td className="table-td text-gray-500 dark:text-gray-400 text-xs">
                    {key.expiresAt ? format(new Date(key.expiresAt), 'MMM d, yyyy') : 'Never'}
                  </td>
                  <td className="table-td">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-md ${key.isActive ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'}`}>
                      {key.isActive ? 'Active' : 'Revoked'}
                    </span>
                  </td>
                  <td className="table-td">
                    {key.isActive && (
                      <button
                        onClick={() => setConfirmRevoke(key)}
                        className="p-1.5 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-500 transition-colors"
                        title="Revoke key"
                      >
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

      {/* Create key modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Create API Key">
        <form onSubmit={e => { e.preventDefault(); createMut.mutate({ projectId, label: form.label, rateLimitPerMin: form.rateLimitPerMin, environment: envPrefix }) }} className="space-y-4">
          <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800 text-sm text-gray-600 dark:text-gray-300">
            Key prefix: <code className="font-mono text-primary-500">tq_{envPrefix}_</code>
            <span className="text-xs text-gray-400 ml-2">(auto from project environment)</span>
          </div>
          <div>
            <label className="label">Label</label>
            <input className="input" value={form.label} onChange={e => setForm(f => ({ ...f, label: e.target.value }))} placeholder="e.g. Production Key Jan 2025" required />
          </div>
          <div>
            <label className="label">Rate limit (requests/min)</label>
            <input className="input" type="number" min="1" max="10000" value={form.rateLimitPerMin} onChange={e => setForm(f => ({ ...f, rateLimitPerMin: Number(e.target.value) }))} />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={createMut.isPending}>
              {createMut.isPending ? 'Creating...' : 'Create Key'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Raw key — shown once */}
      <Modal open={!!newKey} onClose={() => setNewKey(null)} title="Save Your API Key">
        {newKey && (
          <div className="space-y-4">
            <div className="flex items-start gap-3 p-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800">
              <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
              <p className="text-sm text-amber-700 dark:text-amber-300 font-medium">
                This key is shown <strong>only once</strong>. Copy it now — you cannot retrieve it again.
              </p>
            </div>
            <div>
              <label className="label">Your API Key</label>
              <div className="flex gap-2">
                <code className="flex-1 input font-mono text-xs break-all">{newKey.rawKey}</code>
                <button onClick={copyKey} className={`btn-secondary px-3 flex-shrink-0 ${copied ? 'text-green-500' : ''}`}>
                  {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </div>
            <p className="text-xs text-gray-500">Hint: <code className="font-mono">{newKey.keyHint}</code></p>
            <button className="btn-primary w-full justify-center" onClick={() => setNewKey(null)}>
              I've saved the key
            </button>
          </div>
        )}
      </Modal>

      {/* Confirm revoke */}
      <Modal open={!!confirmRevoke} onClose={() => setConfirmRevoke(null)} title="Revoke API Key" size="sm">
        {confirmRevoke && (
          <div className="space-y-4">
            <p className="text-sm text-gray-600 dark:text-gray-300">
              Revoke <code className="font-mono text-xs bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{confirmRevoke.keyPrefix}{confirmRevoke.keyHint}</code>?
              This takes effect immediately. Any app using this key will get 401 errors.
            </p>
            <div className="flex gap-3">
              <button className="btn-secondary flex-1 justify-center" onClick={() => setConfirmRevoke(null)}>Cancel</button>
              <button className="btn-danger flex-1 justify-center" onClick={() => revokeMut.mutate(confirmRevoke.id)} disabled={revokeMut.isPending}>
                {revokeMut.isPending ? 'Revoking...' : 'Revoke Key'}
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}