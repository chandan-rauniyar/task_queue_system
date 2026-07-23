import api from './axios'

export const login = (email, password) =>
  api.post('/auth/login', { email, password }).then(r => r.data.data)

export const register = (fullName, email, password, companyName) =>
  api.post('/auth/register', { fullName, email, password, companyName }).then(r => r.data.data)