import clsx from 'clsx'

const STATUS_STYLES = {
  QUEUED:  'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
  RUNNING: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  SUCCESS: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  FAILED:  'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
  DEAD:    'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
}

const ENV_STYLES = {
  PRODUCTION: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  STAGING:    'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  DEV:        'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300',
}

const PURPOSE_STYLES = {
  NOREPLY: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
  SUPPORT: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  BILLING: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  ALERT:   'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
  CUSTOM:  'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
}

const PRIORITY_STYLES = {
  HIGH:   'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
  NORMAL: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
  LOW:    'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
}

export default function Badge({ type = 'status', value, className }) {
  const map = { status: STATUS_STYLES, env: ENV_STYLES, purpose: PURPOSE_STYLES, priority: PRIORITY_STYLES }
  const styles = map[type] || STATUS_STYLES
  const style = styles[value] || 'bg-gray-100 text-gray-600'

  return (
    <span className={clsx(
      'inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium',
      style, className
    )}>
      {value}
    </span>
  )
}