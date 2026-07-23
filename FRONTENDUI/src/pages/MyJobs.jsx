import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Briefcase, ChevronRight } from 'lucide-react'
import { getClientJobs, getClientProjects } from '../api/client'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Badge from '../components/ui/Badge'
import { formatDistanceToNow } from 'date-fns'
import { useNavigate } from 'react-router-dom'

const STATUSES = ['QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD']

export default function MyJobs() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState({ status: '', projectId: '' })
  const [page, setPage] = useState(0)

  const { data: projects = [] } = useQuery({
    queryKey: ['client-projects'],
    queryFn: getClientProjects,
  })

  const { data, isLoading } = useQuery({
    queryKey: ['client-jobs', filters, page],
    queryFn: () => getClientJobs({
      projectId: filters.projectId || undefined,
      status:    filters.status    || undefined,
      page,
      size: 20,
    }),
  })

  const jobs = data?.content || []
  const total = data?.totalElements || 0
  const totalPages = data?.totalPages || 1

  return (
    <div>
      <PageHeader
        title="Jobs"
        description={`${total.toLocaleString()} total jobs across your projects`}
      />

      {/* Filters */}
      <div className="card p-4 mb-6 flex flex-wrap gap-4">
        <div className="flex-1 min-w-40">
          <label className="label">Project</label>
          <select
            className="input"
            value={filters.projectId}
            onChange={e => { setFilters(f => ({ ...f, projectId: e.target.value })); setPage(0) }}
          >
            <option value="">All projects</option>
            {projects.map(p => (
              <option key={p.id} value={p.id}>{p.name} ({p.environment})</option>
            ))}
          </select>
        </div>
        <div className="flex-1 min-w-40">
          <label className="label">Status</label>
          <select
            className="input"
            value={filters.status}
            onChange={e => { setFilters(f => ({ ...f, status: e.target.value })); setPage(0) }}
          >
            <option value="">All statuses</option>
            {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : jobs.length === 0 ? (
        <div className="card">
          <EmptyState icon={Briefcase} title="No jobs found" description="Try adjusting your filters" />
        </div>
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
                  <th className="table-th">Created</th>
                  <th className="table-th" />
                </tr>
              </thead>
              <tbody>
                {jobs.map(job => (
                  <tr
                    key={job.jobId}
                    className="table-row cursor-pointer"
                    onClick={() => navigate(`/my-jobs/${job.jobId}`)}
                  >
                    <td className="table-td font-mono text-xs">{job.type}</td>
                    <td className="table-td"><Badge type="status" value={job.status} /></td>
                    <td className="table-td"><Badge type="priority" value={job.priority} /></td>
                    <td className="table-td text-gray-500 text-xs">{job.projectName}</td>
                    <td className="table-td text-gray-500 text-xs">
                      {formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
                    </td>
                    <td className="table-td"><ChevronRight className="w-4 h-4 text-gray-400" /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between mt-4 text-sm text-gray-500">
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