import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Briefcase, RefreshCw, ChevronRight } from 'lucide-react'
import { getJobs, getJob, retryJob } from '../api/jobs'
import { getCompanies } from '../api/companies'
import { getProjects } from '../api/projects'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Badge from '../components/ui/Badge'
import { formatDistanceToNow, format } from 'date-fns'
import { useNavigate } from 'react-router-dom'

const STATUSES = ['QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD']

export default function Jobs() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState({ status: '', projectId: '' })
  const [page, setPage] = useState(0)
  const [companyId, setCompanyId] = useState('')

  const { data: companies = [] } = useQuery({ queryKey: ['companies'], queryFn: getCompanies })
  const { data: projects = [] }  = useQuery({
    queryKey: ['projects', companyId], queryFn: () => getProjects(companyId), enabled: !!companyId,
  })

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', filters, page],
    queryFn: () => getJobs({ ...filters, page, size: 20 }),
    keepPreviousData: true,
  })

  const jobs = data?.content || []
  const total = data?.totalElements || 0
  const totalPages = data?.totalPages || 1

  return (
    <div>
      <PageHeader title="Jobs" description={`${total.toLocaleString()} total jobs`} />

      {/* Filters */}
      <div className="card p-4 mb-6 flex flex-wrap gap-4">
        <div className="flex-1 min-w-40">
          <label className="label">Company</label>
          <select className="input" value={companyId} onChange={e => { setCompanyId(e.target.value); setFilters(f => ({ ...f, projectId: '' })) }}>
            <option value="">All companies</option>
            {companies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <div className="flex-1 min-w-40">
          <label className="label">Project</label>
          <select className="input" value={filters.projectId} onChange={e => setFilters(f => ({ ...f, projectId: e.target.value }))}>
            <option value="">All projects</option>
            {projects.map(p => <option key={p.id} value={p.id}>{p.name} ({p.environment})</option>)}
          </select>
        </div>
        <div className="flex-1 min-w-40">
          <label className="label">Status</label>
          <select className="input" value={filters.status} onChange={e => setFilters(f => ({ ...f, status: e.target.value }))}>
            <option value="">All statuses</option>
            {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : jobs.length === 0 ? (
        <div className="card"><EmptyState icon={Briefcase} title="No jobs found" description="Try adjusting your filters" /></div>
      ) : (
        <>
          <div className="card overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 dark:bg-gray-800/50">
                <tr>
                  <th className="table-th">Type</th>
                  <th className="table-th">Status</th>
                  <th className="table-th">Priority</th>
                  <th className="table-th">Project</th>
                  <th className="table-th">Company</th>
                  <th className="table-th">Created</th>
                  <th className="table-th" />
                </tr>
              </thead>
              <tbody>
                {jobs.map(job => (
                  <tr key={job.jobId} className="table-row cursor-pointer" onClick={() => navigate(`/jobs/${job.jobId}`)}>
                    <td className="table-td font-mono text-xs text-gray-700 dark:text-gray-300">{job.type}</td>
                    <td className="table-td"><Badge type="status" value={job.status} /></td>
                    <td className="table-td"><Badge type="priority" value={job.priority} /></td>
                    <td className="table-td text-gray-500 dark:text-gray-400 text-xs">{job.projectName}</td>
                    <td className="table-td text-gray-500 dark:text-gray-400 text-xs">{job.companyName}</td>
                    <td className="table-td text-gray-500 dark:text-gray-400 text-xs">
                      {formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
                    </td>
                    <td className="table-td"><ChevronRight className="w-4 h-4 text-gray-400" /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          <div className="flex items-center justify-between mt-4 text-sm text-gray-500 dark:text-gray-400">
            <span>Showing {jobs.length} of {total}</span>
            <div className="flex gap-2">
              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>Previous</button>
              <span className="px-3 py-1.5 text-xs">Page {page + 1} of {totalPages}</span>
              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>Next</button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}