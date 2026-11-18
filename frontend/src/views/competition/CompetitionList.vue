<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ canManage ? '竞赛管理' : '竞赛列表' }}</h1>
        <p class="page-subtitle">{{ canManage ? '管理所有科创竞赛项目' : '浏览所有科创竞赛项目' }}</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="goCreate">
        <el-icon><Plus /></el-icon>发起竞赛
      </el-button>
    </div>

    <!-- 筛选区域 -->
    <el-card class="filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="搜索竞赛名称..." prefix-icon="Search" clearable class="filter-input" />
        <el-select v-model="filters.category" placeholder="竞赛类别" clearable>
          <el-option label="数学" value="数学" />
          <el-option label="计算机" value="计算机" />
          <el-option label="电子" value="电子" />
          <el-option label="综合" value="综合" />
          <el-option label="创新创业" value="创新创业" />
        </el-select>
        <el-select v-model="filters.status" placeholder="竞赛状态" clearable>
          <el-option label="筹备中" value="preparing" />
          <el-option label="报名中" value="enrolling" />
          <el-option label="报名已结束" value="enrollend" />
          <el-option label="进行中" value="ongoing" />
          <el-option label="已结束" value="finished" />
          <el-option v-if="canManage" label="草稿" value="draft" />
        </el-select>
        <el-button @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <!-- 竞赛列表 -->
    <div class="competition-grid">
      <div v-for="(item, i) in competitions" :key="item.id" class="comp-card" :style="{ animationDelay: `${i * 0.06}s` }">
        <div class="comp-card__banner" :style="{ background: bannerGradients[i % bannerGradients.length] }">
          <el-tag :type="cardStatusTag(item).type" size="small" effect="dark" class="comp-card__status">
            {{ cardStatusTag(item).text }}
          </el-tag>
          <div class="comp-card__category">{{ item.category }}</div>
        </div>
        <div class="comp-card__body">
          <h3 class="comp-card__title" @click="goDetail(item.id)">{{ item.name }}</h3>
          <p class="comp-card__desc">{{ item.description }}</p>
          <div class="comp-card__meta">
            <div class="meta-item">
              <el-icon><Calendar /></el-icon>
              <span>{{ item.startDate }} ~ {{ item.endDate }}</span>
            </div>
            <div class="meta-item">
              <el-icon><User /></el-icon>
              <span>{{ item.participants }} 人报名</span>
            </div>
          </div>
          <div class="comp-card__footer">
            <el-button size="small" text type="primary" @click="goDetail(item.id)">查看详情</el-button>
            <div class="comp-card__footer-right">
              <el-button
                v-if="isStudentRoute && uploadStatus[item.id]?.canUpload"
                size="small"
                type="warning"
                @click="openProjectUploadDialog(item)"
              >
                <el-icon><Upload /></el-icon>上传项目
              </el-button>
              <el-button v-if="!canManage && item.status === 'enrolling'" size="small" type="success" @click="goEnroll(item.id)">
                <el-icon><DocumentChecked /></el-icon>立即报名
              </el-button>
              <div v-if="isStaff" class="comp-card__actions">
                <el-popconfirm
                  v-if="!item.isDraft"
                  title="将该竞赛下所有已录入成绩公示给学生？（无成绩记录则更新 0 条）"
                  confirm-button-text="确定公示"
                  cancel-button-text="取消"
                  width="280"
                  @confirm="handlePublishScores(item.id)"
                >
                  <template #reference>
                    <el-button size="small" text type="success">展示成绩</el-button>
                  </template>
                </el-popconfirm>
                <template v-if="canManage">
                  <el-button size="small" text @click="handleEdit(item)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-popconfirm title="确定删除该竞赛吗？" @confirm="handleDelete(item.id)">
                    <template #reference>
                      <el-button size="small" text type="danger">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[12, 24, 36]"
        layout="total, sizes, prev, pager, next"
        background
      />
    </div>

    <StudentProjectUploadDialog
      v-model="projectUploadDialogVisible"
      :competition-id="projectUploadCompetitionId"
      @success="onProjectUploadDialogSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'
import { usePortalPrefix } from '../../utils/portal'
import StudentProjectUploadDialog from '../../components/StudentProjectUploadDialog.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { withPrefix } = usePortalPrefix()
const isAdmin = computed(() => userStore.role === 'admin')
const isStaff = computed(() => ['admin', 'teacher'].includes(userStore.role))
const canManage = isAdmin
const isStudentRoute = computed(() => route.path.startsWith('/student'))

const competitionDetailName = computed(() => {
  if (isStudentRoute.value) return 'StudentCompetitionDetail'
  if (route.path.startsWith('/teacher')) return 'TeacherCompetitionDetail'
  return 'CompetitionDetail'
})

const filters = reactive({ keyword: '', category: '', status: '' })
const page = reactive({ current: 1, size: 12, total: 0 })

