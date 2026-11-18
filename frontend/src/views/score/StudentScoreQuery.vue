<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">成绩查询</h1>
        <p class="page-subtitle">查看本人在各竞赛中的成绩与排名分析</p>
      </div>
    </div>

    <el-card class="filter-card">
      <div class="filter-row">
        <el-input
          v-model="filters.competitionName"
          placeholder="竞赛名称（模糊）"
          prefix-icon="Search"
          clearable
          class="filter-input-wide"
        />
        <el-select v-model="filters.category" placeholder="竞赛类型" clearable style="width: 160px">
          <el-option label="数学" value="数学" />
          <el-option label="计算机" value="计算机" />
          <el-option label="电子" value="电子" />
          <el-option label="综合" value="综合" />
          <el-option label="创新创业" value="创新创业" />
        </el-select>
        <el-select v-model="filters.award" placeholder="奖项等级" clearable style="width: 200px">
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
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="competition" label="竞赛" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalScore" label="总分" width="90" align="center" sortable>
          <template #default="{ row }">
            <span class="score-value" :class="scoreClass(row.totalScore)">{{ row.totalScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="奖项" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.award" :type="awardMap[row.award]?.type" size="small" effect="dark">
              {{ awardMap[row.award]?.text }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="handleDetail(row)">
              <el-icon><DataAnalysis /></el-icon>分析
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const filters = reactive({ competitionName: '', category: '', award: '' })
const page = reactive({ current: 1, size: 20, total: 0 })
const scores = ref([])

const awardMap = {
  first: { type: 'danger', text: '一等奖' },
  second: { type: 'warning', text: '二等奖' },
  third: { type: '', text: '三等奖' },
  excellent: { type: 'info', text: '优秀奖' }
}

const scoreClass = (score) => (score >= 90 ? 'excellent' : score >= 80 ? 'good' : score >= 60 ? 'pass' : 'fail')

const loadList = async () => {
  try {
    const name = (filters.competitionName || '').trim()
    const res = await request.get('/score/list', {
      params: {
        competitionName: name || undefined,
        competitionCategory: filters.category || undefined,
        award: filters.award || undefined,
        page: page.current,
        size: page.size
      }
    })
    scores.value = res.list || []
    page.total = res.total || 0
  } catch {
    /* 拦截器已提示 */
  }
}

const handleSearch = () => {
  page.current = 1
  loadList()
}

const handleDetail = async (row) => {
  try {
    const res = await request.get(`/score/${row.id}/analysis`)
    ElMessage.info(`${res.studentName} 排名第${res.ranking}，超越${res.scorePercentile}%，${res.awardInfo}`)
  } catch {
    ElMessage.error('获取分析失败')
  }
}

watch(() => [page.current, page.size], () => loadList())

onMounted(() => loadList())
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }

.filter-card { margin-bottom: 16px; background: var(--bg-card); border-color: var(--border); }
.filter-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.filter-input-wide { width: 220px; }

.table-card { background: var(--bg-card); border-color: var(--border); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }

.score-value { font-weight: 700; font-size: 15px; }
.score-value.excellent { color: var(--danger); }
.score-value.good { color: var(--accent); }
.score-value.pass { color: var(--text-primary); }
.score-value.fail { color: var(--text-muted); }
.text-muted { color: var(--text-muted); }
</style>
