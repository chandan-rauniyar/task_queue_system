import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Building2, Plus, ToggleLeft, ToggleRight, ChevronRight } from 'lucide-react'
import { getCompanies, createCompany, toggleCompany } from '../api/companies'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Modal from '../components/ui/Modal'
import { formatDistanceToNow } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import clsx from 'clsx'

export default function Companies() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ name: '', slug: '' })

  const { data: companies = [], isLoading } = useQuery({
    queryKey: ['companies'],
    queryFn: getCompanies,
  })

  const createMut = useMutation({
    mutationFn: createCompany,
    onSuccess: () => {
      qc.invalidateQueries(['companies'])
      setShowModal(false)
      setForm({ name: '', slug: '' })
      toast.success('Company created')
    },
  })

  const toggleMut = useMutation({
    mutationFn: toggleCompany,
    onSuccess: () => qc.invalidateQueries(['companies']),
  })

  const handleSubmit = (e) => {
    e.preventDefault()
    createMut.mutate(form)
  }

  const autoSlug = (name) => name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '')

  return (
    <div>
      <PageHeader
        title="Companies"
        description={`${companies.length} companies registered`}
        action={
          <button className="btn-primary" onClick={() => setShowModal(true)}>
            <Plus className="w-4 h-4" /> New Company
          </button>
        }
      />

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : companies.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Building2}
            title="No companies yet"
            description="Create your first company to get started"
            action={
              <button className="btn-primary" onClick={() => setShowModal(true)}>
                <Plus className="w-4 h-4" /> New Company
              </button>
            }
          />
        </div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800/50">
              <tr>
                <th className="table-th">Company</th>
                <th className="table-th">Slug</th>
                <th className="table-th">Owner</th>
                <th className="table-th">Created</th>
                <th className="table-th">Status</th>
                <th className="table-th" />
              </tr>
            </thead>
            <tbody>
              {companies.map(company => (
                <tr key={company.id} className="table-row">
                  <td className="table-td">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center">
                        <Building2 className="w-4 h-4 text-primary-500" />
                      </div>
                      <span className="font-medium text-gray-900 dark:text-gray-100">
                        {company.name}
                      </span>
                    </div>
                  </td>
                  <td className="table-td">
                    <code className="text-xs bg-gray-100 dark:bg-gray-800 px-2 py-0.5 rounded">
                      {company.slug}
                    </code>
                  </td>
                  <td className="table-td text-gray-500 dark:text-gray-400">{company.ownerEmail}</td>
                  <td className="table-td text-gray-500 dark:text-gray-400">
                    {formatDistanceToNow(new Date(company.createdAt), { addSuffix: true })}
                  </td>
                  <td className="table-td">
                    <button
                      onClick={() => toggleMut.mutate(company.id)}
                      className={clsx(
                        'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-1 rounded-md transition-colors',
                        company.isActive
                          ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400 hover:bg-green-100 dark:hover:bg-green-900/40'
                          : 'bg-gray-100 dark:bg-gray-800 text-gray-500 hover:bg-gray-200 dark:hover:bg-gray-700'
                      )}
                    >
                      {company.isActive
                        ? <><ToggleRight className="w-3.5 h-3.5" /> Active</>
                        : <><ToggleLeft className="w-3.5 h-3.5" /> Inactive</>
                      }
                    </button>
                  </td>
                  <td className="table-td">
                    <button
                      onClick={() => navigate(`/companies/${company.id}`)}
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
      )}

      {/* Create modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Create Company">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Company name</label>
            <input
              className="input"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value, slug: autoSlug(e.target.value) }))}
              placeholder="e.g. Swiggy"
              required
            />
          </div>
          <div>
            <label className="label">Slug</label>
            <input
              className="input"
              value={form.slug}
              onChange={e => setForm(f => ({ ...f, slug: e.target.value }))}
              placeholder="e.g. swiggy"
              pattern="^[a-z0-9-]+$"
              required
            />
            <p className="text-xs text-gray-400 mt-1">Lowercase letters, numbers and hyphens only</p>
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowModal(false)}>
              Cancel
            </button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={createMut.isPending}>
              {createMut.isPending ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}