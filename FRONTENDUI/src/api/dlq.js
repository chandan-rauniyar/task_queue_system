import api from './axios'

export const getDlq       = (params) => api.get('/admin/dlq', { params }).then(r => r.data.data)
export const replaySingle = (id) => api.post(`/admin/dlq/${id}/replay`).then(r => r.data.data)
export const replayAll    = () => api.post('/admin/dlq/replay-all').then(r => r.data.data)