<template>
  <div class="admin-layout">
    <!-- 侧边导航 -->
    <aside class="sidebar" :class="{ 'sidebar--collapsed': isCollapsed }">
      <div class="sidebar-header">
        <div class="logo-icon">
          <el-icon :size="22"><Trophy /></el-icon>
        </div>
        <transition name="fade">
          <span v-if="!isCollapsed" class="logo-text">科创平台</span>
        </transition>
      </div>

      <el-menu
        :default-active="currentRoute"
        :collapse="isCollapsed"
        background-color="transparent"
        text-color="var(--text-secondary)"
        active-text-color="var(--accent)"
        router
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-menu-item
            v-if="!item.hidden"
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar-footer">
        <div class="sidebar-toggle" @click="isCollapsed = !isCollapsed">
          <el-icon :size="18">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
        </div>
      </div>
    </aside>

    <!-- 主体区域 -->
    <div class="main-area">
      <!-- 顶部导航栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: homePath }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentMeta.title">{{ currentMeta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="topbar-right">
          <el-tooltip content="AI 助手" placement="bottom">
            <div class="topbar-action" @click="$router.push(aiAssistantPath)">
              <el-icon :size="18"><MagicStick /></el-icon>
            </div>
          </el-tooltip>

          <el-popover
            v-model:visible="noticePopoverVisible"
            placement="bottom-end"
            :width="380"
            trigger="click"
            popper-class="notice-popover-panel"
            @show="onNoticePopoverShow"
          >
            <template #reference>
              <el-tooltip content="通知" placement="bottom">
                <div class="topbar-action">
                  <el-badge :value="unreadCount" :hidden="unreadCount < 1" :max="99" :offset="[2, -2]">
                    <el-icon :size="18"><Bell /></el-icon>
                  </el-badge>
                </div>
              </el-tooltip>
            </template>
            <div class="notice-popover-inner">
              <div class="notice-popover-head">通知</div>
              <el-scrollbar max-height="320px">
                <div v-loading="noticeLoading" class="notice-list">
                  <div v-if="!noticeLoading && notices.length === 0" class="notice-empty">暂无通知</div>
                  <div
                    v-for="n in notices"
                    :key="n.id"
                    class="notice-item"
                    :class="{ 'is-unread': !n.hasRead }"
                    @click="handleNoticeClick(n)"
                  >
                    <span class="notice-dot" :class="'notice-dot--' + (n.type || 'info')" />
                    <div class="notice-item-main">
                      <div class="notice-item-title">{{ n.title }}</div>
                      <div v-if="n.content" class="notice-item-desc">{{ n.content }}</div>
                      <div class="notice-item-time">{{ formatNoticeTime(n.createdAt) }}</div>
                    </div>
                  </div>
                </div>
              </el-scrollbar>
            </div>
          </el-popover>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-dropdown">
              <el-avatar :size="32" class="user-avatar">
                {{ userStore.userInfo?.name?.charAt(0) || 'U' }}
              </el-avatar>
              <span class="user-name">{{ userStore.userInfo?.name || '用户' }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区域 -->
      <main class="content-area">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessageBox } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapsed = ref(false)

const unreadCount = ref(0)
const notices = ref([])
const noticeLoading = ref(false)
const noticePopoverVisible = ref(false)

const fetchUnreadCount = async () => {
  try {
    const data = await request.get('/notice/unread-count')
    unreadCount.value = Number(data?.count ?? 0)
  } catch {
    unreadCount.value = 0
  }
}

const onNoticePopoverShow = async () => {
  noticeLoading.value = true
  try {
    const res = await request.get('/notice/list', { params: { page: 1, size: 30 } })
    notices.value = res.list || []
  } catch {
    notices.value = []
  } finally {
    noticeLoading.value = false
  }
}

