<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目管理</h1>
        <p class="page-subtitle">{{ pageSubtitle }}</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新建项目
      </el-button>
    </div>

    <el-card class="filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="搜索项目名称 / 负责人 / 学号..." clearable class="filter-input" />
        <el-select v-if="!isStudent" v-model="filters.competition" placeholder="关联竞赛" clearable filterable style="width: 260px">
          <el-option v-for="c in competitionOptions" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="项目状态" clearable style="width: 160px">
          <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-responsive">
        <el-table :data="rows" v-loading="loading" table-layout="auto" style="width: 100%" empty-text="暂无项目">
          <el-table-column prop="name" label="项目名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="competitionName" label="关联竞赛" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.competitionName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="leaderName" label="负责人" width="88" />
          <el-table-column prop="leaderStudentId" label="学号" width="110" />
          <el-table-column prop="advisorTeacherName" label="负责老师" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.advisorTeacherName || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="88" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="dark">{{ statusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="附件" width="72" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.hasAttachment" type="success" size="small">有</el-tag>
              <span v-else class="muted">无</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!isStudent" label="竞赛成绩" width="116" align="center">
            <template #default="{ row }">
              <template v-if="row.scoreTotal != null && row.scoreTotal !== ''">
                <div class="score-cell">
                  <span>{{ row.scoreTotal }}</span>
                  <el-tag v-if="Number(row.scoreShow) !== 1" size="small" type="warning" effect="plain">未公示</el-tag>
                  <el-tag v-else size="small" type="success" effect="plain">已公示</el-tag>
                </div>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" width="200">
            <template #header>
              <div class="time-col-header">
                <span>创建时间</span>
                <el-button link type="primary" class="sort-time-btn" @click="toggleTimeSort">
                  <el-icon><SortDown v-if="timeOrder === 'desc'" /><SortUp v-else /></el-icon>
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="{ row }">
              <div class="op-panel">
                <div class="op-grid">
                  <el-button plain type="primary" size="small" class="op-btn" @click="openDetail(row)">
                    <el-icon class="op-btn__icon"><Document /></el-icon>
                    <span>详情</span>
                  </el-button>
                  <el-button v-if="!isStudent" plain type="primary" size="small" class="op-btn" @click="openEval(row)">
                    <el-icon class="op-btn__icon"><ChatDotRound /></el-icon>
                    <span>评价</span>
                  </el-button>
                  <div class="op-grid__upload">
                    <el-upload
                      :show-file-list="false"
                      accept=".pdf,.zip,.rar,.doc,.docx,.ppt,.pptx,.7z"
                      :http-request="(o) => handleAttach(o, row)"
                    >
                      <el-button plain type="primary" size="small" class="op-btn">
                        <el-icon class="op-btn__icon"><Upload /></el-icon>
                        <span>上传</span>
                      </el-button>
                    </el-upload>
                  </div>
                  <template v-if="row.hasAttachment">
                    <el-button plain type="primary" size="small" class="op-btn" @click="downloadAttach(row)">
                      <el-icon class="op-btn__icon"><Download /></el-icon>
                      <span>下载</span>
                    </el-button>
                  </template>
                  <div v-else class="op-grid__placeholder">
                    <span class="op-placeholder-dash">—</span>
                  </div>
                  <el-button v-if="!isStudent" plain type="danger" size="small" class="op-btn op-btn--full" @click="handleDelete(row)">
                    <el-icon class="op-btn__icon"><Delete /></el-icon>
                    <span>删除</span>
                  </el-button>
                </div>
                <div v-if="!isStudent && showScoreButton(row)" class="op-score">
                  <el-button plain type="warning" size="small" class="op-btn op-btn--full op-btn--score" @click="openScore(row)">
                    <el-icon class="op-btn__icon"><Trophy /></el-icon>
                    <span>打分</span>
                  </el-button>
                </div>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadList"
          @size-change="loadList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建项目" width="560px" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" maxlength="200" show-word-limit placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目说明">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="可选" />
        </el-form-item>
        <el-form-item v-if="isStudent" label="负责老师" prop="advisorTeacherId">
          <el-select
            v-model="form.advisorTeacherId"
            placeholder="请选择负责老师"
            filterable
            style="width: 100%"
            :loading="teacherOptionsLoading"
          >
            <el-option
              v-for="t in teacherOptions"
              :key="t.id"
              :label="teacherOptionLabel(t)"
              :value="t.id"
            />
          </el-select>
          <p v-if="isStudent" class="form-hint">选择后，该教师或管理员可在「项目管理」中查看并评价本项目</p>
        </el-form-item>
        <el-form-item v-if="!isStudent" label="关联竞赛">
          <el-select v-model="form.competitionId" placeholder="不关联则留空" clearable filterable style="width: 100%">
            <el-option v-for="c in competitionOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <div v-if="!isStudent" class="form-row">
          <el-form-item label="负责人姓名" class="flex-1">
            <el-input v-model="form.leaderName" maxlength="50" placeholder="可选" />
          </el-form-item>
          <el-form-item label="负责人学号" class="flex-1">
            <el-input v-model="form.leaderStudentId" maxlength="30" placeholder="可选" />
          </el-form-item>
        </div>
        <el-form-item v-else label="负责人">
          <el-input :model-value="leaderDisplay" disabled />
        </el-form-item>
        <el-form-item v-if="!isStudent" label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="项目详情" width="560px" destroy-on-close @closed="onDetailClosed">
      <div class="detail-view">
        <div class="detail-field">
          <div class="detail-label">项目名称</div>
          <div class="detail-value">{{ detailView.name || '—' }}</div>
        </div>
        <div class="detail-field">
          <div class="detail-label">负责老师</div>
          <div class="detail-value">{{ detailView.advisorTeacherName || '—' }}</div>
        </div>
        <div class="detail-field">
          <div class="detail-label">项目描述</div>
          <div class="detail-value detail-desc">{{ detailView.description ? detailView.description : '暂无' }}</div>
        </div>
        <div v-if="isStudent && detailView.teacherEvaluation" class="detail-field">
          <div class="detail-label">教师评价</div>
          <div class="detail-value detail-desc">{{ detailView.teacherEvaluation }}</div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scoreDialogVisible" title="录入竞赛成绩" width="440px" destroy-on-close @closed="onScoreDialogClosed">
      <p v-if="scoreRow" class="score-dialog-tip">
        项目「{{ scoreRow.name }}」· {{ scoreRow.competitionName || '关联竞赛' }}：成绩写入成绩库，默认不对学生公示；需在竞赛「展示成绩」或成绩管理中公示后学生才可查看。
      </p>
      <div class="score-input-wrap">
        <span class="score-input-label">总分</span>
        <el-input-number
          v-model="scoreInput"
          :min="0"
          :max="999.9"
          :precision="1"
          :step="0.5"
          controls-position="right"
          style="width: 100%"
          placeholder="0～999.9"
        />
      </div>
      <template #footer>
        <el-button @click="scoreDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="scoreSaving" @click="saveScore">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evalDialogVisible" title="教师评价" width="560px" destroy-on-close @closed="onEvalDialogClosed">
      <p v-if="evalRow" class="eval-dialog-tip">
        <span class="eval-word">项目</span>
        <span v-if="formatEvalTime(evalRow)" class="eval-dialog-time">{{ formatEvalTime(evalRow) }}</span>
        <span class="eval-colon">：</span>
        <span class="eval-project-name">{{ evalRow.name }}</span>
      </p>
      <el-input
        v-model="evalContent"
        type="textarea"
        :rows="8"
        maxlength="5000"
        show-word-limit
        placeholder="填写后学生可在该竞赛详情页的「教师评价」中查看；"
      />
      <template #footer>
        <el-button @click="evalDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="evalSaving" @click="saveEval">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Delete, Document, Download, Trophy, Upload } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

const isStudent = computed(() => userStore.role === 'student')

const pageSubtitle = computed(() => {
  if (isStudent.value) {
    return '仅展示本人作为负责人的项目；新建时需选择负责老师，所选教师可在教师端查看'
  }
  if (userStore.role === 'admin') {
    return '维护科创项目档案；管理员可查看全部'
  }
  return '展示您作为项目负责老师或竞赛「负责老师」中包含您账号的竞赛下的项目'
})

/** 该项目档案是否已有竞赛成绩（含 0 分） */
const projectHasScore = (row) =>
  row.scoreTotal !== undefined && row.scoreTotal !== null && row.scoreTotal !== ''

/** 同队同竞赛已有其它档案打分时不可打分；本项目已有成绩后也不可再次打分 */
const showScoreButton = (row) =>
  !!row.competitionId && !row.scoreBlockedByPeer && !projectHasScore(row)

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const competitionOptions = ref([])
const teacherOptions = ref([])
const teacherOptionsLoading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const evalDialogVisible = ref(false)
const evalRow = ref(null)
const evalContent = ref('')
const evalSaving = ref(false)
const scoreDialogVisible = ref(false)
const scoreRow = ref(null)
const scoreInput = ref(null)
const scoreSaving = ref(false)
const detailDialogVisible = ref(false)
const detailView = reactive({ name: '', description: '', advisorTeacherName: '', teacherEvaluation: '' })

const filters = reactive({ keyword: '', competition: undefined, status: '' })
const page = reactive({ current: 1, size: 20, total: 0 })
/** 创建时间排序：desc 从新到旧（默认），asc 从旧到新 */
const timeOrder = ref('desc')

const toggleTimeSort = () => {
  timeOrder.value = timeOrder.value === 'desc' ? 'asc' : 'desc'
  page.current = 1
  loadList()
}

const statusLabels = {
  ongoing: '进行中',
  completed: '已结束',
  planning: '进行中',
  suspended: '进行中'
}

const form = reactive({
  name: '',
  description: '',
  competitionId: undefined,
  advisorTeacherId: undefined,
  leaderName: '',
  leaderStudentId: '',
  status: 'ongoing',
  remark: ''
})

const leaderDisplay = computed(() => {
  const name = userStore.userInfo?.name || ''
  const sid = userStore.userInfo?.studentId || ''
  if (name && sid) return `${name}（${sid}）`
  return name || sid || '—'
})

const teacherOptionLabel = (t) => {
  const roleLabel = t.role === 'admin' ? '管理员' : t.role === 'teacher' ? '教师' : ''
  const parts = [t.name, t.college, roleLabel].filter(Boolean)
  return parts.length ? parts.join(' · ') : t.username || `#${t.id}`
}

const formRules = computed(() => {
  const base = {
    name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
  }
  if (isStudent.value) {
    base.advisorTeacherId = [{ required: true, message: '请选择负责老师', trigger: 'change' }]
  } else {
    base.status = [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
  return base
})

const loadCompetitionOptions = async () => {
  try {
    const list = await request.get('/innovation-project/competition-options')
    competitionOptions.value = Array.isArray(list) ? list : []
  } catch {
    competitionOptions.value = []
  }
}

const loadTeacherOptions = async () => {
  teacherOptionsLoading.value = true
  try {
    const list = await request.get('/innovation-project/teacher-options')
    teacherOptions.value = Array.isArray(list) ? list : []
  } catch {
    teacherOptions.value = []
  } finally {
    teacherOptionsLoading.value = false
  }
}

const loadList = async () => {
  loading.value = true
  try {
    const params = {
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      timeOrder: timeOrder.value,
      page: page.current,
      size: page.size
    }
    if (!isStudent.value && filters.competition != null && filters.competition !== '') {
      params.competition = filters.competition
    }
    const res = await request.get('/innovation-project/list', { params })
    rows.value = res.list || []
    page.total = res.total || 0
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.current = 1
  loadList()
}

const resetForm = () => {
  Object.assign(form, {
    name: '',
    description: '',
    competitionId: undefined,
    advisorTeacherId: undefined,
    leaderName: '',
    leaderStudentId: '',
    status: 'ongoing',
    remark: ''
  })
}

const openCreate = async () => {
  resetForm()
  if (isStudent.value) {
    form.leaderName = userStore.userInfo?.name || ''
    form.leaderStudentId = userStore.userInfo?.studentId || ''
  }
  if (isStudent.value) await loadTeacherOptions()
  dialogVisible.value = true
}

const onEvalDialogClosed = () => {
  evalRow.value = null
  evalContent.value = ''
}

const onScoreDialogClosed = () => {
  scoreRow.value = null
  scoreInput.value = null
}

const openScore = (row) => {
  scoreRow.value = row
  scoreInput.value = null
  scoreDialogVisible.value = true
}

const saveScore = async () => {
  if (!scoreRow.value) return
  if (scoreInput.value === undefined || scoreInput.value === null || Number.isNaN(Number(scoreInput.value))) {
    ElMessage.warning('请输入有效成绩')
    return
  }
  scoreSaving.value = true
  try {
    await request.put(`/innovation-project/${scoreRow.value.id}/score`, {
      totalScore: Number(scoreInput.value)
    })
    ElMessage.success('成绩已保存（未公示）')
    scoreDialogVisible.value = false
    loadList()
  } catch {
    /* 拦截器已提示 */
  } finally {
    scoreSaving.value = false
  }
}

const formatEvalTime = (row) => {
  const t = row?.teacherEvaluationAt
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

const onDetailClosed = () => {
  detailView.name = ''
  detailView.description = ''
  detailView.advisorTeacherName = ''
  detailView.teacherEvaluation = ''
}

const openDetail = (row) => {
  detailView.name = row.name != null ? String(row.name) : ''
  detailView.description = row.description != null ? String(row.description) : ''
  detailView.advisorTeacherName = row.advisorTeacherName != null ? String(row.advisorTeacherName) : ''
  detailView.teacherEvaluation = row.teacherEvaluation != null ? String(row.teacherEvaluation) : ''
  detailDialogVisible.value = true
}

const openEval = (row) => {
  evalRow.value = row
  evalContent.value = row.teacherEvaluation != null ? String(row.teacherEvaluation) : ''
  evalDialogVisible.value = true
}

const saveEval = async () => {
  if (!evalRow.value) return
  evalSaving.value = true
  try {
    await request.put(`/innovation-project/${evalRow.value.id}/teacher-evaluation`, { content: evalContent.value })
    ElMessage.success('评价已保存')
    evalDialogVisible.value = false
    loadList()
  } catch {
    /* 拦截器已提示 */
  } finally {
    evalSaving.value = false
  }
}

const submitForm = async () => {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const payload = {
      name: form.name,
      description: form.description || undefined,
      advisorTeacherId: form.advisorTeacherId ?? undefined,
      remark: form.remark || undefined
    }
    if (!isStudent.value) {
      payload.competitionId = form.competitionId ?? undefined
      payload.leaderName = form.leaderName || undefined
      payload.leaderStudentId = form.leaderStudentId || undefined
      payload.status = form.status
    } else {
      payload.status = 'ongoing'
    }
    await request.post('/innovation-project', payload)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadList()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.name}」吗？`, '提示', { type: 'warning' })
    await request.delete(`/innovation-project/${row.id}`)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    if (e !== 'cancel') { /* ignore */ }
  }
}

const handleAttach = async (options, row) => {
  try {
    const fd = new FormData()
    fd.append('file', options.file)
    await request.post(`/innovation-project/${row.id}/attachment`, fd, { timeout: 120000 })
    ElMessage.success('附件已上传')
    loadList()
  } catch {
    /* 拦截器已提示 */
  }
}

const downloadAttach = (row) => {
  const token = sessionStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    return
  }
  window.open(`/api/innovation-project/${row.id}/attachment?token=${encodeURIComponent(token)}`, '_blank')
}

onMounted(async () => {
  if (!isStudent.value) await loadCompetitionOptions()
  loadList()
})
</script>

<style scoped>
.page-container {
  animation: pageEnter 0.5s var(--ease-out);
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }

.page-header {
  display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }

.filter-card { margin-bottom: 16px; background: var(--bg-card); border-color: var(--border); }
.filter-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 240px; max-width: 100%; }

.table-card { background: var(--bg-card); border-color: var(--border); min-width: 0; }
.table-responsive {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
}
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; flex-wrap: wrap; gap: 8px; }

.form-row { display: flex; gap: 16px; }
.flex-1 { flex: 1; }

.form-hint { font-size: 12px; color: var(--text-muted); margin: 8px 0 0; line-height: 1.5; }

.muted { color: var(--text-muted); font-size: 13px; }

.op-panel {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 6px 4px;
  box-sizing: border-box;
}

.op-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  width: 100%;
}

.op-grid__upload {
  min-width: 0;
}

.op-grid__upload :deep(.el-upload) {
  display: block;
  width: 100%;
}

.op-grid__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  border-radius: 6px;
  border: 1px dashed var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.op-placeholder-dash {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1;
}

.op-btn {
  width: 100%;
  margin: 0 !important;
  padding: 5px 6px !important;
  height: auto !important;
  min-height: 28px;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  border-radius: 6px;
}

.op-btn__icon {
  font-size: 14px;
  flex-shrink: 0;
}

.op-btn--full {
  grid-column: 1 / -1;
}

.op-score {
  padding-top: 2px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.op-btn--score {
  border-style: solid;
}

.time-col-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.sort-time-btn {
  padding: 4px;
  min-height: auto;
  background: transparent !important;
  --el-button-hover-link-text-color: var(--el-color-primary);
}
.sort-time-btn:hover,
.sort-time-btn:focus-visible {
  background: transparent !important;
}
.sort-time-btn .el-icon {
  font-size: 19px;
  color: var(--el-color-primary);
}
.sort-time-btn :deep(svg) {
  color: var(--el-color-primary);
}
.sort-time-btn :deep(svg path[stroke]),
.sort-time-btn :deep(svg line),
.sort-time-btn :deep(svg polyline),
.sort-time-btn :deep(svg circle[stroke]) {
  stroke: currentColor;
  stroke-width: 2.75;
}
.sort-time-btn :deep(svg path) {
  stroke-width: 2.75;
}

.eval-dialog-tip {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--text-secondary);
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 8px;
}
.eval-word {
  font-weight: 600;
  color: var(--text-primary);
}
.eval-dialog-time {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
}
.eval-colon {
  margin: 0 -2px 0 0;
}
.eval-project-name {
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.detail-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-field .detail-label {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 6px;
}
.detail-field .detail-value {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
  word-break: break-word;
}
.detail-desc {
  white-space: pre-wrap;
  max-height: 320px;
  overflow-y: auto;
}

.score-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.score-dialog-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.score-input-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.score-input-label {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--text-primary);
}
</style>
