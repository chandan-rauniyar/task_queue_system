import { createContext, useContext, useState } from 'react'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const token = localStorage.getItem('tq_token')
    const userData = localStorage.getItem('tq_user')
    if (token && userData) {
      try { return JSON.parse(userData) } catch { return null }
    }
    return null
  })

  const login = (token, userData) => {
    localStorage.setItem('tq_token', token)
    localStorage.setItem('tq_user', JSON.stringify(userData))
    setUser(userData)
  }

  const logout = () => {
    localStorage.removeItem('tq_token')
    localStorage.removeItem('tq_user')
    setUser(null)
  }

  const isAdmin = user?.role === 'ADMIN'

  return (
    <AuthContext.Provider value={{ user, login, logout, isAdmin }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)