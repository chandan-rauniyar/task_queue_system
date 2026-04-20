import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, RefreshCw, Clock, CheckCircle, XCircle,
  Code, Eye, Copy, Check, Mail, FileText,
  ChevronRight, ChevronDown
} from 'lucide-react'
import { getJob, retryJob } from '../api/jobs'
import Badge from '../components/ui/Badge'
import { Spinner } from '../components/ui/index.jsx'
import { format, formatDistanceToNow } from 'date-fns'
import toast from 'react-hot-toast'
import clsx from 'clsx'

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
        <p className="text-xs text-gray-400 dark:text-gray-500">{formatDistanceToNow(new Date(time), { addSuffix: true })}</p>
      </div>
    </div>
  )
}

function EmailPreview({ payload }) {
  const [showHtml, setShowHtml] = useState(false)
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        {[['To', payload.to], ['Subject', payload.subject], ['CC', payload.cc]].filter(([, v]) => v).map(([label, value]) => (
          <div key={label} className="flex gap-3">
            <span className="text-xs font-medium text-gray-500 dark:text-gray-400 w-14 flex-shrink-0 pt-0.5">{label}</span>
            <span className="text-sm text-gray-900 dark:text-gray-100 break-all">{value}</span>
          </div>
        ))}
      </div>
      <div className="border-t border-gray-100 dark:border-gray-800" />
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-gray-500 dark:text-gray-400">Email Body</span>
        <div className="flex rounded-lg overflow-hidden border border-gray-200 dark:border-gray-700">
          <button onClick={() => setShowHtml(false)} className={clsx('flex items-center gap-1.5 px-3 py-1 text-xs font-medium transition-colors', !showHtml ? 'bg-primary-500 text-white' : 'bg-white dark:bg-gray-900 text-gray-500 hover:bg-gray-50 dark:hover:bg-gray-800')}>
            <Eye className="w-3 h-3" /> Preview
          </button>
          <button onClick={() => setShowHtml(true)} className={clsx('flex items-center gap-1.5 px-3 py-1 text-xs font-medium transition-colors border-l border-gray-200 dark:border-gray-700', showHtml ? 'bg-primary-500 text-white' : 'bg-white dark:bg-gray-900 text-gray-500 hover:bg-gray-50 dark:hover:bg-gray-800')}>
            <Code className="w-3 h-3" /> HTML
          </button>
        </div>
      </div>
      {showHtml ? (
        <pre className="text-xs bg-gray-50 dark:bg-gray-800 p-4 rounded-lg overflow-auto max-h-64 text-gray-700 dark:text-gray-300 font-mono whitespace-pre-wrap break-all">{payload.body}</pre>
      ) : (
        <div className="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden bg-white">
          <div className="bg-gray-50 dark:bg-gray-800 px-3 py-1.5 border-b border-gray-200 dark:border-gray-700 flex items-center gap-1.5">
            <div className="w-2.5 h-2.5 rounded-full bg-red-400" />
            <div className="w-2.5 h-2.5 rounded-full bg-amber-400" />
            <div className="w-2.5 h-2.5 rounded-full bg-green-400" />
            <span className="text-xs text-gray-400 ml-2">Email Preview</span>
          </div>
          <iframe
            srcDoc={`<!DOCTYPE html><html><head><meta charset="utf-8"><style>body{font-family:system-ui,sans-serif;padding:16px;margin:0;color:#111;font-size:14px;line-height:1.6}*{max-width:100%;box-sizing:border-box}</style></head><body>${payload.body || ''}</body></html>`}
            className="w-full border-0"
            style={{ height: '240px' }}
            sandbox="allow-same-origin"
            title="Email body preview"
          />
        </div>
      )}
      {payload.attachments?.length > 0 && (
        <div>
          <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">Attachments ({payload.attachments.length})</p>
          <div className="space-y-1.5">
            {payload.attachments.map((att, i) => (
              <div key={i} className="flex items-center gap-2 p-2 rounded-lg bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
                <FileText className="w-4 h-4 text-gray-400 flex-shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate">{att.filename}</p>
                  {att.contentType && <p className="text-xs text-gray-400">{att.contentType}</p>}
                </div>
                {att.content && <span className="text-xs text-gray-400 flex-shrink-0">~{Math.round(att.content.length * 0.75 / 1024)}KB</span>}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function JsonNode({ data, depth = 0 }) {
  const [collapsed, setCollapsed] = useState(depth > 1)
  if (data === null) return <span className="text-gray-400">null</span>
  if (typeof data === 'boolean') return <span className="text-amber-500 dark:text-amber-400">{String(data)}</span>
  if (typeof data === 'number') return <span className="text-blue-500 dark:text-blue-400">{data}</span>
  if (typeof data === 'string') {
    if (data.includes('<') && data.includes('>') && data.length > 60)
      return <span className="text-green-600 dark:text-green-400 italic">"{data.slice(0, 60)}… [HTML]"</span>
    return <span className="text-green-600 dark:text-green-400">"{data}"</span>
  }
  const isArray = Array.isArray(data)
  const entries = isArray ? data.map((v, i) => [i, v]) : Object.entries(data)
  const [o, c] = isArray ? ['[', ']'] : ['{', '}']
  if (entries.length === 0) return <span className="text-gray-400">{o}{c}</span>
  return (
    <span>
      <button onClick={() => setCollapsed(x => !x)} className="inline-flex items-center text-gray-500 hover:text-gray-700 dark:hover:text-gray-200">
        {collapsed ? <ChevronRight className="w-3 h-3 inline" /> : <ChevronDown className="w-3 h-3 inline" />}
        <span className="text-gray-400">{o}</span>
        {collapsed && <span className="text-gray-400 text-xs ml-1">{entries.length} {isArray ? 'items' : 'keys'}</span>}
      </button>
      {collapsed ? <span className="text-gray-400">{c}</span> : (
        <span>
          {entries.map(([key, value]) => (
            <div key={key} style={{ paddingLeft: `${(depth + 1) * 12}px` }}>
              {!isArray && <span className="text-purple-600 dark:text-purple-400">"{key}"</span>}
              {!isArray && <span className="text-gray-400">: </span>}
              <JsonNode data={value} depth={depth + 1} /><span className="text-gray-400">,</span>
            </div>
          ))}
          <div style={{ paddingLeft: `${depth * 12}px` }}><span className="text-gray-400">{c}</span></div>
        </span>
      )}
    </span>
  )
}

function PayloadSection({ job }) {
  const isEmail = job.type?.toUpperCase() === 'SEND_EMAIL'
  const [mode, setMode] = useState(isEmail ? 'smart' : 'tree')
  const [copied, setCopied] = useState(false)
  const payload = job.payload || {}

  const copy = () => { navigator.clipboard.writeText(JSON.stringify(payload, null, 2)); setCopied(true); setTimeout(() => setCopied(false), 2000) }

  const modes = isEmail
    ? [{ id: 'smart', label: 'Email Preview', icon: Mail }, { id: 'tree', label: 'Tree', icon: ChevronRight }, { id: 'raw', label: 'Raw JSON', icon: Code }]
    : [{ id: 'tree', label: 'Tree', icon: ChevronRight }, { id: 'raw', label: 'Raw JSON', icon: Code }]

  return (
    <div className="card p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
          Payload
          {isEmail && <span className="ml-2 inline-flex items-center gap-1 text-xs font-normal text-primary-500"><Mail className="w-3 h-3" /> SEND_EMAIL</span>}
        </h3>
        <div className="flex items-center gap-2">
          <button onClick={copy} className={clsx('flex items-center gap-1 text-xs px-2 py-1 rounded-md transition-colors', copied ? 'text-green-500 bg-green-50 dark:bg-green-900/20' : 'text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:bg-gray-800')}>
            {copied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
            {copied ? 'Copied' : 'Copy'}
          </button>
          <div className="flex rounded-lg overflow-hidden border border-gray-200 dark:border-gray-700">
            {modes.map(({ id, label, icon: Icon }) => (
              <button key={id} onClick={() => setMode(id)} className={clsx('flex items-center gap-1 px-2.5 py-1 text-xs font-medium transition-colors border-l border-gray-200 dark:border-gray-700 first:border-l-0', mode === id ? 'bg-primary-500 text-white' : 'bg-white dark:bg-gray-900 text-gray-500 hover:bg-gray-50 dark:hover:bg-gray-800')}>
                <Icon className="w-3 h-3" /><span className="hidden sm:inline">{label}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
      {mode === 'smart' && isEmail && <EmailPreview payload={payload} />}
      {mode === 'tree' && (
        <div className="text-xs bg-gray-50 dark:bg-gray-800/80 p-4 rounded-lg overflow-auto max-h-80 font-mono leading-relaxed">
          <JsonNode data={payload} depth={0} />
        </div>
      )}
      {mode === 'raw' && (
        <pre className="text-xs bg-gray-50 dark:bg-gray-800/80 p-4 rounded-lg overflow-auto max-h-80 text-gray-700 dark:text-gray-300 font-mono whitespace-pre-wrap break-all">
          {JSON.stringify(payload, null, 2)}
        </pre>
      )}
    </div>
  )
}

export default function JobDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()

  const { data: job, isLoading } = useQuery({ queryKey: ['job', id], queryFn: () => getJob(id) })
  const retryMut = useMutation({ mutationFn: () => retryJob(id), onSuccess: () => { qc.invalidateQueries(['job', id]); toast.success('Job re-queued') } })

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  if (!job) return <p className="text-center text-gray-500 py-20">Job not found</p>

  const durationMs = job.startedAt && job.completedAt ? new Date(job.completedAt) - new Date(job.startedAt) : null

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => navigate(-1)} className="btn-secondary p-2"><ArrowLeft className="w-4 h-4" /></button>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">{job.type}</h1>
            <Badge type="status" value={job.status} />
            <Badge type="priority" value={job.priority} />
          </div>
          <p className="text-xs text-gray-400 font-mono mt-0.5 truncate">{job.jobId}</p>
        </div>
        {job.canRetry && (
          <button className="btn-primary flex-shrink-0" onClick={() => retryMut.mutate()} disabled={retryMut.isPending}>
            <RefreshCw className="w-4 h-4" />{retryMut.isPending ? 'Retrying...' : 'Retry Job'}
          </button>
        )}
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
                ['Company',  job.companyName],
                ['Retries',  `${job.retryCount} / ${job.maxRetries}`],
                ['Duration', durationMs !== null ? (durationMs < 1000 ? `${durationMs}ms` : `${(durationMs/1000).toFixed(2)}s`) : '—'],
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
                <h3 className="text-sm font-semibold text-red-700 dark:text-red-400">Error Message</h3>
              </div>
              <p className="text-xs text-red-600 dark:text-red-300 bg-red-50 dark:bg-red-900/20 p-3 rounded-lg font-mono break-all leading-relaxed">{job.errorMessage}</p>
              {job.canRetry && <p className="text-xs text-gray-500 mt-2">This job can be retried — click "Retry Job" above.</p>}
            </div>
          )}

          <PayloadSection job={job} />
        </div>

        <div className="space-y-5">
          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">Timeline</h3>
            <div className="space-y-1">
              <TimelineItem label="Created"   time={job.createdAt}   icon={Clock}        color="bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400" />
              <TimelineItem label="Started"   time={job.startedAt}   icon={RefreshCw}    color="bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400" />
              <TimelineItem label="Completed" time={job.completedAt} icon={CheckCircle}  color="bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400" isLast />
            </div>
            {durationMs !== null && (
              <div className="mt-4 pt-4 border-t border-gray-100 dark:border-gray-800">
                <p className="text-xs text-gray-500 dark:text-gray-400">Processing time</p>
                <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {durationMs < 1000 ? `${durationMs}ms` : `${(durationMs/1000).toFixed(2)}s`}
                </p>
              </div>
            )}
          </div>

          {job.idempotencyKey && (
            <div className="card p-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">Idempotency Key</h3>
              <code className="text-xs font-mono text-gray-500 dark:text-gray-400 break-all">{job.idempotencyKey}</code>
            </div>
          )}

          {job.callbackUrl && (
            <div className="card p-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">Callback URL</h3>
              <p className="text-xs font-mono text-gray-500 dark:text-gray-400 break-all">{job.callbackUrl}</p>
              <p className="text-xs text-gray-400 dark:text-gray-600 mt-2">POST fired here on job completion</p>
            </div>
          )}

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">Retry Progress</h3>
            <div className="space-y-2">
              <div className="flex justify-between text-xs">
                <span className="text-gray-500 dark:text-gray-400">Attempts</span>
                <span className="font-medium text-gray-900 dark:text-gray-100">{job.retryCount} / {job.maxRetries}</span>
              </div>
              <div className="h-1.5 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                <div className={clsx('h-full rounded-full transition-all', job.retryCount >= job.maxRetries ? 'bg-red-500' : 'bg-primary-500')}
                  style={{ width: `${Math.min((job.retryCount / job.maxRetries) * 100, 100)}%` }} />
              </div>
              <p className="text-xs text-gray-400 dark:text-gray-600">
                {job.retryCount === 0 ? 'No retries yet'
                  : job.retryCount >= job.maxRetries ? 'All retries exhausted'
                  : `${job.maxRetries - job.retryCount} retries remaining`}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}