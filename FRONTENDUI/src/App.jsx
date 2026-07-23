import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'

import { ThemeProvider }  from './context/ThemeContext'
import { AuthProvider }   from './context/AuthContext'
import ProtectedRoute     from './components/ProtectedRoute'
import AppLayout          from './components/layout/AppLayout'

// Public pages
import Login     from './pages/Login'
import Register  from './pages/Register'

// Shared
import Dashboard from './pages/Dashboard'

// Admin pages
import Companies       from './pages/Companies'
import CompanyDetail   from './pages/CompanyDetail'
import Projects        from './pages/Projects'
import ProjectDetail   from './pages/ProjectDetail'
import ApiKeys         from './pages/ApiKeys'
import Jobs            from './pages/Jobs'
import JobDetail       from './pages/JobDetail'
import DeadLetterQueue from './pages/DeadLetterQueue'
import SmtpSettings    from './pages/SmtpSettings'

// Client pages — own company only via /client/** API
import MyProjects  from './pages/MyProjects'
import MyKeys      from './pages/MyKeys'
import MyJobs      from './pages/MyJobs'
import MyJobDetail from './pages/MyJobDetail'
import MySmtp      from './pages/MySmtp'
import MyDlq       from './pages/MyDlq'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <BrowserRouter>
            <Toaster
              position="top-right"
              toastOptions={{ duration: 3000, style: { fontSize: '13px', borderRadius: '8px' } }}
            />
            <Routes>
              {/* Public — no auth needed */}
              <Route path="/login"    element={<Login />} />
              <Route path="/register" element={<Register />} />

              {/* Protected app */}
              <Route path="/" element={
                <ProtectedRoute>
                  <AppLayout />
                </ProtectedRoute>
              }>
                <Route index element={<Navigate to="/dashboard" replace />} />

                {/* Shared — both ADMIN and CLIENT see dashboard */}
                <Route path="dashboard" element={<Dashboard />} />

                {/* ADMIN only */}
                <Route path="companies"     element={<Companies />} />
                <Route path="companies/:id" element={<CompanyDetail />} />
                <Route path="projects"      element={<Projects />} />
                <Route path="projects/:id"  element={<ProjectDetail />} />
                <Route path="keys"          element={<ApiKeys />} />
                <Route path="jobs"          element={<Jobs />} />
                <Route path="jobs/:id"      element={<JobDetail />} />
                <Route path="dlq"           element={<DeadLetterQueue />} />
                <Route path="smtp"          element={<SmtpSettings />} />

                {/* CLIENT only — /client/** API, scoped to their company */}
                <Route path="my-projects"  element={<MyProjects />} />
                <Route path="my-keys"      element={<MyKeys />} />
                <Route path="my-jobs"      element={<MyJobs />} />
                <Route path="my-jobs/:id"  element={<MyJobDetail />} />
                <Route path="my-smtp"      element={<MySmtp />} />
                <Route path="my-dlq"       element={<MyDlq />} />
              </Route>

              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}