import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, RefreshCw, RotateCcw } from 'lucide-react'
import { getDlq, replaySingle, replayAll } from '../api/dlq'
import { PageHeader, EmptyState, Spinner } from '../components/ui/index.jsx'
import Modal from '../components/ui/Modal'
import { format, formatDistanceToNow } from 'date-fns'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'

export default function DeadLetterQueue() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [confirmAll, setConfirmAll] = useState(false)

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['dlq', page],
    queryFn: () => getDlq({ page, size: 20 }),
  })

  const jobs = data?.content || []
  const total = data?.totalElements || 0
  const totalPages = data?.totalPages || 1

  const replayOneMut = useMutation({
    mutationFn: replaySingle,
    onSuccess: () => { qc.invalidateQueries(['dlq']); qc.invalidateQueries(['metrics']); toast.success('Job re-queued') },
  })

  const replayAllMut = useMutation({
    mutationFn: replayAll,
    onSuccess: (data) => {
      qc.invalidateQueries(['dlq']); qc.invalidateQueries(['metrics'])
      setConfirmAll(false)
      toast.success(`${data.replayedCount} jobs re-queued`)
    },
  })

  return (
    <div>
      <PageHeader
        title="Dead Letter Queue"
        description={`${total} jobs waiting for replay`}
        action={
          total > 0 && (
            <button className="btn-primary" onClick={() => setConfirmAll(true)}>
              <RotateCcw className="w-4 h-4" /> Replay All ({total})
            </button>
          )
        }
      />

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : jobs.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={AlertTriangle}
            title="No dead jobs"
            description="All jobs are processing normally"
          />
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
                        onClick={() => navigate(`/jobs/${dlq.job?.jobId}`)}
                      >
                        {dlq.job?.type || 'Unknown'}
                      </button>
                    </td>
                    <td className="table-td">
                      <p className="text-xs text-red-600 dark:text-red-400 max-w-xs truncate" title={dlq.failureReason}>
                        {dlq.failureReason}
                      </p>
                    </td>
                    <td className="table-td text-center text-gray-500 dark:text-gray-400">
                      {dlq.retryCount}
                    </td>
                    <td className="table-td text-gray-500 dark:text-gray-400 text-xs">
                      {formatDistanceToNow(new Date(dlq.failedAt), { addSuffix: true })}
                    </td>
                    <td className="table-td">
                      {dlq.replayedAt ? (
                        <span className="text-xs text-green-600 dark:text-green-400 font-medium">
                          Replayed {formatDistanceToNow(new Date(dlq.replayedAt), { addSuffix: true })}
                        </span>
                      ) : (
                        <span className="text-xs text-gray-500">Pending</span>
                      )}
                    </td>
                    <td className="table-td">
                      {!dlq.replayedAt && (
                        <button
                          onClick={() => replayOneMut.mutate(dlq.id)}
                          disabled={replayOneMut.isPending}
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

          <div className="flex items-center justify-between mt-4 text-sm text-gray-500 dark:text-gray-400">
            <span>{total} total dead jobs</span>
            <div className="flex gap-2">
              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>Previous</button>
              <span className="px-3 py-1.5 text-xs">Page {page + 1} of {totalPages}</span>
              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>Next</button>
            </div>
          </div>
        </>
      )}

      <Modal open={confirmAll} onClose={() => setConfirmAll(false)} title="Replay All Dead Jobs" size="sm">
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            Re-queue all <strong>{total}</strong> dead jobs for processing. Each job will run again from the beginning.
          </p>
          <div className="flex gap-3">
            <button className="btn-secondary flex-1 justify-center" onClick={() => setConfirmAll(false)}>Cancel</button>
            <button className="btn-primary flex-1 justify-center" onClick={() => replayAllMut.mutate()} disabled={replayAllMut.isPending}>
              {replayAllMut.isPending ? 'Replaying...' : 'Replay All'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  )
}