import { Menu, Sun, Moon } from 'lucide-react'
import { useTheme } from '../../context/ThemeContext'
import { useLocation, useParams } from 'react-router-dom'

const PAGE_TITLES = {
  '/dashboard':  'Dashboard',
  '/companies':  'Companies',
  '/projects':   'Projects',
  '/keys':       'API Keys',
  '/jobs':       'Jobs',
  '/dlq':        'Dead Letter Queue',
  '/smtp':       'SMTP Settings',
}

function getTitle(pathname) {
  // Detail pages
  if (/^\/companies\/[^/]+$/.test(pathname)) return 'Company Detail'
  if (/^\/projects\/[^/]+$/.test(pathname))  return 'Project Detail'
  if (/^\/jobs\/[^/]+$/.test(pathname))       return 'Job Detail'
  // Exact matches
  const match = Object.entries(PAGE_TITLES).find(([path]) => pathname === path || pathname.startsWith(path + '/') && PAGE_TITLES[path])
  return match?.[1] || 'Admin'
}

export default function TopBar({ onMenuClick }) {
  const { theme, toggle } = useTheme()
  const location = useLocation()
  const title = getTitle(location.pathname)

  return (
    <header className="
      fixed top-0 right-0 left-0 lg:left-64 h-16 z-10
      bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm
      border-b border-gray-200 dark:border-gray-800
      flex items-center justify-between px-4 gap-4
    ">
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuClick}
          className="lg:hidden p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-500"
        >
          <Menu className="w-5 h-5" />
        </button>
        <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">{title}</h2>
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={toggle}
          className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-500 dark:text-gray-400"
          title="Toggle theme"
        >
          {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>
      </div>
    </header>
  )
}