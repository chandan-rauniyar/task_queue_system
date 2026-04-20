import clsx from 'clsx'

export function MetricCard({ title, value, icon: Icon, color = 'purple', trend }) {
  const colors = {
    purple: 'bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400',
    green:  'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400',
    amber:  'bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400',
    red:    'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400',
    blue:   'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400',
    gray:   'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
  }

  return (
    <div className="card p-5">
      <div className="flex items-center justify-between mb-3">
        <p className="text-sm text-gray-500 dark:text-gray-400">{title}</p>
        {Icon && (
          <div className={clsx('w-8 h-8 rounded-lg flex items-center justify-center', colors[color])}>
            <Icon className="w-4 h-4" />
          </div>
        )}
      </div>
      <p className="text-2xl font-semibold text-gray-900 dark:text-gray-100">
        {value?.toLocaleString() ?? '—'}
      </p>
      {trend !== undefined && (
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{trend}</p>
      )}
    </div>
  )
}