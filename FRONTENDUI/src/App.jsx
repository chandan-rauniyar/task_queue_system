import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'

import { ThemeProvider } from './context/ThemeContext'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import AppLayout from './components/layout/AppLayout'

import Login            from './pages/Login'
import Dashboard        from './pages/Dashboard'
import Companies        from './pages/Companies'
import CompanyDetail    from './pages/Companydetail'
import Projects         from './pages/Projects'
import ProjectDetail    from './pages/Projectdetail'
import ApiKeys          from './pages/ApiKeys'
import Jobs             from './pages/Jobs'
import JobDetail        from './pages/JobDetail'
import DeadLetterQueue  from './pages/DeadLetterQueue'
import SmtpSettings     from './pages/SmtpSettings'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <BrowserRouter>
            <Toaster position="top-right" toastOptions={{ duration: 3000, style: { fontSize: '13px', borderRadius: '8px' } }} />
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/" element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard"        element={<Dashboard />} />
                <Route path="companies"        element={<Companies />} />
                <Route path="companies/:id"    element={<CompanyDetail />} />
                <Route path="projects"         element={<Projects />} />
                <Route path="projects/:id"     element={<ProjectDetail />} />
                <Route path="keys"             element={<ApiKeys />} />
                <Route path="jobs"             element={<Jobs />} />
                <Route path="jobs/:id"         element={<JobDetail />} />
                <Route path="dlq"              element={<DeadLetterQueue />} />
                <Route path="smtp"             element={<SmtpSettings />} />
              </Route>
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}