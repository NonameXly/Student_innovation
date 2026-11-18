<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">成绩管理</h1>
        <p class="page-subtitle">管理科创竞赛成绩与数据分析</p>
      </div>
      <div class="header-actions">
        <el-button @click="handleExport"><el-icon><Download /></el-icon>导出成绩</el-button>
        <el-button @click="openImportDialog"><el-icon><Upload /></el-icon>导入成绩</el-button>
        <el-button type="primary" @click="openCreateScore"><el-icon><Plus /></el-icon>录入成绩</el-button>
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="score-overview">
      <div v-for="(item, i) in overview" :key="i" class="overview-item" :style="{ animationDelay: `${i * 0.08}s` }">
        <div class="overview-value" :style="{ color: item.color }">{{ item.value }}</div>
        <div class="overview-label">{{ item.label }}</div>
      </div>
    </div>

    <el-card class="filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="搜索学生姓名/学号..." prefix-icon="Search" clearable class="filter-input" />
        <el-select
          v-model="filters.competition"
          placeholder="所属竞赛"
          clearable
          filterable
          style="width: 260px"
          :loading="competitionLoading"
        >
          <el-option v-for="c in competitionOptions" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="filters.award" placeholder="奖项等级" clearable>
          <el-option label="一等奖" value="first" />
          <el-option label="二等奖" value="second" />
          <el-option label="三等奖" value="third" />
          <el-option label="优秀奖" value="excellent" />
        </el-select>
        <el-button @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="scores" style="width: 100%" :default-sort="{ prop: 'totalScore', order: 'descending' }">
        <el-table-column type="index" label="排名" width="70" align="center" />
        <el-table-column prop="studentName" label="学生姓名" width="100" />
        <el-table-column prop="studentId" label="学号" width="130" />
        <el-table-column prop="college" label="学院" width="120" />
        <el-table-column prop="competition" label="竞赛" min-width="200" show-overflow-tooltip />
        <el-table-column prop="teamName" label="团队名称" width="120" />
        <el-table-column prop="totalScore" label="总分" width="80" align="center" sortable>
          <template #default="{ row }">
            <span class="score-value" :class="scoreClass(row.totalScore)">{{ row.totalScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="奖项" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="awardMap[row.award]?.type" size="small" effect="dark" v-if="row.award">
              {{ awardMap[row.award]?.text }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="公示" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.show === 1 ? 'success' : 'info'" size="small">
              {{ row.show === 1 ? '已公示' : '未公示' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="handleEditScore(row)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button text size="small" type="primary" @click="handleDetail(row)"><el-icon><DataAnalysis /></el-icon>分析</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :total="page.total" layout="total, sizes, prev, pager, next" background />
      </div>
    </el-card>

    <!-- 成绩录入弹窗 -->
    <el-dialog v-model="scoreFormVisible" title="录入成绩" width="560px" destroy-on-close>
      <el-form :model="scoreForm" label-position="top" ref="scoreFormRef">
        <el-form-item label="所属竞赛" required>
          <el-select
            v-model="scoreForm.competition"
            placeholder="请选择竞赛"
            filterable
            style="width: 100%"
            :loading="competitionLoading"
          >
            <el-option v-for="c in competitionOptions" :key="c.id" :label="c.name" :value="c.name" />
          </el-select>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="学生姓名" required class="flex-1">
            <el-input v-model="scoreForm.studentName" placeholder="姓名" />
          </el-form-item>
          <el-form-item label="学号" required class="flex-1">
            <el-input v-model="scoreForm.studentId" placeholder="学号" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="总分" required class="flex-1">
            <el-input-number v-model="scoreForm.totalScore" :min="0" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="奖项" class="flex-1">
            <el-select v-model="scoreForm.award" placeholder="请选择" style="width: 100%" clearable>
              <el-option label="一等奖" value="first" />
              <el-option label="二等奖" value="second" />
              <el-option label="三等奖" value="third" />
              <el-option label="优秀奖" value="excellent" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="scoreForm.remark" type="textarea" :rows="2" placeholder="备注信息..." />
        </el-form-item>
        <el-form-item label="向学生公示成绩">
          <el-switch
            v-model="scoreForm.show"
            :active-value="1"
            :inactive-value="0"
            inline-prompt
            active-text="公示"
            inactive-text="隐藏"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scoreFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveScore">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exportVisible" title="导出成绩" width="520px" destroy-on-close @open="loadFinishedCompetitions" @closed="resetExportState">
      <p class="export-hint">请选择<strong>已结束</strong>的竞赛，将导出该竞赛下的全部成绩记录（Excel 格式，表头与导入模板一致）。</p>
      <el-select
        v-model="exportCompetitionId"
        class="export-competition-select"
        placeholder="选择竞赛"
        filterable
        style="width: 100%"
        popper-class="export-competition-popper"
        :loading="exportCompetitionLoading"
        no-data-text="暂无已结束的竞赛"
      >
        <el-option
          v-for="c in finishedCompetitions"
          :key="c.id"
          :label="c.name"
          :value="c.id"
        >
          <div class="export-option">
            <span class="export-option__name">{{ c.name }}</span>
            <span class="export-option__meta">{{ c.category || '—' }} · {{ c.scoreCount }} 条成绩</span>
          </div>
        </el-option>
      </el-select>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!exportCompetitionId" @click="confirmExport">
          <el-icon><Download /></el-icon>导出
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入成绩" width="560px" destroy-on-close @closed="resetImportState">
      <p class="import-hint">
        请上传 <strong>.xlsx</strong>，首行表头须与导出完全一致：
        <span class="import-headers">排名、学生姓名、学号、学院、竞赛、总分、奖项</span>。
        后端按列解析并写入数据库；同一学号同一竞赛已存在则更新总分与奖项。
      </p>
      <p class="import-hint">
        <el-button link type="primary" @click="downloadImportTemplate">下载 Excel 模板</el-button>
        （也可先「导出成绩」在导出表上修改后再导入。）
      </p>
      <div class="import-mode-row">
        <el-button
          class="grading-toggle"
          :type="importUseGrading ? 'primary' : 'default'"
          :plain="!importUseGrading"
          @click="importUseGrading = !importUseGrading"
        >
          赋分制
        </el-button>
        <span class="import-mode-hint">{{ importUseGrading ? '已开启：导入时按赋分制规则计算成绩。' : '已关闭：按表格「总分」列原样导入。' }}</span>
      </div>
      <div class="import-toolbar">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          @change="handleImportFileChange"
        >
          <el-button><el-icon><FolderOpened /></el-icon>选择 Excel 文件</el-button>
        </el-upload>
        <span v-if="importFileName" class="import-filename">{{ importFileName }}</span>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importSubmitting" :disabled="!importFile" @click="submitImport">上传并导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const filters = reactive({ keyword: '', competition: undefined, award: '' })
const page = reactive({ current: 1, size: 20, total: 0 })
const competitionOptions = ref([])
const competitionLoading = ref(false)
const scoreFormVisible = ref(false)
const scoreFormRef = ref()

const exportVisible = ref(false)
const exportCompetitionId = ref(undefined)
const finishedCompetitions = ref([])
const exportCompetitionLoading = ref(false)

const importVisible = ref(false)
const importFileName = ref('')
const importFile = ref(null)
const importSubmitting = ref(false)
/** 导入成绩：开启则走赋分制，关闭则按 Excel 总分列原样写入（后端需识别 useGrading） */
const importUseGrading = ref(false)

const scoreForm = reactive({ competition: '', studentName: '', studentId: '', totalScore: 0, award: '', remark: '', show: 0 })

const awardMap = {
  first: { type: 'danger', text: '一等奖' },
  second: { type: 'warning', text: '二等奖' },
  third: { type: '', text: '三等奖' },
  excellent: { type: 'info', text: '优秀奖' }
}

const overview = ref([
  { label: '已评竞赛', value: '0', color: 'var(--accent)' },
  { label: '总参赛人次', value: '0', color: 'var(--info)' },
  { label: '获奖率', value: '0%', color: 'var(--success)' },
  { label: '平均分', value: '0', color: 'var(--warning)' }
])

const scores = ref([])

const loadCompetitions = async () => {
  competitionLoading.value = true
  try {
    const res = await request.get('/competition/list', { params: { page: 1, size: 500 } })
    const list = res.list || []
    competitionOptions.value = list.map((c) => ({ id: c.id, name: c.name || '' })).filter((c) => c.id != null && c.name)
  } catch {
    competitionOptions.value = []
  } finally {
    competitionLoading.value = false
  }
}

const openCreateScore = () => {
  Object.assign(scoreForm, {
    id: undefined,
    competition: '',
    studentName: '',
    studentId: '',
    totalScore: 0,
    award: '',
    remark: '',
    show: 0
  })
  scoreFormVisible.value = true
}

const loadList = async () => {
  try {
    const res = await request.get('/score/list', { params: {
      keyword: filters.keyword || undefined,
      competition: filters.competition != null && filters.competition !== '' ? filters.competition : undefined,
      award: filters.award || undefined,
      page: page.current,
      size: page.size
    }})
    scores.value = res.list || []
    page.total = res.total || 0
  } catch { /* ignore */ }
}

const loadStats = async () => {
  try {
    const res = await request.get('/score/statistics')
    overview.value[0].value = String(res.evaluatedCompetitions || 0)
    overview.value[1].value = String(res.totalParticipants || 0)
    overview.value[2].value = res.awardRate || '0%'
    overview.value[3].value = String(res.averageScore || 0)
  } catch { /* ignore */ }
}

const scoreClass = (score) => score >= 90 ? 'excellent' : score >= 80 ? 'good' : score >= 60 ? 'pass' : 'fail'

function openImportDialog () {
  resetImportState()
  importVisible.value = true
}

function resetImportState () {
  importFileName.value = ''
  importFile.value = null
  importSubmitting.value = false
  importUseGrading.value = false
}

function downloadImportTemplate () {
  const token = sessionStorage.getItem('token')
  if (!token) {
    ElMessage.warning('登录已失效，请重新登录')
    return
  }
  window.open(`/api/score/import-template?token=${encodeURIComponent(token)}`, '_blank')
}

function handleImportFileChange (uploadFile) {
  const file = uploadFile?.raw
  if (!file) return
  if (!file.name?.toLowerCase().endsWith('.xlsx')) {
    ElMessage.warning('请上传 .xlsx 文件')
    return
  }
  importFile.value = file
  importFileName.value = file.name
}

async function submitImport () {
  if (!importFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  const fd = new FormData()
  fd.append('file', importFile.value)
  fd.append('useGrading', importUseGrading.value ? 'true' : 'false')
  importSubmitting.value = true
  try {
    const data = await request.post('/score/import', fd, { timeout: 120000 })
    const inserted = data?.inserted ?? 0
    const updated = data?.updated ?? 0
    const failed = data?.failed ?? []
    if (inserted > 0 || updated > 0) {
      ElMessage.success(`导入完成：新增 ${inserted} 条，更新 ${updated} 条`)
    }
    if (failed.length) {
      ElMessage({
        type: inserted > 0 || updated > 0 ? 'warning' : 'error',
        duration: 10000,
        showClose: true,
        message: `${failed.length} 行未写入：\n${failed.slice(0, 8).join('\n')}${failed.length > 8 ? '\n…' : ''}`
      })
    }
    importVisible.value = false
    loadList()
    loadStats()
  } catch {
    /* 拦截器已提示 */
  } finally {
    importSubmitting.value = false
  }
}

const handleSearch = () => { page.current = 1; loadList() }

function resetExportState () {
  exportCompetitionId.value = undefined
  finishedCompetitions.value = []
}

async function loadFinishedCompetitions () {
  exportCompetitionLoading.value = true
  try {
    const list = await request.get('/score/export/competitions')
    finishedCompetitions.value = Array.isArray(list) ? list : []
    if (filters.competition != null && filters.competition !== '') {
      const hit = finishedCompetitions.value.find((c) => c.id === filters.competition)
      if (hit) exportCompetitionId.value = hit.id
    }
  } catch {
    finishedCompetitions.value = []
  } finally {
    exportCompetitionLoading.value = false
  }
}

const handleExport = () => {
  exportCompetitionId.value = undefined
  exportVisible.value = true
}

const confirmExport = () => {
  if (!exportCompetitionId.value) {
    ElMessage.warning('请选择要导出的竞赛')
    return
  }
  const token = sessionStorage.getItem('token')
  if (!token) {
    ElMessage.warning('登录已失效，请重新登录')
    return
  }
  const url = `/api/score/export?competitionId=${encodeURIComponent(exportCompetitionId.value)}&token=${encodeURIComponent(token)}`
  window.open(url, '_blank')
  exportVisible.value = false
  ElMessage.success('正在导出，请查看浏览器下载')
}
const handleEditScore = (row) => {
  Object.assign(scoreForm, {
    ...row,
    show: row.show === 1 ? 1 : 0
  })
  scoreFormVisible.value = true
}
const handleSaveScore = async () => {
  try {
    if (scoreForm.id) {
      await request.put(`/score/${scoreForm.id}`, scoreForm)
    } else {
      await request.post('/score', scoreForm)
    }
    ElMessage.success('成绩保存成功')
    scoreFormVisible.value = false
    loadList()
    loadStats()
  } catch { ElMessage.error('保存失败') }
}
const handleDetail = async (row) => {
  try {
    const res = await request.get(`/score/${row.id}/analysis`)
    ElMessage.info(`${res.studentName} 排名第${res.ranking}，超越${res.scorePercentile}%，${res.awardInfo}`)
  } catch { ElMessage.error('获取分析失败') }
}

onMounted(() => {
  loadCompetitions()
  loadList()
  loadStats()
})
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }
.header-actions { display: flex; gap: 10px; }

.score-overview {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px;
}
.overview-item {
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-md);
  padding: 20px 24px; text-align: center;
  animation: cardSlideUp 0.5s var(--ease-out) both;
  transition: border-color 0.3s;
}
.overview-item:hover { border-color: var(--border-light); }
@keyframes cardSlideUp { from { opacity: 0; transform: translateY(16px); } }
.overview-value { font-family: var(--font-display); font-size: 32px; font-weight: 700; margin-bottom: 4px; }
.overview-label { font-size: 13px; color: var(--text-muted); }

.filter-card { margin-bottom: 16px; background: var(--bg-card); border-color: var(--border); }
.filter-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 220px; }

.table-card { background: var(--bg-card); border-color: var(--border); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }

.score-value { font-weight: 700; font-size: 15px; }
.score-value.excellent { color: var(--danger); }
.score-value.good { color: var(--accent); }
.score-value.pass { color: var(--text-primary); }
.score-value.fail { color: var(--text-muted); }
.text-muted { color: var(--text-muted); }

.form-row { display: flex; gap: 16px; }
.flex-1 { flex: 1; }

.import-hint { font-size: 13px; color: var(--text-secondary); line-height: 1.6; margin: 0 0 14px; }
.import-headers { color: var(--text-primary); font-weight: 500; }
.import-mode-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.import-mode-hint { font-size: 13px; color: var(--text-muted); line-height: 1.5; flex: 1; min-width: 200px; }
.grading-toggle { flex-shrink: 0; }
.import-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.import-filename { font-size: 13px; color: var(--text-muted); }

.export-hint { font-size: 13px; color: var(--text-secondary); line-height: 1.6; margin: 0 0 16px; }
.export-option { display: flex; flex-direction: column; gap: 6px; line-height: 1.45; padding: 4px 0; white-space: normal; }
.export-option__name { font-size: 14px; color: var(--text-primary); line-height: 1.4; }
.export-option__meta { font-size: 12px; color: var(--text-muted); line-height: 1.35; }
</style>

<!-- 下拉层挂载在 body，需非 scoped 覆盖 el-option 固定高度 -->
<style>
.export-competition-popper .el-select-dropdown__item {
  height: auto !important;
  min-height: 56px;
  padding: 10px 16px;
  line-height: 1.45;
  white-space: normal;
}
.export-competition-popper .el-select-dropdown__item + .el-select-dropdown__item {
  margin-top: 2px;
}
.export-competition-popper .el-select-dropdown__list {
  padding: 6px 0;
}
</style>
