import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FolderOpen, Plus, ChevronRight } from 'lucide-react'
import { getCompanies } from '../api/companies'
import { getProjects, createProject, toggleProject } from '../api/projects'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Badge from '../components/ui/Badge'
import Modal from '../components/ui/Modal'
import { formatDistanceToNow } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'

const ENVIRONMENTS = ['PRODUCTION', 'STAGING', 'DEV']

export default function Projects() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [selectedCompany, setSelectedCompany] = useState('')
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ name: '', description: '', environment: 'PRODUCTION' })

  const { data: companies = [] } = useQuery({
    queryKey: ['companies'],
    queryFn: getCompanies,
  })

  const { data: projects = [], isLoading } = useQuery({
    queryKey: ['projects', selectedCompany],
    queryFn: () => getProjects(selectedCompany),
    enabled: !!selectedCompany,
  })

  const createMut = useMutation({
    mutationFn: createProject,
    onSuccess: () => {
      qc.invalidateQueries(['projects', selectedCompany])
      setShowModal(false)
      setForm({ name: '', description: '', environment: 'PRODUCTION' })
      toast.success('Project created')
    },
  })

  const toggleMut = useMutation({
    mutationFn: toggleProject,
    onSuccess: () => qc.invalidateQueries(['projects', selectedCompany]),
  })

  const handleSubmit = (e) => {
    e.preventDefault()
    createMut.mutate({ ...form, companyId: selectedCompany })
  }

  // Group by environment
  const grouped = ENVIRONMENTS.reduce((acc, env) => {
    const items = projects.filter(p => p.environment === env)
    if (items.length) acc[env] = items
    return acc
  }, {})

  return (
    <div>
      <PageHeader
        title="Projects"
        description="Manage projects per company"
        action={
          selectedCompany && (
            <button className="btn-primary" onClick={() => setShowModal(true)}>
              <Plus className="w-4 h-4" /> New Project
            </button>
          )
        }
      />

      {/* Company selector */}
      <div className="card p-4 mb-6">
        <label className="label">Select Company</label>
        <select
          className="input max-w-xs"
          value={selectedCompany}
          onChange={e => setSelectedCompany(e.target.value)}
        >
          <option value="">Choose a company...</option>
          {companies.map(c => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </div>

      {!selectedCompany ? (
        <div className="card">
          <EmptyState icon={FolderOpen} title="Select a company" description="Choose a company above to see its projects" />
        </div>
      ) : isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : projects.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={FolderOpen}
            title="No projects yet"
            description="Create the first project for this company"
            action={
              <button className="btn-primary" onClick={() => setShowModal(true)}>
                <Plus className="w-4 h-4" /> New Project
              </button>
            }
          />
        </div>
      ) : (
        <div className="space-y-6">
          {Object.entries(grouped).map(([env, items]) => (
            <div key={env}>
              <h3 className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                <Badge type="env" value={env} />
                {items.length} project{items.length !== 1 ? 's' : ''}
              </h3>
              <div className="card overflow-hidden">
                <table className="w-full">
                  <thead className="bg-gray-50 dark:bg-gray-800/50">
                    <tr>
                      <th className="table-th">Name</th>
                      <th className="table-th">Environment</th>
                      <th className="table-th">Description</th>
                      <th className="table-th">Created</th>
                      <th className="table-th">Status</th>
                      <th className="table-th" />
                    </tr>
                  </thead>
                  <tbody>
                    {items.map(project => (
                      <tr key={project.id} className="table-row">
                        <td className="table-td">
                          <div className="flex items-center gap-2">
                            <FolderOpen className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <span className="font-medium text-gray-900 dark:text-gray-100">{project.name}</span>
                          </div>
                        </td>
                        <td className="table-td">
                          <Badge type="env" value={project.environment} />
                        </td>
                        <td className="table-td text-gray-500 dark:text-gray-400 max-w-xs truncate">
                          {project.description || '—'}
                        </td>
                        <td className="table-td text-gray-500 dark:text-gray-400">
                          {formatDistanceToNow(new Date(project.createdAt), { addSuffix: true })}
                        </td>
                        <td className="table-td">
                          <button
                            onClick={() => toggleMut.mutate(project.id)}
                            className={`text-xs font-medium px-2 py-1 rounded-md transition-colors ${
                              project.isActive
                                ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400'
                                : 'bg-gray-100 dark:bg-gray-800 text-gray-500'
                            }`}
                          >
                            {project.isActive ? 'Active' : 'Inactive'}
                          </button>
                        </td>
                        <td className="table-td">
                          <button
                            onClick={() => navigate(`/projects/${project.id}`)}
                            className="flex items-center gap-1 text-xs text-primary-500 hover:text-primary-600 font-medium"
                          >
                            View <ChevronRight className="w-3.5 h-3.5" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={showModal} onClose={() => setShowModal(false)} title="Create Project">
        <form onSubmit={handleSubmit} className="space-y-4">
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
          <div className="flex gap-3 pt-2">
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