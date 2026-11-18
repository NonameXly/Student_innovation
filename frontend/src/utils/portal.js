import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 根据当前路径判断门户前缀（管理员 ''、教师 /teacher、学生 /student），用于拼接站内跳转。
 */
export function usePortalPrefix() {
  const route = useRoute()

  const prefix = computed(() => {
    const p = route.path
    if (p.startsWith('/student')) return '/student'
    if (p.startsWith('/teacher')) return '/teacher'
    return ''
  })

  function withPrefix(subPath) {
    const sub = subPath.startsWith('/') ? subPath : `/${subPath}`
    const base = prefix.value
    if (!base) return sub
    return `${base}${sub}`
  }

  return { prefix, withPrefix }
}
