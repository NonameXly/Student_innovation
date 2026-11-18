<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑竞赛' : '发起竞赛' }}</h1>
        <p class="page-subtitle">{{ isEdit ? '修改竞赛信息' : '创建新的科创竞赛' }}</p>
      </div>
      <el-button @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
    </div>

    <el-card class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" label-position="top" size="large">
        <div class="form-grid">
          <div class="form-grid__main">
            <el-form-item label="竞赛名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入竞赛全称" maxlength="100" show-word-limit />
            </el-form-item>

            <el-form-item label="竞赛描述" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请详细描述竞赛内容、要求和目标..." maxlength="1000" show-word-limit />
            </el-form-item>

            <div class="form-row">
              <el-form-item label="竞赛类别" prop="category" class="flex-1">
                <el-select v-model="form.category" placeholder="请选择类别" style="width: 100%">
                  <el-option label="数学" value="数学" />
                  <el-option label="计算机" value="计算机" />
                  <el-option label="电子" value="电子" />
                  <el-option label="综合" value="综合" />
                  <el-option label="创新创业" value="创新创业" />
                </el-select>
              </el-form-item>
              <el-form-item label="竞赛级别" prop="level" class="flex-1">
                <el-select v-model="form.level" placeholder="请选择级别" style="width: 100%">
                  <el-option label="国家级" value="national" />
                  <el-option label="省级" value="provincial" />
                  <el-option label="市级" value="city" />
                  <el-option label="校级" value="school" />
                  <el-option label="院级" value="college" />
                </el-select>
              </el-form-item>
            </div>

            <div class="form-row">
              <el-form-item label="报名开始日期" prop="enrollStart" class="flex-1">
                <el-date-picker v-model="form.enrollStart" type="date" placeholder="选择日期" style="width: 100%" value-format="YYYY-MM-DD" />
              </el-form-item>
              <el-form-item label="报名截止日期" prop="enrollEnd" class="flex-1">
                <el-date-picker v-model="form.enrollEnd" type="date" placeholder="选择日期" style="width: 100%" value-format="YYYY-MM-DD" />
              </el-form-item>
            </div>

            <div class="form-row">
              <el-form-item label="竞赛开始日期" prop="startDate" class="flex-1">
                <el-date-picker v-model="form.startDate" type="date" placeholder="选择日期" style="width: 100%" value-format="YYYY-MM-DD" />
              </el-form-item>
              <el-form-item label="竞赛结束日期" prop="endDate" class="flex-1">
                <el-date-picker v-model="form.endDate" type="date" placeholder="选择日期" style="width: 100%" value-format="YYYY-MM-DD" />
              </el-form-item>
            </div>
            <p class="date-range-hint">
              日期按整天计算：开始日为当日 0:00 起，结束日为当日 24:00 止。报名开始与截止、竞赛开始与结束各自可为同一天；竞赛开始日必须晚于报名截止日（例如截止选 24 日则开赛最早为 25 日）。
            </p>

            <el-form-item label="参赛要求">
              <el-input v-model="form.requirements" type="textarea" :rows="3" placeholder="请描述参赛资格要求..." />
            </el-form-item>

            <el-form-item label="奖项设置">
              <el-input v-model="form.awards" type="textarea" :rows="3" placeholder="请描述奖项设置（如一等奖、二等奖等）..." />
            </el-form-item>
          </div>

          <div class="form-grid__side">
            <div class="side-panel">
              <h3 class="side-panel__title">发布设置</h3>
              <el-form-item label="最大报名人数">
                <el-input-number v-model="form.maxParticipants" :min="0" :max="9999" style="width: 100%" />
              </el-form-item>
              <el-form-item label="组队人数">
                <div class="team-range">
                  <el-input-number v-model="form.teamMin" :min="1" :max="10" size="small" />
                  <span class="range-sep">~</span>
                  <el-input-number v-model="form.teamMax" :min="1" :max="10" size="small" />
                </div>
              </el-form-item>
              <el-form-item label="负责老师" prop="advisor">
                <el-input
                  :model-value="advisorSummary"
                  readonly
                  class="advisor-picker-input"
                  placeholder="点击选择教师或管理员（可多选，支持搜索）"
                  @click="openAdvisorPicker"
                >
                  <template #suffix>
                    <el-icon class="advisor-picker-icon" @click.stop="openAdvisorPicker"><Search /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="联系方式">
                <el-input v-model="form.contact" placeholder="联系电话或邮箱" />
              </el-form-item>
              <el-form-item label="学生上传项目">
                <div class="project-upload-toggle">
                  <el-switch
                    v-model="form.projectUploadEnabled"
                    inline-prompt
                    active-text="开"
                    inactive-text="关"
                  />
                  <span class="project-upload-hint">开启后，报名审核通过的队长仅在竞赛进行中可上传项目材料</span>
                </div>
              </el-form-item>
            </div>
          </div>
        </div>

        <div class="form-actions">
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" @click="handleSaveDraft">保存草稿</el-button>
          <el-button type="primary" @click="handleSubmit">
            <el-icon><Check /></el-icon>{{ isEdit ? '更新发布' : '立即发布' }}
          </el-button>
        </div>
      </el-form>
    </el-card>

    <el-dialog
      v-model="advisorPickerVisible"
      title="选择负责老师"
      width="720px"
      destroy-on-close
      class="advisor-picker-dialog"
      @open="onAdvisorDialogOpen"
    >
      <div class="advisor-dialog-toolbar">
        <el-input
          v-model="staffKeyword"
          clearable
          placeholder="姓名 / 账号 / 学院"
          style="flex: 1; max-width: 320px"
          @keyup.enter="loadStaffPage(1)"
        />
        <el-button type="primary" @click="loadStaffPage(1)">搜索</el-button>
      </div>
      <el-table
        ref="staffTableRef"
        v-loading="staffLoading"
        :data="staffRows"
        row-key="id"
        height="360"
        @selection-change="onStaffSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column label="角色" width="88">
          <template #default="{ row }">{{ row.role === 'admin' ? '管理员' : '教师' }}</template>
        </el-table-column>
        <el-table-column prop="college" label="学院" min-width="120" show-overflow-tooltip />
      </el-table>
      <div class="advisor-dialog-pagination">
        <el-pagination
          v-model:current-page="staffPage"
          :page-size="staffPageSize"
          :total="staffTotal"
          layout="total, prev, pager, next"
          background
          @current-change="loadStaffPage"
        />
      </div>
      <template #footer>
        <el-button @click="cancelAdvisorPicker">取消</el-button>
        <el-button type="primary" @click="confirmAdvisorPicker">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { usePortalPrefix } from '../../utils/portal'

