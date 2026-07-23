/**
 * Client API — calls /client/** endpoints which are JWT-protected
 * and automatically scoped to the logged-in user's company.
 * Used when role === 'CLIENT'.
 */
import api from './axios'

// My company
export const getMyCompany = () =>
  api.get('/client/my-company').then(r => r.data.data)

// Metrics — only this company's data
export const getClientMetrics = () =>
  api.get('/client/metrics').then(r => r.data.data)

// Projects
export const getClientProjects = () =>
  api.get('/client/projects').then(r => r.data.data)

export const createClientProject = (data) =>
  api.post('/client/projects', data).then(r => r.data.data)

// API Keys
export const getClientKeys = (projectId) =>
  api.get(`/client/projects/${projectId}/keys`).then(r => r.data.data)

export const createClientKey = (data) =>
  api.post('/client/keys', data).then(r => r.data.data)

export const revokeClientKey = (id) =>
  api.delete(`/client/keys/${id}`).then(r => r.data.data)

// Jobs
export const getClientJobs = (params) =>
  api.get('/client/jobs', { params }).then(r => r.data.data)

export const getClientJob = (id) =>
  api.get(`/client/jobs/${id}`).then(r => r.data.data)

// SMTP
export const getClientSmtp = () =>
  api.get('/client/smtp').then(r => r.data.data)

export const createClientSmtp = (data) =>
  api.post('/client/smtp', data).then(r => r.data.data)

export const testClientSmtp = (id) =>
  api.post(`/client/smtp/${id}/test`).then(r => r.data.data)

// DLQ
export const getClientDlq = (params) =>
  api.get('/client/dlq', { params }).then(r => r.data.data)

export const replayClientDlq = (id) =>
  api.post(`/client/dlq/${id}/replay`).then(r => r.data.data)