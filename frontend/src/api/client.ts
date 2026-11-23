import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResult } from '@/types'

export const TOKEN_KEY = 'bagu:token'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api',
  timeout: 15000,
})

// Sa-Token：请求头携带 satoken
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.set('satoken', token)
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data as ApiResult<unknown>
    if (body && typeof body.code === 'number' && body.code !== 200) {
      if (body.code === 401) onUnauthorized()
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return res
  },
  (err) => {
    if (err.response?.status === 401) onUnauthorized()
    return Promise.reject(new Error(err.response?.data?.message ?? err.message ?? '网络异常'))
  },
)

function onUnauthorized() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem('bagu:user')
  if (!location.pathname.startsWith('/login')) {
    location.href = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
  }
}

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await http.request<ApiResult<T>>(config)
  return res.data.data
}
