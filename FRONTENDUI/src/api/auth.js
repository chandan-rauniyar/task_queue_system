// api/auth.js
import api from './axios'
export const login = (email, password) =>
  api.post('/auth/login', { email, password }).then(r => r.data.data)