const bannerGradients = [
  'linear-gradient(135deg, #1a2744 0%, #2a5298 100%)',
  'linear-gradient(135deg, #1e3c58 0%, #22766a 100%)',
  'linear-gradient(135deg, #2d1b4e 0%, #6b3fa0 100%)',
  'linear-gradient(135deg, #4a1a2e 0%, #a03a5f 100%)',
  'linear-gradient(135deg, #2f2e10 0%, #8a7a2a 100%)',
  'linear-gradient(135deg, #0e3026 0%, #1a7a5c 100%)'
]

const competitions = ref([])
const uploadStatus = ref({})
const projectUploadDialogVisible = ref(false)
const projectUploadCompetitionId = ref(null)

const openProjectUploadDialog = (item) => {
  projectUploadCompetitionId.value = item.id
  projectUploadDialogVisible.value = true
}

const onProjectUploadDialogSuccess = async () => {
  await loadUploadStatuses()
}

const loadUploadStatuses = async () => {
  if (!isStudentRoute.value || !competitions.value.length) {
    uploadStatus.value = {}
    return
  }
  const ids = competitions.value.map((c) => c.id).filter(Boolean).join(',')
  if (!ids) {
    uploadStatus.value = {}
    return
  }
  try {
    const res = await request.get('/apply/project-upload-status', { params: { competitionIds: ids } })
    uploadStatus.value = res && typeof res === 'object' ? res : {}
  } catch {
    uploadStatus.value = {}
  }
}

const loadList = async () => {
  try {
    const res = await request.get('/competition/list', { params: {
      keyword: filters.keyword || undefined,
      category: filters.category || undefined,
      status: filters.status || undefined,
      page: page.current,
      size: page.size
    }})
    competitions.value = res.list || []
    page.total = res.total || 0
  } catch { /* ignore */ }
  await loadUploadStatuses()
}

const statusTag = (status) => {
  const map = {
    preparing: { type: 'info', text: '筹备中' },
    enrolling: { type: 'success', text: '报名中' },
    enrollend: { type: 'warning', text: '报名已结束' },
    ongoing: { type: 'warning', text: '进行中' },
    finished: { type: '', text: '已结束' }
  }
  return map[status] || { type: 'info', text: status }
}

/** 卡片左上角：草稿优先显示「草稿」，否则显示竞赛进度状态 */
const cardStatusTag = (item) => {
  if (item.isDraft === true || item.isDraft === 1) {
    return { type: 'info', text: '草稿' }
  }
  return statusTag(item.status)
}

const handleSearch = () => { page.current = 1; loadList() }
const resetFilters = () => { Object.assign(filters, { keyword: '', category: '', status: '' }); handleSearch() }
const goCreate = () => { router.push(withPrefix('/competition/create')) }
const handleEdit = (item) => { router.push(withPrefix(`/competition/edit/${item.id}`)) }
const goDetail = (id) => {
  router.push({
    name: competitionDetailName.value,
    params: { id }
  })
}
const goEnroll = (id) => {
  router.push({ name: 'StudentCompetitionEnroll', params: { id } })
}
const handleDelete = async (id) => {
  try {
    await request.delete(`/competition/${id}`)
    ElMessage.success('删除成功')
    loadList()
  } catch { ElMessage.error('删除失败') }
}

const handlePublishScores = async (competitionId) => {
  try {
    const data = await request.post('/score/publish-by-competition', null, {
      params: { competitionId }
    })
    const n = data?.updated ?? 0
    const ns = data?.noticesSent ?? 0
    let msg = n > 0 ? `已公示 ${n} 条成绩` : '该竞赛暂无成绩记录或已全部公示'
    if (ns > 0) msg += `，已向 ${ns} 名参赛学生推送通知`
    ElMessage.success(msg)
  } catch {
    /* 拦截器已提示 */
  }
}

watch(() => page.current, loadList)
watch(() => page.size, () => { page.current = 1; loadList() })
onMounted(loadList)
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

.filter-card { margin-bottom: 24px; background: var(--bg-card); border-color: var(--border); }
.filter-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 240px; }

.competition-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.comp-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: transform 0.3s var(--ease-out), border-color 0.3s, box-shadow 0.3s;
  animation: cardSlideUp 0.5s var(--ease-out) both;
}
.comp-card:hover {
  transform: translateY(-4px);
  border-color: var(--border-light);
  box-shadow: var(--shadow-md);
}
@keyframes cardSlideUp { from { opacity: 0; transform: translateY(20px); } }

.comp-card__banner {
  height: 100px;
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12px 16px;
}
.comp-card__status { position: relative; z-index: 1; }
.comp-card__category {
  background: rgba(0,0,0,0.35);
  backdrop-filter: blur(8px);
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  color: #fff;
}
.comp-card__body { padding: 18px; }
.comp-card__title {
  font-family: var(--font-display);
  font-size: 17px;
  color: var(--text-primary);
  margin-bottom: 8px;
  cursor: pointer;
  transition: color 0.2s;
  line-height: 1.4;
}
.comp-card__title:hover { color: var(--accent); }
.comp-card__desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.comp-card__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}
.comp-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}
.comp-card__footer-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.list-upload { display: inline-flex; }
.list-upload :deep(.el-upload) { display: inline-flex; }
.comp-card__actions { display: flex; gap: 4px; }

.pagination-wrap { display: flex; justify-content: flex-end; }
</style>
