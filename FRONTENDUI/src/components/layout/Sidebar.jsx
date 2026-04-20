import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Building2, FolderOpen, Key,
  Briefcase, AlertTriangle, Mail, LogOut, Zap
} from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useQuery } from '@tanstack/react-query'
import { getMetrics } from '../../api/metrics'
import clsx from 'clsx'

const NAV = [
  { to: '/dashboard',  icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/companies',  icon: Building2,       label: 'Companies' },
  { to: '/projects',   icon: FolderOpen,      label: 'Projects' },
  { to: '/keys',       icon: Key,             label: 'API Keys' },
  { to: '/jobs',       icon: Briefcase,       label: 'Jobs' },
  { to: '/dlq',        icon: AlertTriangle,   label: 'Dead Letter Queue', badge: true },
  { to: '/smtp',       icon: Mail,            label: 'SMTP Settings' },
]

export default function Sidebar({ collapsed, onClose }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const { data: metrics } = useQuery({
    queryKey: ['metrics'],
    queryFn: getMetrics,
    refetchInterval: 30000,
  })

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <>
      {/* Mobile overlay */}
      {!collapsed && (
        <div
          className="fixed inset-0 z-20 bg-black/40 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside className={clsx(
        'fixed left-0 top-0 h-full z-30 flex flex-col',
        'w-64 bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-800',
        'transition-transform duration-200 ease-in-out',
        collapsed ? '-translate-x-full lg:translate-x-0 lg:w-16' : 'translate-x-0'
      )}>
        {/* Logo */}
        <div className="flex items-center gap-3 px-4 h-16 border-b border-gray-200 dark:border-gray-800">
          <div className="w-8 h-8 rounded-lg bg-primary-500 flex items-center justify-center flex-shrink-0">
            <Zap className="w-4 h-4 text-white" />
          </div>
          {!collapsed && (
            <span className="font-semibold text-gray-900 dark:text-gray-100 truncate">
              Task Queue
            </span>
          )}
        </div>

        {/* Nav */}
        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {NAV.map(({ to, icon: Icon, label, badge }) => (
            <NavLink
              key={to}
              to={to}
              onClick={onClose}
              className={({ isActive }) =>
                clsx('sidebar-link', isActive && 'active')
              }
            >
              <Icon className="w-4 h-4 flex-shrink-0" />
              {!collapsed && (
                <>
                  <span className="flex-1">{label}</span>
                  {badge && metrics?.pendingDlq > 0 && (
                    <span className="ml-auto bg-red-500 text-white text-xs font-medium rounded-full w-5 h-5 flex items-center justify-center">
                      {metrics.pendingDlq > 9 ? '9+' : metrics.pendingDlq}
                    </span>
                  )}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        {/* User */}
        <div className="p-3 border-t border-gray-200 dark:border-gray-800">
          <div className={clsx(
            'flex items-center gap-3 px-3 py-2 rounded-lg',
            'bg-gray-50 dark:bg-gray-800'
          )}>
            <div className="w-7 h-7 rounded-full bg-primary-500 flex items-center justify-center flex-shrink-0">
              <span className="text-white text-xs font-semibold">
                {user?.email?.[0]?.toUpperCase() || 'A'}
              </span>
            </div>
            {!collapsed && (
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-gray-900 dark:text-gray-100 truncate">
                  {user?.email}
                </p>
                <p className="text-xs text-gray-500 dark:text-gray-400">{user?.role}</p>
              </div>
            )}
            <button
              onClick={handleLogout}
              className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-700 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              title="Logout"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </aside>
    </>
  )
}