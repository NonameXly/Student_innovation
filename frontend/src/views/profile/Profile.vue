<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">个人中心</h1>
      <el-button
        v-if="user.role === 'student'"
        type="primary"
        plain
        @click="handleExport"
      >
        <el-icon><Download /></el-icon>导出成绩与项目
      </el-button>
    </div>

    <div class="profile-grid">
      <el-card class="profile-card">
        <div class="profile-top">
          <el-avatar :size="80" class="profile-avatar">{{ user.name.charAt(0) }}</el-avatar>
          <h2 class="profile-name">{{ user.name }}</h2>
          <el-tag :type="roleMap[user.role]?.type" size="small" effect="plain">{{ roleMap[user.role]?.text }}</el-tag>
          <p class="profile-email">{{ user.email }}</p>
        </div>
        <el-divider />
        <div class="info-list">
          <div
            class="info-item"
            v-for="info in infoItems"
            :key="info.label"
            v-show="!(user.role === 'admin' && info.label === '学号')"
          >
            <span class="info-label"><el-icon><component :is="info.icon" /></el-icon>{{ info.label }}</span>
            <span class="info-value">{{ info.value }}</span>
          </div>
        </div>
        <el-button class="edit-profile-btn" @click="editFormVisible = true"><el-icon><Edit /></el-icon>编辑资料</el-button>
      </el-card>

      <div class="profile-right">
        <el-card class="stat-card">
          <template #header><span class="card-title">参赛统计</span></template>
          <div class="stat-grid">
            <div v-for="(s, i) in stats" :key="i" class="stat-block" :style="{ animationDelay: `${i * 0.08}s` }">
              <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
              <div class="stat-label">{{ s.label }}</div>
            </div>
          </div>
        </el-card>

        <el-card class="history-card">
          <template #header><span class="card-title">参赛记录</span></template>
          <div v-if="history.length === 0" class="timeline-empty">暂无参赛记录，报名竞赛后将显示在这里</div>
          <div v-else class="timeline">
            <div v-for="(h, i) in history" :key="i" class="timeline-item" :style="{ animationDelay: `${i * 0.06}s` }">
              <div class="timeline-dot" :class="h.status"></div>
              <div class="timeline-content">
                <div class="timeline-title">{{ h.competition }}</div>
                <div class="timeline-meta">
                  <span>{{ h.date }}</span>
                  <el-tag v-if="h.award" :type="h.awardType" size="small" effect="dark">{{ h.award }}</el-tag>
                  <el-tag v-else size="small" type="info" effect="plain">{{ h.statusText }}</el-tag>
                </div>
              </div>
              <div class="timeline-score" v-if="h.score">{{ h.score }}分</div>
            </div>
          </div>
        </el-card>

        <el-card class="pwd-card">
          <template #header><span class="card-title">修改密码</span></template>
          <el-form :model="pwdForm" label-position="top" style="max-width: 400px">
            <el-form-item label="当前密码" required>
              <el-input v-model="pwdForm.oldPwd" type="password" show-password placeholder="输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码" required>
              <el-input v-model="pwdForm.newPwd" type="password" show-password placeholder="输入新密码" />
            </el-form-item>
            <el-form-item label="确认密码" required>
              <el-input v-model="pwdForm.confirmPwd" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleChangePwd">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="editFormVisible" title="编辑个人资料" width="480px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editFormRules" label-position="top">
        <el-form-item label="姓名">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item v-if="user.role !== 'admin'" label="学号" prop="studentId">
          <el-input v-model="editForm.studentId" placeholder="请输入学号" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" />
        </el-form-item>
        <el-form-item label="学院">
          <el-input v-model="editForm.college" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, markRaw, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { School, Phone, Calendar, Stamp, Download } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'

const roleMap = {
  admin: { type: 'danger', text: '管理员' },
  teacher: { type: 'warning', text: '教师' },
  student: { type: '', text: '学生' }
}

const user = reactive({
  name: '', role: '', email: '', phone: '', college: '', studentId: '', joinDate: ''
})

const infoItems = ref([
  { label: '学院', value: '', icon: markRaw(School) },
  { label: '手机', value: '', icon: markRaw(Phone) },
  { label: '入学日期', value: '', icon: markRaw(Calendar) },
  { label: '学号', value: '', icon: markRaw(Stamp) }
])

const stats = ref([
  { label: '参赛次数', value: 0, color: 'var(--accent)' },
  { label: '获奖次数', value: 0, color: 'var(--success)' },
  { label: '最高分', value: 0, color: 'var(--danger)' },
  { label: '平均分', value: 0, color: 'var(--info)' }
])

const history = ref([])

