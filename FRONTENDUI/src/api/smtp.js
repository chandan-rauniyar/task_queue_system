import api from './axios'

export const getSmtpConfigs = (companyId) => api.get(`/admin/companies/${companyId}/smtp`).then(r => r.data.data)
export const createSmtp     = (data) => api.post('/admin/smtp', data).then(r => r.data.data)
export const testSmtp       = (id) => api.post(`/admin/smtp/${id}/test`).then(r => r.data.data)
export const toggleSmtp     = (id) => api.patch(`/admin/smtp/${id}/toggle`).then(r => r.data.data)
export const deleteSmtp     = (id) => api.delete(`/admin/smtp/${id}`).then(r => r.data.data)