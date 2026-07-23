import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Clock, CheckCircle, XCircle, RefreshCw } from 'lucide-react'
import { getClientJob } from '../api/client'
import Badge from '../components/ui/Badge'
import { Spinner } from '../components/ui/index.jsx'
import { format, formatDistanceToNow } from 'date-fns'
import clsx from 'clsx'

// Reuse timeline from JobDetail style
function TimelineItem({ label, time, icon: Icon, color, isLast }) {
  if (!time) return null
  return (
    <div className="flex items-start gap-3">
      <div className="flex flex-col items-center">
        <div className={clsx('w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0', color)}>
          <Icon className="w-3.5 h-3.5" />
        </div>
        {!isLast && <div className="w-0.5 h-6 bg-gray-200 dark:bg-gray-700 mt-1" />}
      </div>
      <div className="pb-1">
        <p className="text-xs font-medium text-gray-700 dark:text-gray-300">{label}</p>
        <p className="text-xs text-gray-500 dark:text-gray-400">{format(new Date(time), 'MMM d, yyyy HH:mm:ss')}</p>
        <p className="text-xs text-gray-400">{formatDistanceToNow(new Date(time), { addSuffix: true })}</p>
      </div>
    </div>
  )
}

export default function MyJobDetail() {
  const { id } = useParams()
  const navigate = useNavigate()

  const { data: job, isLoading } = useQuery({
    queryKey: ['client-job', id],
    queryFn: () => getClientJob(id),
  })

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  if (!job) return <p className="text-center text-gray-500 py-20">Job not found</p>

  const durationMs = job.startedAt && job.completedAt
    ? new Date(job.completedAt) - new Date(job.startedAt)
    : null

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => navigate(-1)} className="btn-secondary p-2">
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">{job.type}</h1>
            <Badge type="status" value={job.status} />
            <Badge type="priority" value={job.priority} />
          </div>
          <p className="text-xs text-gray-400 font-mono mt-0.5 truncate">{job.jobId}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-5">
          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">Job Details</h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
              {[
                ['Status',   <Badge type="status" value={job.status} />],
                ['Priority', <Badge type="priority" value={job.priority} />],
                ['Project',  job.projectName],
                ['Retries',  `${job.retryCount} / ${job.maxRetries}`],
                ['Duration', durationMs !== null
                  ? durationMs < 1000 ? `${durationMs}ms` : `${(durationMs/1000).toFixed(2)}s`
                  : '—'
                ],
              ].map(([label, val]) => (
                <div key={label}>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">{label}</p>
                  <div className="text-sm font-medium text-gray-900 dark:text-gray-100">{val}</div>
                </div>
              ))}
            </div>
          </div>

          {job.errorMessage && (
            <div className="card p-5 border-red-200 dark:border-red-800">
              <div className="flex items-start gap-2 mb-3">
                <XCircle className="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
                <h3 className="text-sm font-semibold text-red-700 dark:text-red-400">Error</h3>
              </div>
              <p className="text-xs text-red-600 dark:text-red-300 bg-red-50 dark:bg-red-900/20 p-3 rounded-lg font-mono break-all">
                {job.errorMessage}
              </p>
            </div>
          )}

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">Payload</h3>
            <pre className="text-xs bg-gray-50 dark:bg-gray-800 p-4 rounded-lg overflow-auto max-h-80 font-mono whitespace-pre-wrap break-all text-gray-700 dark:text-gray-300">
              {JSON.stringify(job.payload, null, 2)}
            </pre>
          </div>
        </div>

        <div className="space-y-5">
          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">Timeline</h3>
            <div className="space-y-1">
              <TimelineItem label="Created"   time={job.createdAt}   icon={Clock}        color="bg-blue-100 dark:bg-blue-900/30 text-blue-600" />
              <TimelineItem label="Started"   time={job.startedAt}   icon={RefreshCw}    color="bg-amber-100 dark:bg-amber-900/30 text-amber-600" />
              <TimelineItem label="Completed" time={job.completedAt} icon={CheckCircle}  color="bg-green-100 dark:bg-green-900/30 text-green-600" isLast />
            </div>
          </div>

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">Retry Progress</h3>
            <div className="space-y-2">
              <div className="flex justify-between text-xs">
                <span className="text-gray-500">Attempts</span>
                <span className="font-medium text-gray-900 dark:text-gray-100">{job.retryCount} / {job.maxRetries}</span>
              </div>
              <div className="h-1.5 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                <div
                  className={clsx('h-full rounded-full', job.retryCount >= job.maxRetries ? 'bg-red-500' : 'bg-primary-500')}
                  style={{ width: `${Math.min((job.retryCount / job.maxRetries) * 100, 100)}%` }}
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}