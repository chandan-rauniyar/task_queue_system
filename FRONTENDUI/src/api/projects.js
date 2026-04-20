import api from './axios'

export const getProjects    = (companyId) => api.get(`/admin/companies/${companyId}/projects`).then(r => r.data.data)
export const createProject  = (data) => api.post('/admin/projects', data).then(r => r.data.data)
export const toggleProject  = (id) => api.patch(`/admin/projects/${id}/toggle`).then(r => r.data.data)