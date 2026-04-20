import api from './axios'

export const getApiKeys  = (projectId) => api.get(`/admin/projects/${projectId}/keys`).then(r => r.data.data)
export const createApiKey = (data) => api.post('/admin/keys', data).then(r => r.data.data)
export const revokeApiKey = (id) => api.delete(`/admin/keys/${id}`).then(r => r.data.data)