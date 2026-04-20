import api from './axios'

export const getCompanies  = () => api.get('/admin/companies').then(r => r.data.data)
export const createCompany = (data) => api.post('/admin/companies', data).then(r => r.data.data)
export const toggleCompany = (id) => api.patch(`/admin/companies/${id}/toggle`).then(r => r.data.data)