const loadProfile = async () => {
  const res = await request.get('/user/profile')
  Object.assign(user, res)
  infoItems.value[0].value = res.college || ''
  infoItems.value[1].value = res.phone || ''
  infoItems.value[2].value = res.joinDate || ''
  infoItems.value[3].value = res.studentId || ''
  Object.assign(editForm, {
    name: res.name,
    studentId: res.studentId || '',
    email: res.email,
    phone: res.phone,
    college: res.college
  })
  if (res.stats) {
    stats.value[0].value = res.stats.participations || 0
    stats.value[1].value = res.stats.awards || 0
    stats.value[2].value = res.stats.maxScore || 0
    stats.value[3].value = res.stats.avgScore || 0
  }
  if (res.history) {
    history.value = res.history
  }
}

onMounted(() => { loadProfile() })

const editFormVisible = ref(false)
const editFormRef = ref()
const editFormRules = computed(() => {
  if (user.role === 'admin') return {}
  return {
    studentId: [
      { required: true, message: '请输入学号', trigger: 'blur' },
      { max: 30, message: '学号最多 30 个字符', trigger: 'blur' }
    ]
  }
})
const userStore = useUserStore()
const editForm = reactive({ name: '', studentId: '', email: '', phone: '', college: '' })
const pwdForm = reactive({ oldPwd: '', newPwd: '', confirmPwd: '' })

const handleSaveProfile = async () => {
  try {
    await editFormRef.value?.validate()
  } catch {
    return
  }
  const payload = { ...editForm }
  if (user.role === 'admin') {
    delete payload.studentId
  }
  await request.put('/user/profile', payload)
  ElMessage.success('个人资料已更新')
  editFormVisible.value = false
  if (user.role !== 'admin') {
    const sid = (editForm.studentId || '').trim()
    if (userStore.userInfo) {
      userStore.userInfo.studentId = sid
      sessionStorage.setItem('studentId', sid)
    }
  }
  loadProfile()
}

const handleChangePwd = async () => {
  if (!pwdForm.oldPwd || !pwdForm.newPwd || !pwdForm.confirmPwd) {
    ElMessage.warning('请填写完整')
    return
  }
  if (pwdForm.newPwd !== pwdForm.confirmPwd) {
    ElMessage.error('两次密码不一致')
    return
  }
  await request.put('/user/password', { oldPassword: pwdForm.oldPwd, newPassword: pwdForm.newPwd })
  ElMessage.success('密码修改成功')
  Object.assign(pwdForm, { oldPwd: '', newPwd: '', confirmPwd: '' })
}

const handleExport = () => {
  const token = sessionStorage.getItem('token')
  if (!token) {
    ElMessage.warning('登录已失效，请重新登录')
    return
  }
  const url = `/api/user/profile/export?token=${encodeURIComponent(token)}`
  window.open(url, '_blank')
  ElMessage.success('正在导出，请查看浏览器下载')
}
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header {
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); }

.profile-grid { display: grid; grid-template-columns: 300px 1fr; gap: 20px; align-items: start; }

.profile-card {
  background: var(--bg-card); border-color: var(--border); text-align: center;
  position: sticky; top: 20px;
}
.profile-top { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 8px 0; }
.profile-avatar {
  background: linear-gradient(135deg, var(--accent), #f59e0b);
  font-size: 28px; font-weight: 700; color: #fff;
}
.profile-name { font-family: var(--font-display); font-size: 22px; color: var(--text-primary); margin: 0; }
.profile-email { font-size: 13px; color: var(--text-muted); margin: 0; }

.info-list { text-align: left; }
.info-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0; font-size: 13px;
}
.info-label { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); }
.info-value { color: var(--text-primary); font-weight: 500; }
.edit-profile-btn { width: 100%; margin-top: 16px; }

.profile-right { display: flex; flex-direction: column; gap: 20px; }
.card-title { font-family: var(--font-display); font-size: 16px; color: var(--text-primary); }
.stat-card, .history-card, .pwd-card { background: var(--bg-card); border-color: var(--border); }

.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-block {
  text-align: center; padding: 12px;
  animation: statPop 0.4s var(--ease-out) both;
}
@keyframes statPop { from { opacity: 0; transform: scale(0.9); } }
.stat-value { font-family: var(--font-display); font-size: 28px; font-weight: 700; }
.stat-label { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.timeline { display: flex; flex-direction: column; gap: 0; }
.timeline-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0; border-bottom: 1px solid var(--border);
  animation: timelineIn 0.4s var(--ease-out) both;
}
.timeline-item:last-child { border-bottom: none; }
@keyframes timelineIn { from { opacity: 0; transform: translateX(-8px); } }
.timeline-dot {
  width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0;
  background: var(--text-muted);
}
.timeline-dot.awarded { background: var(--accent); box-shadow: 0 0 8px rgba(232, 168, 56, 0.4); }
.timeline-dot.participated { background: var(--success); }
.timeline-dot.pending { background: var(--info); }
.timeline-content { flex: 1; }
.timeline-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 4px; }
.timeline-empty { font-size: 13px; color: var(--text-muted); padding: 12px 0; }
.timeline-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-muted); }
.timeline-score { font-family: var(--font-display); font-size: 18px; font-weight: 700; color: var(--accent); }

.pwd-card :deep(.el-card__body) { padding-top: 8px; }
</style>
