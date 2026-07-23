import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import TopBar from './TopBar'
import clsx from 'clsx'

export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(true)

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar
        collapsed={!sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />
      <TopBar onMenuClick={() => setSidebarOpen(s => !s)} />

      {/* Main content — offset for sidebar on desktop */}
      <main className={clsx(
        "pt-16 min-h-screen transition-all duration-200 ease-in-out",
        sidebarOpen ? "lg:ml-64" : "lg:ml-16"
      )}>
        <div className="p-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}