const route = useRoute()
const router = useRouter()
const { withPrefix } = usePortalPrefix()
const formRef = ref()
const staffTableRef = ref()

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  name: '', description: '', category: '', level: '',
  enrollStart: '', enrollEnd: '', startDate: '', endDate: '',
  requirements: '', awards: '', maxParticipants: 100,
  teamMin: 1, teamMax: 3, advisor: '', contact: '',
  projectUploadEnabled: false
})

const advisorPickerVisible = ref(false)
const advisorSelections = ref([])
const advisorPickerBackup = ref([])
const advisorLegacyPlain = ref(false)
const staffKeyword = ref('')
const staffLoading = ref(false)
const staffRows = ref([])
const staffPage = ref(1)
const staffPageSize = 15
const staffTotal = ref(0)

const advisorSummary = computed(() => {
  if (advisorSelections.value.length > 0) {
    return advisorSelections.value.map((x) => x.name || x.username || '').filter(Boolean).join('、')
  }
  if (advisorLegacyPlain.value && String(form.advisor || '').trim()) {
    return String(form.advisor).trim()
  }
  return ''
})

const validateAdvisorField = (_rule, _value, callback) => {
  if (advisorSelections.value.length > 0) {
    callback()
    return
  }
  if (advisorLegacyPlain.value && String(form.advisor || '').trim()) {
    callback()
    return
  }
  callback(new Error('请选择至少一位负责老师'))
}

