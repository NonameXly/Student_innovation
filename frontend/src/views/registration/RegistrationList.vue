<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">报名管理</h1>
        <p class="page-subtitle">审核和管理竞赛报名信息</p>
      </div>
    </div>

    <el-card class="filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="搜索学生姓名/学号..." prefix-icon="Search" clearable class="filter-input" />
        <el-select v-model="filters.competition" placeholder="所属竞赛" clearable style="width: 260px">
          <el-option label="全国大学生数学建模竞赛" value="1" />
          <el-option label="挑战杯课外学术科技作品竞赛" value="2" />
          <el-option label="ACM-ICPC 选拔赛" value="3" />
        </el-select>
        <el-select v-model="filters.status" placeholder="审核状态" clearable>
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
        <el-button @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
      </div>
    </el-card>

    <!-- 批量操作 -->
    <div class="batch-bar" v-if="selectedRows.length">
      <span class="batch-info">已选 <strong>{{ selectedRows.length }}</strong> 项</span>
      <el-button type="primary" size="small" @click="handleBatchApprove">
        <el-icon><Check /></el-icon>批量通过
      </el-button>
      <el-button type="danger" size="small" @click="handleBatchReject">
        <el-icon><Close /></el-icon>批量驳回
      </el-button>
    </div>

    <el-card class="table-card">
      <el-table :data="registrations" @selection-change="handleSelectionChange" style="width: 100%" row-key="id">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="studentName" label="学生姓名" width="100" />
        <el-table-column prop="studentId" label="学号" width="130" />
        <el-table-column prop="college" label="学院" width="140" />
        <el-table-column prop="competition" label="报名竞赛" min-width="220" show-overflow-tooltip />
        <el-table-column prop="teamName" label="团队名称" width="140" />
        <el-table-column prop="enrollTime" label="报名时间" width="160" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type" size="small" effect="dark">
              {{ statusMap[row.status]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button size="small" type="primary" text @click="handleApprove(row)">
                <el-icon><Check /></el-icon>通过
              </el-button>
              <el-button size="small" type="danger" text @click="handleReject(row)">
                <el-icon><Close /></el-icon>驳回
              </el-button>
            </template>
            <el-button size="small" text @click="handleViewDetail(row)">
              <el-icon><View /></el-icon>详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </el-card>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回报名" width="480px">
      <el-form>
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="4" placeholder="请输入驳回原因..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确定驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const filters = reactive({ keyword: '', competition: '', status: '' })
const page = reactive({ current: 1, size: 20, total: 56 })
const selectedRows = ref([])
const rejectVisible = ref(false)
const rejectReason = ref('')
let rejectTarget = null

const statusMap = {
  pending: { type: 'warning', text: '待审核' },
  approved: { type: 'success', text: '已通过' },
  rejected: { type: 'danger', text: '已驳回' }
}

const registrations = ref([])
const router = useRouter()
const route = useRoute()

const registrationDetailRouteName = computed(() =>
  route.path.startsWith('/teacher') ? 'TeacherRegistrationTeamDetail' : 'RegistrationTeamDetail'
)

const loadList = async () => {
  try {
    const res = await request.get('/registration/list', { params: {
      keyword: filters.keyword || undefined,
      competition: filters.competition || undefined,
      status: filters.status || undefined,
      page: page.current,
      size: page.size
    }})
    registrations.value = res.list || []
    page.total = res.total || 0
  } catch { /* ignore */ }
}

const handleSelectionChange = (rows) => { selectedRows.value = rows }
const handleSearch = () => { page.current = 1; loadList() }

const handleApprove = async (row) => {
  try {
    await request.post(`/registration/${row.id}/approve`)
    ElMessage.success(`已通过 ${row.studentName} 的报名`)
    loadList()
  } catch { ElMessage.error('操作失败') }
}

const handleReject = (row) => {
  rejectTarget = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (rejectTarget) {
    try {
      await request.post(`/registration/${rejectTarget.id}/reject`, { reason: rejectReason.value })
      ElMessage.success(`已驳回 ${rejectTarget.studentName} 的报名`)
      loadList()
    } catch { ElMessage.error('操作失败') }
  }
  rejectVisible.value = false
}

const handleBatchApprove = async () => {
  const ids = selectedRows.value.filter(r => r.status === 'pending').map(r => r.id)
  if (!ids.length) return
  try {
    await request.post('/registration/batch/approve', { ids })
    ElMessage.success(`已批量通过 ${ids.length} 条报名`)
    loadList()
  } catch { ElMessage.error('操作失败') }
}

const handleBatchReject = () => {
  rejectTarget = null
  rejectReason.value = ''
  rejectVisible.value = true
}

const handleViewDetail = (row) => {
  router.push({
    name: registrationDetailRouteName.value,
    params: { id: row.id },
    query: {
      teamName: row.teamName || '',
      status: row.status || '',
      studentName: row.studentName || '',
      studentId: row.studentId || '',
      college: row.college || '',
      competition: row.competition || '',
      enrollTime: row.enrollTime || ''
    }
  })
}

onMounted(loadList)
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }
.filter-card { margin-bottom: 16px; background: var(--bg-card); border-color: var(--border); }
.filter-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 220px; }
.batch-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 16px; margin-bottom: 16px;
  background: rgba(232,168,56,0.08); border: 1px solid rgba(232,168,56,0.2);
  border-radius: var(--radius-sm);
}
.batch-info { font-size: 13px; color: var(--text-secondary); }
.batch-info strong { color: var(--accent); }
.table-card { background: var(--bg-card); border-color: var(--border); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
