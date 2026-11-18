import axios from 'axios'

/** 距离过期不足此时长则尝试续期（页签可见即续，不要求最后几分钟内有鼠标操作） */
const REFRESH_BEFORE_MS = 5 * 60 * 1000
const CHECK_INTERVAL_MS = 45 * 1000

let timerId = null
let lastActivityAt = Date.now()

export function markTokenActivity() {
  lastActivityAt = Date.now()
}

export function stopTokenRefresh() {
  if (timerId != null) {
    clearInterval(timerId)
    timerId = null
  }
}

function getJwtExpMs(token) {
  if (!token || !token.includes('.')) return null
  try {
    const part = token.split('.')[1]
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const pad = base64.length % 4
    const padded = base64 + (pad ? '='.repeat(4 - pad) : '')
    const payload = JSON.parse(atob(padded))
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

function bindActivityListeners() {
  const onAct = () => {
    lastActivityAt = Date.now()
  }
  ;['mousedown', 'keydown', 'scroll', 'touchstart', 'click'].forEach((evt) => {
    window.addEventListener(evt, onAct, { passive: true })
  })
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      lastActivityAt = Date.now()
      void tryRefresh()
    }
  })
}

async function applyNewToken(newToken) {
  sessionStorage.setItem('token', newToken)
  const { useUserStore } = await import('../stores/user.js')
  useUserStore().updateToken(newToken)
}

async function tryRefresh() {
  const token = sessionStorage.getItem('token')
  if (!token) return

  const exp = getJwtExpMs(token)
  if (exp == null) return

  const now = Date.now()
  const msToExp = exp - now
  if (msToExp > REFRESH_BEFORE_MS) return

  if (document.visibilityState !== 'visible') return

  try {
    const res = await axios.post(
      '/api/auth/refresh',
      {},
      {
        headers: { Authorization: `Bearer ${token}` },
        timeout: 15000,
        validateStatus: (s) => s === 200
      }
    )
    const body = res.data
    if (body?.code === 200 && body.data?.token) {
      await applyNewToken(body.data.token)
      lastActivityAt = Date.now()
    }
  } catch {
    /* 静默失败，后续请求若已过期会走统一 401 处理 */
  }
}

export function setupTokenRefresh() {
  stopTokenRefresh()
  bindActivityListeners()
  markTokenActivity()
  timerId = window.setInterval(() => {
    tryRefresh()
  }, CHECK_INTERVAL_MS)
  void tryRefresh()
}