const rules = {
  name: [{ required: true, message: '请输入竞赛名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入竞赛描述', trigger: 'blur' }],
  category: [{ required: true, message: '请选择竞赛类别', trigger: 'change' }],
  level: [{ required: true, message: '请选择竞赛级别', trigger: 'change' }],
  enrollStart: [{ required: true, message: '请选择报名开始日期', trigger: 'change' }],
  enrollEnd: [{ required: true, message: '请选择报名截止日期', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择竞赛开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择竞赛结束日期', trigger: 'change' }],
  advisor: [{ validator: validateAdvisorField, trigger: 'change' }]
}

const syncAdvisorIdsToForm = () => {
  if (advisorSelections.value.length > 0) {
    form.advisor = advisorSelections.value.map((x) => x.id).join(',')
  } else if (!advisorLegacyPlain.value) {
    form.advisor = ''
  }
}

const loadStaffPage = async (page = 1) => {
  staffLoading.value = true
  staffPage.value = page
  try {
    const res = await request.get('/user/staff-for-advisor', {
      params: {
        keyword: staffKeyword.value?.trim() || undefined,
        page: staffPage.value,
        size: staffPageSize
      }
    })
    staffRows.value = res.list || []
    staffTotal.value = res.total || 0
    await nextTick()
    const table = staffTableRef.value
    if (table) {
      table.clearSelection()
      staffRows.value.forEach((row) => {
        if (advisorSelections.value.some((x) => x.id === row.id)) {
          table.toggleRowSelection(row, true)
        }
      })
    }
  } catch {
    staffRows.value = []
    staffTotal.value = 0
  } finally {
    staffLoading.value = false
  }
}

const onStaffSelectionChange = (selection) => {
  const pageIds = new Set(staffRows.value.map((r) => r.id))
  const kept = advisorSelections.value.filter((x) => !pageIds.has(x.id))
  const merged = selection.map((r) => ({
    id: r.id,
    name: r.name,
    username: r.username,
    role: r.role
  }))
  advisorSelections.value = [...kept, ...merged]
}

const onAdvisorDialogOpen = () => {
  loadStaffPage(1)
}

const openAdvisorPicker = () => {
  advisorPickerBackup.value = advisorSelections.value.map((x) => ({ ...x }))
  staffKeyword.value = ''
  staffPage.value = 1
  advisorPickerVisible.value = true
}

const cancelAdvisorPicker = () => {
  advisorSelections.value = advisorPickerBackup.value.map((x) => ({ ...x }))
  syncAdvisorIdsToForm()
  advisorPickerVisible.value = false
}

const confirmAdvisorPicker = () => {
  advisorLegacyPlain.value = false
  syncAdvisorIdsToForm()
  advisorPickerVisible.value = false
  formRef.value?.validateField('advisor')
}

const buildSubmitPayload = () => {
  const payload = { ...form }
  if (advisorSelections.value.length > 0) {
    payload.advisor = advisorSelections.value.map((x) => x.id).join(',')
  }
  return payload
}

const loadDetail = async () => {
  if (!isEdit.value) return
  const res = await request.get(`/competition/${route.params.id}`)
  Object.assign(form, res)
  form.projectUploadEnabled = !!res.projectUploadEnabled

  advisorSelections.value = []
  advisorLegacyPlain.value = false
  const raw = res.advisor != null ? String(res.advisor).trim() : ''
  if (raw && /^\d+(\s*,\s*\d+)*$/.test(raw)) {
    try {
      const list = await request.get('/user/staff-by-ids', {
        params: { ids: raw.replace(/\s/g, '') }
      })
      const arr = Array.isArray(list) ? list : []
      advisorSelections.value = arr.map((u) => ({
        id: u.id,
        name: u.name,
        username: u.username,
        role: u.role
      }))
      syncAdvisorIdsToForm()
    } catch {
      form.advisor = raw
    }
  } else if (raw) {
    advisorLegacyPlain.value = true
    form.advisor = raw
  } else {
    form.advisor = ''
  }
}

onMounted(() => {
  loadDetail()
})

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload = buildSubmitPayload()
  if (isEdit.value) {
    await request.put(`/competition/${route.params.id}`, payload)
  } else {
    await request.post('/competition', payload)
  }
  ElMessage.success(isEdit.value ? '更新成功' : '发布成功')
  router.push(withPrefix('/competition'))
}

const handleSaveDraft = async () => {
  await request.post('/competition/draft', buildSubmitPayload())
  ElMessage.info('草稿已保存')
}
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }

.page-header {
  display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px;
}
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }

.form-card { background: var(--bg-card); border-color: var(--border); }

.form-grid { display: grid; grid-template-columns: 1fr 300px; gap: 32px; }
.form-row { display: flex; gap: 16px; }
.flex-1 { flex: 1; }

.date-range-hint {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.55;
  margin: -8px 0 16px;
}

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
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}

.team-range { display: flex; align-items: center; gap: 8px; }
.range-sep { color: var(--text-muted); }

.project-upload-toggle { display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.project-upload-hint { font-size: 13px; color: var(--text-muted); line-height: 1.5; flex: 1; min-width: 180px; }

.form-actions {
  display: flex; justify-content: flex-end; gap: 12px;
  margin-top: 32px; padding-top: 24px; border-top: 1px solid var(--border);
}

.advisor-picker-input { cursor: pointer; }
.advisor-picker-input :deep(.el-input__inner) { cursor: pointer; }
.advisor-picker-icon { cursor: pointer; color: var(--el-color-primary); }

.advisor-dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.advisor-dialog-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
