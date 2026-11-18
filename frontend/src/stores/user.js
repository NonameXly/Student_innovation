import { defineStore } from 'pinia'
import { ref } from 'vue'
import { stopTokenRefresh } from '../utils/tokenRefresh'

export const useUserStore = defineStore('user', () => {
  const token = ref(sessionStorage.getItem('token') || '')
  const role = ref(sessionStorage.getItem('role') || '')
  const userInfo = ref(token.value ? {
    name: sessionStorage.getItem('name') || '',
    username: sessionStorage.getItem('username') || '',
    role: sessionStorage.getItem('role') || '',
    studentId: sessionStorage.getItem('studentId') || '',
    userId: sessionStorage.getItem('userId') || ''
  } : null)

  /** 滑动续期：仅更新 token，不改变用户信息 */
  function updateToken(newToken) {
    token.value = newToken
    sessionStorage.setItem('token', newToken)
  }

  function setUser(info) {
    userInfo.value = info
    token.value = info.token
    role.value = info.role
    sessionStorage.setItem('token', info.token)
    sessionStorage.setItem('role', info.role)
    sessionStorage.setItem('name', info.name || '')
    sessionStorage.setItem('username', info.username || '')
    sessionStorage.setItem('studentId', info.studentId || '')
    const uid = info.userId != null && info.userId !== '' ? String(info.userId) : ''
    sessionStorage.setItem('userId', uid)
    if (userInfo.value && uid) userInfo.value.userId = uid
  }

  function logout() {
    stopTokenRefresh()
    userInfo.value = null
    token.value = ''
    role.value = ''
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('role')
    sessionStorage.removeItem('name')
    sessionStorage.removeItem('username')
    sessionStorage.removeItem('studentId')
    sessionStorage.removeItem('userId')
  }

  const isAdmin = () => role.value === 'admin'

  return { userInfo, token, role, setUser, updateToken, logout, isAdmin }
})
