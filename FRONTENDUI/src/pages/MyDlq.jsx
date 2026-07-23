import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { getClientDlq, replayClientDlq } from '../api/client'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import { formatDistanceToNow } from 'date-fns'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'

export default function MyDlq() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['client-dlq', page],
    queryFn: () => getClientDlq({ page, size: 20 }),
  })

  const replayMut = useMutation({
    mutationFn: replayClientDlq,
    onSuccess: () => { qc.invalidateQueries(['client-dlq']); toast.success('Job re-queued') },
  })

  const jobs = data?.content || []
  const total = data?.totalElements || 0
  const totalPages = data?.totalPages || 1

  return (
    <div>
      <PageHeader title="Dead Letter Queue" description={`${total} jobs waiting for replay`} />

      {isLoading ? <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      : jobs.length === 0 ? (
        <div className="card">
          <EmptyState icon={AlertTriangle} title="No dead jobs" description="All your jobs are processing normally" />
        </div>
      ) : (
        <>
          <div className="card overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 dark:bg-gray-800/50">
                <tr>
                  <th className="table-th">Job Type</th>
                  <th className="table-th">Failure Reason</th>
                  <th className="table-th">Retries</th>
                  <th className="table-th">Failed</th>
                  <th className="table-th">Status</th>
                  <th className="table-th" />
                </tr>
              </thead>
              <tbody>
                {jobs.map(dlq => (
                  <tr key={dlq.id} className="table-row">
                    <td className="table-td">
                      <button
                        className="font-mono text-xs text-primary-500 hover:text-primary-600 hover:underline"
                        onClick={() => navigate(`/my-jobs/${dlq.job?.jobId}`)}
                      >
                        {dlq.job?.type || 'Unknown'}
                      </button>
                    </td>
                    <td className="table-td">
                      <p className="text-xs text-red-600 dark:text-red-400 max-w-xs truncate" title={dlq.failureReason}>
                        {dlq.failureReason}
                      </p>
                    </td>
                    <td className="table-td text-center text-gray-500">{dlq.retryCount}</td>
                    <td className="table-td text-gray-500 text-xs">
                      {formatDistanceToNow(new Date(dlq.failedAt), { addSuffix: true })}
                    </td>
                    <td className="table-td">
                      {dlq.replayedAt
                        ? <span className="text-xs text-green-600 dark:text-green-400 font-medium">Replayed</span>
                        : <span className="text-xs text-gray-500">Pending</span>
                      }
                    </td>
                    <td className="table-td">
                      {!dlq.replayedAt && (
                        <button
                          onClick={() => replayMut.mutate(dlq.id)}
                          disabled={replayMut.isPending}
                          className="flex items-center gap-1 text-xs text-primary-500 hover:text-primary-600 font-medium"
                        >
                          <RefreshCw className="w-3 h-3" /> Replay
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex justify-between items-center mt-4 text-xs text-gray-500">
            <span>{total} total dead jobs</span>
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