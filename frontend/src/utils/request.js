import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { markTokenActivity, stopTokenRefresh } from './tokenRefresh'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})


request.interceptors.response.use(
    response => {
      markTokenActivity()
      // 仅处理 HTTP 2xx 的响应
      const res = response.data
      if (res.code !== 200) {
        ElMessage.error(res.msg || '请求失败')
        // 如果业务 code 是 401（例如后端返回 200 但 code=401）
        if (res.code === 401) {
          stopTokenRefresh()
          sessionStorage.removeItem('token')
          sessionStorage.removeItem('role')
          sessionStorage.removeItem('name')
          sessionStorage.removeItem('username')
          sessionStorage.removeItem('studentId')
          sessionStorage.removeItem('userId')
          router.push('/login')
        }
        return Promise.reject(new Error(res.msg))
      }
      return res.data
    },
    error => {
      // 处理 HTTP 非 2xx 的错误（如 401、403、500 等）
      if (error.response) {
        const { status, data } = error.response
        if (status === 401) {
          // token 过期或无效
          stopTokenRefresh()
          ElMessage.error(data?.msg || '登录已过期，请重新登录')
          sessionStorage.removeItem('token')
          sessionStorage.removeItem('role')
          sessionStorage.removeItem('name')
          sessionStorage.removeItem('username')
          sessionStorage.removeItem('studentId')
          sessionStorage.removeItem('userId')
          router.push('/login')
        } else {
          ElMessage.error(data?.msg || `请求失败: ${status}`)
        }
      } else {
        // 网络断开或请求超时等
        ElMessage.error(error.message || '网络异常')
      }
      return Promise.reject(error)
    }
)

export default request
