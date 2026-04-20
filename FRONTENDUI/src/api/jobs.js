import api from './axios'

export const getJobs   = (params) => api.get('/admin/jobs', { params }).then(r => r.data.data)
export const getJob    = (id) => api.get(`/admin/jobs/${id}`).then(r => r.data.data)
export const retryJob  = (id) => api.post(`/admin/jobs/${id}/retry`).then(r => r.data.data)