const formatNoticeTime = (t) => {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

const handleNoticeClick = async (n) => {
  try {
    if (!n.hasRead) {
      await request.post(`/notice/${n.id}/read`)
      n.hasRead = true
      fetchUnreadCount()
    }
  } catch {
    /* 拦截器已提示 */
  }
  const url = n.linkUrl != null ? String(n.linkUrl).trim() : ''
  if (url) {
    noticePopoverVisible.value = false
    if (/^https?:\/\//i.test(url)) {
      window.open(url, '_blank')
    } else {
      router.push(url)
    }
  }
}

onMounted(() => {
  fetchUnreadCount()
})

watch(() => route.fullPath, () => {
  fetchUnreadCount()
})

const currentRoute = computed(() => route.path)
const currentMeta = computed(() => route.meta || {})

/** 与路由门户一致：管理员根路径、教师 /teacher、学生 /student */
const routePrefix = computed(() => {
  const r = userStore.role
  if (r === 'student') return '/student'
  if (r === 'teacher') return '/teacher'
  return ''
})

const homePath = computed(() => (routePrefix.value ? `${routePrefix.value}/dashboard` : '/dashboard'))

const aiAssistantPath = computed(() => (routePrefix.value ? `${routePrefix.value}/ai-assistant` : '/ai-assistant'))

const profilePath = computed(() => (routePrefix.value ? `${routePrefix.value}/profile` : '/profile'))

const MENU_DEFS = [
  { segment: 'dashboard', title: '工作台', studentTitle: '学生工作台', icon: 'Odometer', roles: ['admin', 'teacher', 'student'] },
  { segment: 'competition', title: '竞赛管理', icon: 'Trophy', roles: ['admin', 'teacher', 'student'] },
  { segment: 'registration', title: '报名管理', icon: 'DocumentChecked', roles: ['admin', 'teacher'] },
  { segment: 'announcement', title: '公告管理', icon: 'Bell', roles: ['admin', 'teacher', 'student'] },
  { segment: 'message-board', title: '留言板', icon: 'ChatDotSquare', roles: ['admin', 'teacher', 'student'] },
  { segment: 'score', title: '成绩管理', icon: 'DataAnalysis', roles: ['admin', 'teacher'] },
  { segment: 'innovation-project', title: '项目管理', icon: 'FolderOpened', roles: ['admin', 'teacher'] },
  { segment: 'score-query', title: '成绩查询', icon: 'DataAnalysis', roles: ['student'] },
  { segment: 'innovation-project', title: '项目管理', icon: 'FolderOpened', roles: ['student'] },
  { segment: 'user', title: '用户管理', icon: 'User', roles: ['admin'] },
  { segment: 'ai-assistant', title: 'AI 助手', icon: 'MagicStick', roles: ['admin', 'teacher', 'student'] }
]

const menuItems = computed(() => {
  const role = userStore.role
  const pref = routePrefix.value
  const prefixPath = (segment) => (pref ? `${pref}/${segment}` : `/${segment}`)

  return MENU_DEFS.filter((def) => def.roles.includes(role)).map((def) => ({
    path: prefixPath(def.segment),
    title: role === 'student' && def.studentTitle ? def.studentTitle : def.title,
    icon: def.icon,
    hidden: false
  }))
})

const handleCommand = async (command) => {
  if (command === 'profile') {
    router.push(profilePath.value)
  } else if (command === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  background: var(--glass-bg);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border-right: var(--glass-border);
  display: flex;
  flex-direction: column;
  transition: width 0.4s var(--ease-out);
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
  box-shadow: 4px 0 24px rgba(15, 23, 42, 0.04);
}

.sidebar--collapsed {
  width: 64px;
}

.sidebar-header {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 12px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: var(--accent);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  flex-shrink: 0;
}

.logo-text {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
}

.sidebar :deep(.el-menu) {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sidebar :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  border-radius: var(--radius-sm);
  margin-bottom: 2px;
  transition: all 0.2s var(--ease-out);
}

.sidebar :deep(.el-menu-item:hover) {
  background: var(--bg-hover) !important;
}

.sidebar :deep(.el-menu-item.is-active) {
  background: rgba(43, 95, 158, 0.1) !important;
  color: var(--primary-light) !important;
  font-weight: 600;
  box-shadow: inset 3px 0 0 var(--primary-light);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}

.sidebar-toggle {
  width: 100%;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  transition: all 0.2s;
}

.sidebar-toggle:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* 主体区域 */
.main-area {
  flex: 1;
  min-width: 0;
  margin-left: 260px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left 0.4s var(--ease-out);
}

.sidebar--collapsed ~ .main-area {
  margin-left: 64px;
}

/* 顶栏 */
.topbar {
  height: 72px;
  background: var(--glass-bg);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border-bottom: var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  position: sticky;
  top: 0;
  z-index: 50;
  box-shadow: 0 1px 16px rgba(15, 23, 42, 0.04);
}

.topbar-left {
  display: flex;
  align-items: center;
}

.topbar :deep(.el-breadcrumb__inner) {
  color: var(--text-secondary) !important;
}

.topbar :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary) !important;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topbar-action {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.topbar-action:hover {
  background: var(--bg-hover);
  color: var(--accent);
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.2s;
}

.user-dropdown:hover {
  background: var(--bg-hover);
}

.user-avatar {
  background: var(--accent) !important;
  color: var(--primary) !important;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: var(--text-primary);
}

/* 内容区域 */
.content-area {
  flex: 1;
  min-width: 0;
  padding: 32px;
  background:
    radial-gradient(ellipse at 80% 0%, rgba(59,130,246,0.06) 0%, transparent 60%),
    radial-gradient(ellipse at 20% 100%, rgba(139,92,246,0.06) 0%, transparent 60%),
    var(--bg-main);
  position: relative;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>

<style>
/* el-popover 挂载到 body，不用 scoped */
.notice-popover-panel.el-popover.el-popper {
  padding: 0;
}
.notice-popover-inner {
  min-height: 80px;
}
.notice-popover-head {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.notice-list {
  padding: 8px 0;
}
.notice-empty {
  padding: 24px 16px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.notice-item {
  display: flex;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.notice-item:hover {
  background: var(--el-fill-color-light);
}
.notice-item.is-unread .notice-item-title {
  font-weight: 600;
}
.notice-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}
.notice-dot--info {
  background: var(--el-color-info);
}
.notice-dot--success {
  background: var(--el-color-success);
}
.notice-dot--warning {
  background: var(--el-color-warning);
}
.notice-item-main {
  min-width: 0;
  flex: 1;
}
.notice-item-title {
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 1.4;
}
.notice-item-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notice-item-time {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
