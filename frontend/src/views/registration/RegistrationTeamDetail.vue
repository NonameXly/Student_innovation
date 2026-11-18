<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">队伍详情</h1>
        <p class="page-subtitle">查看报名小队的成员与审核信息</p>
      </div>
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>返回列表
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card class="table-card">
          <template #header>
            <div class="card-title">基本信息</div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="团队名称">{{ detail.teamName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="审核状态">
              <el-tag :type="statusMap[detail.status]?.type || 'info'" effect="dark">
                {{ statusMap[detail.status]?.text || '未知' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="队长姓名">{{ detail.studentName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="队长学号">{{ detail.studentId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="学院">{{ detail.college || '-' }}</el-descriptions-item>
            <el-descriptions-item label="报名竞赛">{{ detail.competition || '-' }}</el-descriptions-item>
            <el-descriptions-item label="报名时间">{{ detail.enrollTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="报名编号">#{{ detail.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="驳回原因" :span="2">{{ detail.rejectReason || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card class="table-card members-card">
          <template #header>
            <div class="card-title">队伍成员</div>
          </template>
          <el-table :data="memberList" style="width: 100%">
            <el-table-column prop="name" label="姓名" min-width="120" />
            <el-table-column prop="studentId" label="学号" min-width="160" />
            <el-table-column prop="college" label="学院" min-width="180" />
            <el-table-column label="角色" min-width="100">
              <template #default="{ row }">{{ roleLabel(row.role) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="table-card">
          <template #header>
            <div class="card-title">审核记录</div>
          </template>
          <el-timeline>
            <el-timeline-item timestamp="当前状态" placement="top">
              {{ statusMap[detail.status]?.text || '未知状态' }}
            </el-timeline-item>
            <el-timeline-item :timestamp="detail.enrollTime || '未记录'" placement="top">
              提交报名申请
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { usePortalPrefix } from '../../utils/portal'

const route = useRoute()
const router = useRouter()
const { withPrefix } = usePortalPrefix()

const statusMap = {
  pending: { type: 'warning', text: '待审核' },
  approved: { type: 'success', text: '已通过' },
  rejected: { type: 'danger', text: '已驳回' }
}

const detail = ref({
  id: route.params.id,
  teamName: route.query.teamName || '',
  status: route.query.status || '',
  studentName: route.query.studentName || '',
  studentId: route.query.studentId || '',
  college: route.query.college || '',
  competition: route.query.competition || '',
  enrollTime: route.query.enrollTime || '',
  rejectReason: '',
  memberList: []
})

const memberList = computed(() => {
  if (detail.value.memberList?.length) return detail.value.memberList
  return [
    {
      name: detail.value.studentName || '未命名',
      studentId: detail.value.studentId || '-',
      college: detail.value.college || '-',
      role: 'captain'
    }
  ]
})

const roleLabel = (role) => {
  if (role === 'captain') return '队长'
  if (role === 'leader') return '队长'
  if (role === 'member') return '成员'
  return role || '-'
}

const loadDetail = async () => {
  try {
    const res = await request.get(`/registration/${route.params.id}/team-detail`)
    detail.value = {
      ...detail.value,
      ...res
    }
  } catch {
    ElMessage.error('加载队伍详情失败')
  }
}

const goBack = () => router.push(withPrefix('/registration'))

onMounted(loadDetail)
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }
.table-card { background: var(--bg-card); border-color: var(--border); margin-bottom: 16px; }
.members-card { margin-bottom: 0; }
.card-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
</style>
