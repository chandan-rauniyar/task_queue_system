import api from './axios'

export const getMetrics = () => api.get('/admin/metrics').then(r => r.data.data)