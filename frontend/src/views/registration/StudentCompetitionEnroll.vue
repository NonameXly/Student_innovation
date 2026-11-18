<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">竞赛报名</h1>
        <p class="page-subtitle">填写报名信息并提交审核</p>
      </div>
      <el-button @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
    </div>

    <el-card class="form-card" v-loading="pageLoading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" label-position="top" size="large">
        <div class="form-grid">
          <div class="form-grid__main">
            <el-form-item label="竞赛名称">
              <el-input :model-value="competition.name" disabled />
            </el-form-item>

            <div class="form-row">
              <el-form-item label="竞赛类别" class="flex-1">
                <el-input :model-value="competition.category" disabled />
              </el-form-item>
              <el-form-item label="竞赛级别" class="flex-1">
                <el-input :model-value="competition.level" disabled />
              </el-form-item>
            </div>

            <div class="form-row">
              <el-form-item label="报名时间" class="flex-1">
                <el-input :model-value="`${competition.enrollStart || '-'} ~ ${competition.enrollEnd || '-'}`" disabled />
              </el-form-item>
              <el-form-item label="竞赛时间" class="flex-1">
                <el-input :model-value="`${competition.startDate || '-'} ~ ${competition.endDate || '-'}`" disabled />
              </el-form-item>
            </div>

            <el-form-item label="团队名称" prop="teamName">
              <el-input v-model="form.teamName" placeholder="请输入团队名称（可选）" maxlength="60" show-word-limit />
            </el-form-item>

            <el-form-item label="队长信息（本人）">
              <div class="form-row member-row">
                <el-input :model-value="captainInfo.name || '-'" class="flex-1" disabled />
                <el-input :model-value="captainInfo.username || '-'" class="flex-1" disabled />
              </div>
            </el-form-item>

            <el-form-item v-if="memberSlots.length > 0" :label="`队友信息（选填，还可填写 ${memberSlots.length} 人）`">
              <div class="member-list">
                <div class="form-row member-row" v-for="(_, index) in memberSlots" :key="index">
                  <el-input
                    v-model="form.members[index].name"
                    class="flex-1"
                    :placeholder="`成员${index + 1}姓名（选填）`"
                    maxlength="20"
                    clearable
                  />
                  <el-input
                    v-model="form.members[index].username"
                    class="flex-1"
                    :placeholder="`成员${index + 1}账号（选填）`"
                    maxlength="30"
                    clearable
                  />
                </div>
              </div>
            </el-form-item>

            <el-form-item label="备注">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="提交后将进入待审核状态，审核通过后计入报名人数。"
              />
            </el-form-item>
          </div>

          <div class="form-grid__side">
            <div class="side-panel">
              <h3 class="side-panel__title">当前状态</h3>
              <el-tag :type="statusType" size="large" effect="dark">{{ statusText }}</el-tag>
              <p class="side-note" v-if="alreadyEnrolled">你已提交过该竞赛报名，不能重复提交。</p>
              <p class="side-note" v-else-if="competition.status !== 'enrolling'">当前不在报名阶段，暂不可提交。</p>
              <p class="side-note" v-else>当前处于报名阶段，可正常提交报名。</p>
            </div>
          </div>
        </div>

        <div class="form-actions">
          <el-button @click="router.back()">取消</el-button>
          <el-button type="primary" :loading="loading" :disabled="submitDisabled" @click="handleSubmit">
            提交报名
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const pageLoading = ref(false)
const alreadyEnrolled = ref(false)

const competition = ref({
  id: route.params.id,
  name: '',
  category: '',
  level: '',
  status: '',
  enrollStart: '',
  enrollEnd: '',
  startDate: '',
  endDate: ''
})

const form = reactive({
  teamName: '',
  members: []
})

const rules = {
  teamName: [{ max: 60, message: '团队名称最多 60 个字符', trigger: 'blur' }]
}

const statusTextMap = {
  preparing: '筹备中',
  enrolling: '报名中',
  enrollend: '报名已结束',
  ongoing: '进行中',
  finished: '已结束'
}
const statusTypeMap = {
  preparing: 'info',
  enrolling: 'success',
  enrollend: 'warning',
  ongoing: 'warning',
  finished: ''
}

const statusText = computed(() => statusTextMap[competition.value.status] || competition.value.status || '-')
const statusType = computed(() => statusTypeMap[competition.value.status] || 'info')
const submitDisabled = computed(() => competition.value.status !== 'enrolling' || alreadyEnrolled.value)
const captainInfo = computed(() => ({
  name: userStore.userInfo?.name || userStore.userInfo?.username || '',
  username: userStore.userInfo?.username || ''
}))
const memberSlots = computed(() => {
  const max = Number(competition.value.teamMax)
  const fallback = 1
  const total = Number.isFinite(max) && max > 0 ? max : fallback
  const count = Math.max(total - 1, 0)
  return Array.from({ length: Math.min(count, 10) })
})

const syncMemberFields = () => {
  const targetCount = memberSlots.value.length
  const currentMembers = Array.isArray(form.members) ? form.members : []
  form.members = Array.from({ length: targetCount }, (_, index) => {
    const existing = currentMembers[index] || {}
    return {
      name: existing.name || '',
      username: existing.username || ''
    }
  })
}

watch(memberSlots, syncMemberFields, { immediate: true })

const loadCompetition = async () => {
  const res = await request.get(`/competition/${route.params.id}`)
  competition.value = { ...competition.value, ...res }
  syncMemberFields()
}

const checkEnrolled = async () => {
  const res = await request.get('/apply/list', { params: { competition: route.params.id } })
  const list = res.list || []
  const myUsername = userStore.userInfo?.username
  alreadyEnrolled.value = !!myUsername && list.some((item) => item.username === myUsername && item.status !== 'rejected')
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || submitDisabled.value) return
  loading.value = true
  try {
    const members = form.members
      .map((item) => ({
        name: (item?.name || '').trim(),
        username: (item?.username || '').trim()
      }))
      .filter((item) => item.name || item.username)
    await request.post('/apply/enroll', {
      competitionId: Number(route.params.id),
      teamName: form.teamName || '',
      captainName: captainInfo.value.name || '',
      captainUsername: captainInfo.value.username || '',
      members
    })
    ElMessage.success('报名申请已提交，请等待审核')
    router.push({ name: 'StudentCompetitionDetail', params: { id: route.params.id } })
  } catch (error) {
    if (!error?.handled) {
      ElMessage.error('报名失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  pageLoading.value = true
  try {
    await loadCompetition()
    await checkEnrolled()
  } catch (error) {
    ElMessage.error(error?.message || '报名页面加载失败，请返回重试')
  } finally {
    pageLoading.value = false
  }
})
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }

.form-card { background: var(--bg-card); border-color: var(--border); }
.form-grid { display: grid; grid-template-columns: 1fr 300px; gap: 32px; }
.form-row { display: flex; gap: 16px; }
.flex-1 { flex: 1; }
.member-list { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.member-row { width: 100%; margin-bottom: 0; }

.side-panel {
  background: var(--bg-dark);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
}
.side-panel__title {
  font-family: var(--font-display);
  font-size: 16px;
  color: var(--text-primary);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.side-note {
  margin-top: 14px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border);
}

@media (max-width: 1100px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
