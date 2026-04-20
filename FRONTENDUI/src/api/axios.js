import axios from 'axios'
import toast from 'react-hot-toast'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('tq_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Handle errors globally
api.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.error || error.message

    if (status === 401) {
      localStorage.removeItem('tq_token')
      localStorage.removeItem('tq_user')
      window.location.href = '/login'
      return Promise.reject(error)
    }

    if (status !== 404) {
      toast.error(message || 'Something went wrong')
    }

    return Promise.reject(error)
  }
)

export default api