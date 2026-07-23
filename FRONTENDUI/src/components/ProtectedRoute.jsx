import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// No setup step anymore — company is created on register
export default function ProtectedRoute({ children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return children
}