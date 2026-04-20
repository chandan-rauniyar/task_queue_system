import { useQuery } from '@tanstack/react-query'
import { LayoutDashboard, Briefcase, CheckCircle, XCircle, Clock, AlertTriangle, Building2, FolderOpen } from 'lucide-react'
import { getMetrics } from '../api/metrics'
import { getJobs } from '../api/jobs'
import { MetricCard } from '../components/dashboard/MetricCard'
import Badge from '../components/ui/Badge'
import { Spinner } from '../components/ui/index.jsx'
import { PageHeader } from '../components/ui/index.jsx'
import { formatDistanceToNow } from 'date-fns'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import { useNavigate } from 'react-router-dom'

const BAR_COLORS = {
  Success: '#1D9E75',
  Failed:  '#E24B4A',
  Queued:  '#378ADD',
  Running: '#BA7517',
  Dead:    '#888780',
}

export default function Dashboard() {
  const navigate = useNavigate()

  const { data: metrics, isLoading: mLoading } = useQuery({
    queryKey: ['metrics'],
    queryFn: getMetrics,
    refetchInterval: 10000,
  })

  const { data: recentJobs, isLoading: jLoading } = useQuery({
    queryKey: ['jobs', 'recent'],
    queryFn: () => getJobs({ page: 0, size: 8 }),
    refetchInterval: 10000,
  })

  const chartData = metrics ? [
    { name: 'Success', value: metrics.successJobs },
    { name: 'Queued',  value: metrics.queuedJobs  },
    { name: 'Running', value: metrics.runningJobs  },
    { name: 'Failed',  value: metrics.failedJobs   },
    { name: 'Dead',    value: metrics.deadJobs     },
  ] : []

  return (
    <div>
      <PageHeader
        title="Dashboard"
        description="System overview and recent activity"
      />

      {mLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : (
        <>
          {/* DLQ Alert */}
          {metrics?.pendingDlq > 0 && (
            <div
              className="mb-6 flex items-center gap-3 px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 cursor-pointer hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
              onClick={() => navigate('/dlq')}
            >
              <AlertTriangle className="w-4 h-4 text-red-500 flex-shrink-0" />
              <p className="text-sm text-red-700 dark:text-red-300">
                <span className="font-semibold">{metrics.pendingDlq} dead jobs</span> waiting in the Dead Letter Queue.
                <span className="underline ml-1">Review and replay →</span>
              </p>
            </div>
          )}

          {/* Metric cards */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <MetricCard title="Total Jobs"   value={metrics?.totalJobs}    icon={Briefcase}  color="purple" />
            <MetricCard title="Success"      value={metrics?.successJobs}  icon={CheckCircle} color="green" />
            <MetricCard title="Failed"       value={metrics?.failedJobs}   icon={XCircle}     color="red"   />
            <MetricCard title="Dead (DLQ)"   value={metrics?.deadJobs}     icon={AlertTriangle} color="gray" />
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
            <MetricCard title="Queued"     value={metrics?.queuedJobs}    icon={Clock}      color="blue"   />
            <MetricCard title="Running"    value={metrics?.runningJobs}   icon={LayoutDashboard} color="amber" />
            <MetricCard title="Companies"  value={metrics?.totalCompanies} icon={Building2} color="purple" />
            <MetricCard title="Projects"   value={metrics?.totalProjects}  icon={FolderOpen} color="green"  />
          </div>

          {/* Chart + recent jobs */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

            {/* Bar chart */}
            <div className="card p-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">Jobs by status</h3>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={chartData} barSize={32}>
                  <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 12, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{
                      background: 'var(--tw-prose-body)',
                      border: 'none',
                      borderRadius: '8px',
                      fontSize: '12px',
                    }}
                  />
                  <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                    {chartData.map((entry) => (
                      <Cell key={entry.name} fill={BAR_COLORS[entry.name] || '#888'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>

            {/* Recent jobs */}
            <div className="card overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-100 dark:border-gray-800">
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Recent jobs</h3>
              </div>
              {jLoading ? (
                <div className="flex justify-center py-10"><Spinner /></div>
              ) : (
                <div className="divide-y divide-gray-100 dark:divide-gray-800">
                  {recentJobs?.content?.map(job => (
                    <div
                      key={job.jobId}
                      className="px-5 py-3 flex items-center justify-between hover:bg-gray-50 dark:hover:bg-gray-800/50 cursor-pointer transition-colors"
                      onClick={() => navigate(`/jobs/${job.jobId}`)}
                    >
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{job.type}</p>
                        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                          {formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
                        </p>
                      </div>
                      <Badge type="status" value={job.status} className="ml-3 flex-shrink-0" />
                    </div>
                  ))}
                  {(!recentJobs?.content?.length) && (
                    <p className="text-sm text-gray-400 text-center py-8">No jobs yet</p>
                  )}
                </div>
              )}
            </div>

          </div>
        </>
      )}
    </div>
  )
}