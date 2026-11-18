<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="$router.back()" class="back-btn">
        <el-icon><ArrowLeft /></el-icon>返回列表
      </el-button>
    </div>

    <!-- 竞赛横幅 -->
    <div class="detail-banner" :style="{ background: 'linear-gradient(135deg, #1a2744 0%, #2a5298 100%)' }">
      <div class="banner-content">
        <el-tag :type="statusType" effect="dark" size="large">{{ statusText }}</el-tag>
        <h1 class="banner-title">{{ competition.name }}</h1>
        <div class="banner-meta">
          <span><el-icon><Calendar /></el-icon> {{ competition.startDate }} ~ {{ competition.endDate }}</span>
          <span><el-icon><User /></el-icon> {{ competition.participants }} 人报名</span>
          <span><el-icon><OfficeBuilding /></el-icon> {{ competition.level }}</span>
        </div>
      </div>
      <div class="banner-action" v-if="isStudent">
        <div class="banner-actions-inner">
          <template v-if="competition.status === 'enrolling'">
            <el-button v-if="!hasEnrolled" type="primary" size="large" round @click="handleEnroll">立即报名</el-button>
            <el-tag v-else type="success" size="large" effect="dark" round>已报名</el-tag>
          </template>
          <el-tag v-else-if="hasEnrolled" type="success" size="large" effect="dark" round>已报名</el-tag>
          <el-button
            v-if="showProjectUpload"
            type="warning"
            size="large"
            round
            @click="projectUploadDialogVisible = true"
          >
            <el-icon><Upload /></el-icon>上传项目
          </el-button>
        </div>
      </div>
    </div>

    <div class="detail-grid">
      <div class="detail-main">
        <el-card class="detail-card">
          <template #header><span class="card-title">竞赛详情</span></template>
          <div class="detail-section">
            <h3>竞赛描述</h3>
            <p>{{ competition.description }}</p>
          </div>
          <div class="detail-section">
            <h3>参赛要求</h3>
            <p>{{ competition.requirements }}</p>
          </div>
          <div class="detail-section">
            <h3>奖项设置</h3>
            <p>{{ competition.awards }}</p>
          </div>
        </el-card>

        <!-- 报名名单 -->
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">报名名单</span>
              <el-tag size="small">共 {{ enrollList.length }} 人</el-tag>
            </div>
          </template>
          <el-table :data="enrollList" style="width: 100%" empty-text="暂无报名">
            <el-table-column prop="teamName" label="队伍" min-width="120" />
            <el-table-column prop="studentName" label="姓名" width="100" />
            <el-table-column prop="username" label="账号" width="140" />
            <el-table-column prop="college" label="学院" />
            <el-table-column label="角色" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.role === 'captain' ? 'success' : 'info'" size="small">
                  {{ row.role === 'captain' ? '队长' : '队员' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enrollTime" label="报名时间" width="160" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="registrationStatusType(row.status)" size="small">
                  {{ registrationStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="isStudent" class="detail-card teacher-eval-card">
          <template #header>
            <span class="card-title">教师评价</span>
          </template>
          <div v-if="teacherEvals.length === 0" class="teacher-eval-empty">暂无教师评价</div>
          <div v-else class="teacher-eval-list">
            <div v-for="item in teacherEvals" :key="item.id" class="teacher-eval-item">
              <div class="teacher-eval-head">
                <span class="teacher-eval-name">{{ item.name || '项目' }}</span>
                <span v-if="formatEvalTime(item)" class="teacher-eval-time">{{ formatEvalTime(item) }}</span>
              </div>
              <div class="teacher-eval-preview-row">
                <p class="teacher-eval-preview">{{ previewEval(item.teacherEvaluation) }}</p>
                <el-button type="primary" link size="large" class="teacher-eval-detail-btn" @click="openTeacherEvalDetail(item)">详情</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <div class="detail-side">
        <el-card class="detail-card">
          <template #header><span class="card-title">基本信息</span></template>
          <div class="info-list">
            <div class="info-item"><span class="info-label">竞赛类别</span><span>{{ competition.category }}</span></div>
            <div class="info-item"><span class="info-label">竞赛级别</span><span>{{ competition.level }}</span></div>
            <div class="info-item"><span class="info-label">报名时间</span><span>{{ competition.enrollStart }} ~ {{ competition.enrollEnd }}</span></div>
            <div class="info-item"><span class="info-label">最大人数</span><span>{{ competition.maxParticipants }} 人</span></div>
            <div class="info-item"><span class="info-label">组队规模</span><span>{{ competition.teamMin }}-{{ competition.teamMax }} 人/队</span></div>
            <div class="info-item"><span class="info-label">负责老师</span><span>{{ competition.advisorDisplay || competition.advisor || '—' }}</span></div>
            <div class="info-item"><span class="info-label">联系方式</span><span>{{ competition.contact }}</span></div>
            <div class="info-item"><span class="info-label">项目上传</span><span>{{ competition.projectUploadEnabled ? '已开启（队长须报名通过且在竞赛进行中）' : '未开放' }}</span></div>
            <div v-if="isStudent && myRegistration?.exists && myRegistration?.projectSubmitLocked" class="info-item info-item--muted">
              <span class="info-label">材料提交</span>
              <span>管理员已为您的队伍评定成绩，不可再上传新的项目材料</span>
            </div>
          </div>
        </el-card>

        <el-card class="detail-card">
          <template #header><span class="card-title">竞赛进度</span></template>
          <el-timeline>
            <el-timeline-item v-for="(step, i) in timeline" :key="i" :type="step.active ? 'primary' : ''" :hollow="!step.active" :timestamp="step.date" placement="top">
              {{ step.title }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </div>
    </div>

    <StudentProjectUploadDialog
      v-model="projectUploadDialogVisible"
      :competition-id="competition.id"
      @success="onProjectUploadDialogSuccess"
    />

    <el-dialog v-model="teacherEvalDetailVisible" title="教师评价详情" width="520px" destroy-on-close @closed="onTeacherEvalDetailClosed">
      <template v-if="teacherEvalDetail">
        <div class="teacher-eval-detail-head">
          <span class="teacher-eval-detail-name">{{ teacherEvalDetail.name || '项目' }}</span>
          <span v-if="formatEvalTime(teacherEvalDetail)" class="teacher-eval-detail-time">{{ formatEvalTime(teacherEvalDetail) }}</span>
        </div>
        <div class="teacher-eval-detail-body">{{ teacherEvalDetail.teacherEvaluation }}</div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'
import StudentProjectUploadDialog from '../../components/StudentProjectUploadDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isStudent = computed(() => userStore.role === 'student')
const hasEnrolled = ref(false)

const competition = ref({
  id: route.params.id,
  name: '', category: '', level: '', status: '',
  description: '', requirements: '', awards: '',
  startDate: '', endDate: '', enrollStart: '', enrollEnd: '',
  participants: 0, maxParticipants: 0, teamMin: 1, teamMax: 3,
  advisor: '', advisorDisplay: '', contact: '', projectUploadEnabled: false,
  scorePublished: false
})

const myRegistration = ref(null)
const projectUploadDialogVisible = ref(false)
const teacherEvals = ref([])
const teacherEvalDetailVisible = ref(false)
const teacherEvalDetail = ref(null)

/** 队长且报名已通过：可下载已有材料（竞赛未结束时）；上传仅在「进行中」 */
const captainProjectMaterialsEligible = computed(
  () =>
    isStudent.value &&
    !!competition.value.projectUploadEnabled &&
    myRegistration.value?.exists &&
    myRegistration.value?.owner &&
    myRegistration.value?.status === 'approved'
)

const showProjectUpload = computed(
  () =>
    captainProjectMaterialsEligible.value &&
    competition.value.status === 'ongoing' &&
    !myRegistration.value?.projectSubmitLocked
)

const statusText = computed(() => ({ preparing: '筹备中', enrolling: '报名中', enrollend: '报名已结束', ongoing: '进行中', finished: '已结束' }[competition.value.status]))
const statusType = computed(() => ({ preparing: 'info', enrolling: 'success', enrollend: 'warning', ongoing: 'warning', finished: '' }[competition.value.status]))

const enrollList = ref([])

const timeline = ref([])

const registrationStatusText = (status) => {
  if (status === 'approved') return '已通过'
  if (status === 'rejected') return '未通过'
  if (status === 'pending') return '待审核'
  return status || '-'
}

const registrationStatusType = (status) => {
  if (status === 'approved') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

const loadMyRegistration = async () => {
  if (!isStudent.value || !competition.value?.id) {
    myRegistration.value = null
    return
  }
  try {
    myRegistration.value = await request.get('/apply/my-registration', {
      params: { competitionId: competition.value.id }
    })
  } catch {
    myRegistration.value = null
  }
}

const onProjectUploadDialogSuccess = async () => {
  await loadMyRegistration()
  await loadTeacherEvals()
}

const previewEval = (text) => {
  if (!text) return ''
  const t = String(text).replace(/\s+/g, ' ').trim()
  const max = 100
  return t.length <= max ? t : `${t.slice(0, max)}…`
}

const formatEvalTime = (row) => {
  const t = row?.teacherEvaluationAt
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

const openTeacherEvalDetail = (item) => {
  teacherEvalDetail.value = item
  teacherEvalDetailVisible.value = true
}

const onTeacherEvalDetailClosed = () => {
  teacherEvalDetail.value = null
}

const loadTeacherEvals = async () => {
  if (!isStudent.value || !competition.value?.id) {
    teacherEvals.value = []
    return
  }
  try {
    const list = await request.get('/apply/my-teacher-evaluations', {
      params: { competitionId: competition.value.id }
    })
    teacherEvals.value = Array.isArray(list) ? list : []
  } catch {
    teacherEvals.value = []
  }
}

const loadDetail = async () => {
  const res = await request.get(`/competition/${route.params.id}`)
  competition.value = { ...competition.value, ...res }
  const finished = res.status === 'finished'
  const published = Boolean(res.scorePublished)
  // 竞赛结束后进入成绩评定；管理员「展示成绩」后进入结果公布
  timeline.value = [
    { title: '发布竞赛', date: res.createdAt || '', active: true },
    { title: '报名阶段', date: `${res.enrollStart || ''} ~ ${res.enrollEnd || ''}`, active: ['enrolling', 'enrollend', 'ongoing', 'finished'].includes(res.status) },
    { title: '竞赛进行', date: `${res.startDate || ''} ~ ${res.endDate || ''}`, active: ['ongoing', 'finished'].includes(res.status) },
    { title: '成绩评定', date: '', active: finished && !published },
    { title: '结果公布', date: '', active: finished && published }
  ]
  // 加载报名名单
  if (res.id) {
    const regRes = await request.get('/apply/list', { params: { competition: res.id } })
    enrollList.value = regRes.list || []
    // 检查当前用户是否已报名（通过账号匹配）
    if (isStudent.value && userStore.userInfo) {
      const myUsername = userStore.userInfo.username
      hasEnrolled.value = !!myUsername && enrollList.value.some(r => r.username === myUsername)
    }
  }
  await loadMyRegistration()
  await loadTeacherEvals()
}

onMounted(() => { loadDetail() })

const handleEnroll = () => {
  router.push({ name: 'StudentCompetitionEnroll', params: { id: route.params.id } })
}
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }

.page-header { margin-bottom: 20px; }
.back-btn { margin-bottom: 4px; }

.detail-banner {
  border-radius: var(--radius-lg);
  padding: 36px 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
}
.detail-banner::after {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: rgba(232, 168, 56, 0.08);
  border-radius: 50%;
}
.banner-title {
  font-family: var(--font-display);
  font-size: 26px;
  color: #fff;
  margin: 12px 0 16px;
  position: relative;
}
.banner-meta {
  display: flex;
  gap: 24px;
  color: rgba(255,255,255,0.75);
  font-size: 14px;
}
.banner-meta span { display: flex; align-items: center; gap: 6px; }

.banner-actions-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.banner-upload { display: inline-flex; }

.detail-grid { display: grid; grid-template-columns: 1fr 340px; gap: 24px; }

.detail-card { background: var(--bg-card); border-color: var(--border); margin-bottom: 20px; }
.card-title { font-family: var(--font-display); font-size: 16px; color: var(--text-primary); }
.card-header { display: flex; justify-content: space-between; align-items: center; }

.detail-section { margin-bottom: 24px; }
.detail-section:last-child { margin-bottom: 0; }
.detail-section h3 {
  font-family: var(--font-display);
  font-size: 15px;
  color: var(--accent);
  margin-bottom: 8px;
}
.detail-section p { color: var(--text-secondary); font-size: 14px; line-height: 1.8; white-space: pre-line; }

.info-list { display: flex; flex-direction: column; gap: 14px; }
.info-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.info-item:last-child { border: none; padding: 0; }
.info-label { color: var(--text-muted); }
.info-item span:last-child { color: var(--text-primary); }
.info-item--muted span:last-child { color: var(--text-muted); font-size: 13px; line-height: 1.5; text-align: right; max-width: 220px; }

.teacher-eval-card .teacher-eval-empty {
  color: var(--text-muted);
  font-size: 14px;
  padding: 8px 0;
}
.teacher-eval-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.teacher-eval-item {
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-elevated, var(--bg-card));
}
.teacher-eval-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.teacher-eval-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
  min-width: 0;
  word-break: break-word;
}
.teacher-eval-time {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}
.teacher-eval-preview-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  justify-content: space-between;
}
.teacher-eval-preview {
  flex: 1;
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
  min-width: 0;
  word-break: break-word;
}
.teacher-eval-detail-btn {
  font-size: 16px;
  font-weight: 500;
  padding: 6px 10px;
  flex-shrink: 0;
}
.teacher-eval-detail-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.teacher-eval-detail-name {
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
  min-width: 0;
  word-break: break-word;
}
.teacher-eval-detail-time {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}
.teacher-eval-detail-body {
  font-size: 18px;
  line-height: 1.75;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
