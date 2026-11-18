import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录 - 学生科创管理平台' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册 - 学生科创管理平台' }
  },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '工作台', icon: 'Odometer', roles: ['admin'] }
      },
      {
        path: 'competition',
        name: 'Competition',
        component: () => import('../views/competition/CompetitionList.vue'),
        meta: { title: '竞赛管理', icon: 'Trophy', roles: ['admin'] }
      },
      {
        path: 'competition/create',
        name: 'CompetitionCreate',
        component: () => import('../views/competition/CompetitionForm.vue'),
        meta: { title: '发起竞赛', hidden: true, roles: ['admin'] }
      },
      {
        path: 'competition/edit/:id',
        name: 'CompetitionEdit',
        component: () => import('../views/competition/CompetitionForm.vue'),
        meta: { title: '编辑竞赛', hidden: true, roles: ['admin'] }
      },
      {
        path: 'competition/:id',
        name: 'CompetitionDetail',
        component: () => import('../views/competition/CompetitionDetail.vue'),
        meta: { title: '竞赛详情', hidden: true, roles: ['admin'] }
      },
      {
        path: 'registration',
        name: 'Registration',
        component: () => import('../views/registration/RegistrationList.vue'),
        meta: { title: '报名管理', icon: 'DocumentChecked', roles: ['admin'] }
      },
      {
        path: 'registration/:id',
        name: 'RegistrationTeamDetail',
        component: () => import('../views/registration/RegistrationTeamDetail.vue'),
        meta: { title: '队伍详情', hidden: true, roles: ['admin'] }
      },
      {
        path: 'announcement',
        name: 'Announcement',
        component: () => import('../views/announcement/AnnouncementList.vue'),
        meta: { title: '公告管理', icon: 'Bell', roles: ['admin'] }
      },
      {
        path: 'message-board',
        name: 'MessageBoard',
        component: () => import('../views/message/MessageBoard.vue'),
        meta: { title: '留言板', icon: 'ChatDotSquare', roles: ['admin'] }
      },
      {
        path: 'score',
        name: 'Score',
        component: () => import('../views/score/ScoreList.vue'),
        meta: { title: '成绩管理', icon: 'DataAnalysis', roles: ['admin'] }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('../views/user/UserList.vue'),
        meta: { title: '用户管理', icon: 'User', roles: ['admin'] }
      },
      {
        path: 'innovation-project',
        name: 'InnovationProject',
        component: () => import('../views/project/InnovationProjectList.vue'),
        meta: { title: '项目管理', icon: 'FolderOpened', roles: ['admin'] }
      },
      {
        path: 'ai-assistant',
        name: 'AIAssistant',
        component: () => import('../views/ai/AIAssistant.vue'),
        meta: { title: 'AI 助手', icon: 'MagicStick', roles: ['admin'] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/profile/Profile.vue'),
        meta: { title: '个人中心', hidden: true, roles: ['admin'] }
      }
    ]
  },
  {
    path: '/teacher',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/teacher/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'TeacherDashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '工作台', icon: 'Odometer', roles: ['teacher'] }
      },
      {
        path: 'competition',
        name: 'TeacherCompetition',
        component: () => import('../views/competition/CompetitionList.vue'),
        meta: { title: '竞赛列表', icon: 'Trophy', roles: ['teacher'] }
      },
      {
        path: 'competition/:id',
        name: 'TeacherCompetitionDetail',
        component: () => import('../views/competition/CompetitionDetail.vue'),
        meta: { title: '竞赛详情', hidden: true, roles: ['teacher'] }
      },
      {
        path: 'registration',
        name: 'TeacherRegistration',
        component: () => import('../views/registration/RegistrationList.vue'),
        meta: { title: '报名管理', icon: 'DocumentChecked', roles: ['teacher'] }
      },
      {
        path: 'registration/:id',
        name: 'TeacherRegistrationTeamDetail',
        component: () => import('../views/registration/RegistrationTeamDetail.vue'),
        meta: { title: '队伍详情', hidden: true, roles: ['teacher'] }
      },
      {
        path: 'announcement',
        name: 'TeacherAnnouncement',
        component: () => import('../views/announcement/AnnouncementList.vue'),
        meta: { title: '公告管理', icon: 'Bell', roles: ['teacher'] }
      },
      {
        path: 'message-board',
        name: 'TeacherMessageBoard',
        component: () => import('../views/message/MessageBoard.vue'),
        meta: { title: '留言板', icon: 'ChatDotSquare', roles: ['teacher'] }
      },
      {
        path: 'score',
        name: 'TeacherScore',
        component: () => import('../views/score/ScoreList.vue'),
        meta: { title: '成绩管理', icon: 'DataAnalysis', roles: ['teacher'] }
      },
      {
        path: 'innovation-project',
        name: 'TeacherInnovationProject',
        component: () => import('../views/project/InnovationProjectList.vue'),
        meta: { title: '项目管理', icon: 'FolderOpened', roles: ['teacher'] }
      },
      {
        path: 'ai-assistant',
        name: 'TeacherAIAssistant',
        component: () => import('../views/ai/AIAssistant.vue'),
        meta: { title: 'AI 助手', icon: 'MagicStick', roles: ['teacher'] }
      },
      {
        path: 'profile',
        name: 'TeacherProfile',
        component: () => import('../views/profile/Profile.vue'),
        meta: { title: '个人中心', hidden: true, roles: ['teacher'] }
      }
    ]
  },
  {
    path: '/student',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/student/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '学生工作台', icon: 'Odometer', roles: ['student'] }
      },
      {
        path: 'competition',
        name: 'StudentCompetition',
        component: () => import('../views/competition/CompetitionList.vue'),
        meta: { title: '竞赛管理', icon: 'Trophy', roles: ['student'] }
      },
      {
        path: 'competition/:id',
        name: 'StudentCompetitionDetail',
        component: () => import('../views/competition/CompetitionDetail.vue'),
        meta: { title: '竞赛详情', hidden: true, roles: ['student'] }
      },
      {
        path: 'competition/:id/enroll',
        name: 'StudentCompetitionEnroll',
        component: () => import('../views/registration/StudentCompetitionEnroll.vue'),
        meta: { title: '竞赛报名', hidden: true, roles: ['student'] }
      },
      {
        path: 'announcement',
        name: 'StudentAnnouncement',
        component: () => import('../views/announcement/AnnouncementList.vue'),
        meta: { title: '公告管理', icon: 'Bell', roles: ['student'] }
      },
      {
        path: 'message-board',
        name: 'StudentMessageBoard',
        component: () => import('../views/message/MessageBoard.vue'),
        meta: { title: '留言板', icon: 'ChatDotSquare', roles: ['student'] }
      },
      {
        path: 'score-query',
        name: 'StudentScoreQuery',
        component: () => import('../views/score/StudentScoreQuery.vue'),
        meta: { title: '成绩查询', icon: 'DataAnalysis', roles: ['student'] }
      },
      {
        path: 'innovation-project',
        name: 'StudentInnovationProject',
        component: () => import('../views/project/InnovationProjectList.vue'),
        meta: { title: '项目管理', icon: 'FolderOpened', roles: ['student'] }
      },
      {
        path: 'ai-assistant',
        name: 'StudentAIAssistant',
        component: () => import('../views/ai/AIAssistant.vue'),
        meta: { title: 'AI 助手', icon: 'MagicStick', roles: ['student'] }
      },
      {
        path: 'profile',
        name: 'StudentProfile',
        component: () => import('../views/profile/Profile.vue'),
        meta: { title: '个人中心', hidden: true, roles: ['student'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '学生科创管理平台'

  const token = sessionStorage.getItem('token')
  const role = sessionStorage.getItem('role') || ''
  const isPublic = to.path === '/login' || to.path === '/register'

  if (!isPublic && !token) return next('/login')
  if (isPublic) return next()

  const roles = to.meta.roles
  if (!roles || roles.length === 0) return next()

  if (roles.includes(role)) return next()

  /** 将路径归一到「无门户前缀」的内层路径，便于在管理员 / 教师 / 学生门户间跳转 */
  function stripPortal(path) {
    let inner = path
    if (inner.startsWith('/student')) inner = inner.slice('/student'.length) || '/dashboard'
    else if (inner.startsWith('/teacher')) inner = inner.slice('/teacher'.length) || '/dashboard'
    if (!inner.startsWith('/')) inner = `/${inner}`
    if (inner === '/') inner = '/dashboard'
    return inner
  }

  if (role === 'student') {
    const inner = stripPortal(to.path)
    return next(`/student${inner}`)
  }

  if (role === 'teacher') {
    let inner = stripPortal(to.path)
    if (inner === '/user' || inner.startsWith('/user/')) return next('/teacher/dashboard')
    return next(`/teacher${inner}`)
  }

  if (role === 'admin') {
    const inner = stripPortal(to.path)
    return next(inner)
  }

  return next('/login')
})

export default router
