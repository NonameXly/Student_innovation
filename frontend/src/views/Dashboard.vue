<template>
  <div class="dashboard">
    <div class="page-header">
      <div class="page-header__bg"></div>
      <div class="page-header__content">
        <h1 class="page-title">工作台</h1>
        <p class="page-subtitle">欢迎回来，{{ userStore.userInfo?.name || '用户' }}</p>
      </div>
    </div>

    <!-- 竞赛统计图表 -->
    <div v-if="canManage" class="chart-row">
      <el-card class="chart-card" shadow="never">
        <template #header>
          <div class="card-header card-header--stack">
            <span class="card-title">竞赛类型分布</span>
            <span class="card-hint">近一个月内发布的竞赛类别占比</span>
          </div>
        </template>
        <div ref="categoryChartRef" class="chart-box" v-loading="chartsLoading" />
        <el-table
          v-if="!chartsLoading && categoryDistribution.length"
          :data="categoryDistribution"
          size="small"
          class="chart-table"
          stripe
        >
          <el-table-column prop="category" label="类别" min-width="100" />
          <el-table-column prop="count" label="数量" width="72" align="center" />
          <el-table-column label="占比" width="88" align="center">
            <template #default="{ row }">{{ row.percent }}%</template>
          </el-table-column>
        </el-table>
        <div v-if="!chartsLoading && !categoryDistribution.length" class="chart-empty">近一个月内暂无新发布竞赛</div>
      </el-card>

      <el-card class="chart-card" shadow="never">
        <template #header>
          <div class="card-header card-header--stack">
            <span class="card-title">竞赛发布数量</span>
            <span class="card-hint">近半年每月发布竞赛数</span>
          </div>
        </template>
        <div ref="monthlyChartRef" class="chart-box" v-loading="chartsLoading" />
        <el-table
          v-if="!chartsLoading && monthlyPublish.length"
          :data="monthlyPublish"
          size="small"
          class="chart-table"
          stripe
        >
          <el-table-column prop="label" label="月份" min-width="110" />
          <el-table-column prop="count" label="发布数量" width="100" align="center" />
        </el-table>
      </el-card>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards" :class="{ 'stat-cards--student': isStudent }">
      <div
        v-for="(stat, i) in displayStats"
        :key="stat.key"
        class="stat-card"
        :class="{ 'stat-card--student': isStudent }"
        :style="{ animationDelay: `${i * 0.1}s` }"
      >
        <div class="stat-card__icon" :style="{ background: stat.bg }">
          <el-icon :size="22" :color="stat.color"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__value">{{ stat.value }}</span>
          <span class="stat-card__label">{{ stat.label }}</span>
        </div>
        <div class="stat-card__trend" :class="stat.trendUp ? 'up' : 'down'">
          <el-icon :size="14"><component :is="stat.trendUp ? 'Top' : 'Bottom'" /></el-icon>
          {{ stat.trend }}
        </div>
      </div>
    </div>

    <div class="dashboard-grid">
      <!-- 近期竞赛 -->
      <el-card class="grid-card grid-card--wide">
        <template #header>
          <div class="card-header">
            <span class="card-title">近期竞赛</span>
            <el-button text type="primary" @click="$router.push(withPrefix('/competition'))">查看全部</el-button>
          </div>
        </template>
        <el-table :data="recentCompetitions" style="width: 100%">
          <el-table-column prop="name" label="竞赛名称" min-width="180" />
          <el-table-column prop="category" label="类别" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.categoryType">{{ row.category }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deadline" label="报名截止" width="120" />
          <el-table-column prop="participants" label="报名人数" width="100" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.statusType" size="small" effect="dark">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 最新公告 -->
      <el-card class="grid-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">最新公告</span>
            <el-button text type="primary" @click="$router.push(withPrefix('/announcement'))">更多</el-button>
          </div>
        </template>
        <div class="announcement-list">
          <div v-for="(item, i) in announcements" :key="i" class="announcement-item">
            <div class="announcement-dot" :class="item.type"></div>
            <div class="announcement-content">
              <p class="announcement-title">{{ item.title }}</p>
              <span class="announcement-date">{{ item.date }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 快捷操作 -->
      <el-card class="grid-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">快捷操作</span>
          </div>
        </template>
        <div class="quick-actions">
          <div v-if="isAdmin" class="quick-action" @click="$router.push(withPrefix('/competition/create'))">
            <el-icon :size="24" color="var(--accent)"><Plus /></el-icon>
            <span>发起竞赛</span>
          </div>
          <div v-if="canManage" class="quick-action" @click="$router.push(withPrefix('/registration'))">
            <el-icon :size="24" color="var(--info)"><DocumentChecked /></el-icon>
            <span>审核报名</span>
          </div>
          <div v-if="canManage" class="quick-action" @click="$router.push(withPrefix('/score'))">
            <el-icon :size="24" color="var(--success)"><DataAnalysis /></el-icon>
            <span>录入成绩</span>
          </div>
          <div v-if="isStudent" class="quick-action" @click="$router.push(withPrefix('/competition'))">
            <el-icon :size="24" color="var(--accent)"><Trophy /></el-icon>
            <span>浏览竞赛</span>
          </div>
          <div v-if="isStudent" class="quick-action" @click="$router.push(withPrefix('/announcement'))">
            <el-icon :size="24" color="var(--info)"><Bell /></el-icon>
            <span>查看公告</span>
          </div>
          <div class="quick-action" @click="$router.push(withPrefix('/ai-assistant'))">
            <el-icon :size="24" color="var(--warning)"><MagicStick /></el-icon>
            <span>AI 助手</span>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { usePortalPrefix } from '../utils/portal'

const { withPrefix } = usePortalPrefix()

const userStore = useUserStore()
const canManage = computed(() => ['admin', 'teacher'].includes(userStore.role))
const isAdmin = computed(() => userStore.role === 'admin')
const isStudent = computed(() => userStore.role === 'student')

const stats = ref([
  { key: 'ongoing', label: '进行中竞赛', value: '0', icon: 'Trophy', bg: 'rgba(232,168,56,0.15)', color: '#e8a838', trend: '+0', trendUp: true },
  { key: 'pending', label: '待审核报名', value: '0', icon: 'DocumentChecked', bg: 'rgba(93,173,226,0.15)', color: '#5dade2', trend: '+0', trendUp: true },
  { key: 'students', label: '注册学生', value: '0', icon: 'User', bg: 'rgba(46,204,113,0.15)', color: '#2ecc71', trend: '+0', trendUp: true },
  { key: 'messages', label: '本月留言', value: '0', icon: 'ChatDotSquare', bg: 'rgba(243,156,18,0.15)', color: '#f39c12', trend: '+0', trendUp: false }
])
const displayStats = computed(() =>
  isStudent.value ? stats.value.filter(item => !['pending', 'students'].includes(item.key)) : stats.value
)

const recentCompetitions = ref([])
const announcements = ref([])
const categoryDistribution = ref([])
const monthlyPublish = ref([])
const chartsLoading = ref(false)
const categoryChartRef = ref(null)
const monthlyChartRef = ref(null)
let categoryChart = null
let monthlyChart = null

const CHART_COLORS = ['#2a5298', '#5dade2', '#e8a838', '#2ecc71', '#9b59b6', '#e74c3c', '#95a5a6']

const disposeCharts = () => {
  categoryChart?.dispose()
  monthlyChart?.dispose()
  categoryChart = null
  monthlyChart = null
}

const renderCategoryChart = () => {
  if (!categoryChartRef.value) return
  if (!categoryChart) {
    categoryChart = echarts.init(categoryChartRef.value)
  }
  const data = categoryDistribution.value.map((d) => ({
    name: d.category,
    value: d.count
  }))
  if (!data.length) {
    categoryChart.clear()
    return
  }
  categoryChart.setOption({
    color: CHART_COLORS,
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%' },
      data: data.length ? data : []
    }]
  }, true)
}

const renderMonthlyChart = () => {
  if (!monthlyChartRef.value) return
  if (!monthlyChart) {
    monthlyChart = echarts.init(monthlyChartRef.value)
  }
  const labels = monthlyPublish.value.map((d) => d.label)
  const counts = monthlyPublish.value.map((d) => d.count)
  monthlyChart.setOption({
    color: ['#2a5298'],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: 40 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { rotate: labels.length > 4 ? 28 : 0, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      name: '场'
    },
    series: [{
      type: 'bar',
      data: counts,
      barMaxWidth: 40,
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#5dade2' },
          { offset: 1, color: '#2a5298' }
        ])
      }
    }]
  }, true)
}

const renderCharts = async () => {
  if (!canManage.value) return
  await nextTick()
  renderCategoryChart()
  renderMonthlyChart()
}

const onChartResize = () => {
  categoryChart?.resize()
  monthlyChart?.resize()
}

const loadDashboard = async () => {
  try {
    const res = await request.get('/dashboard/overview')
    if (res.stats && res.stats.length === 4) {
      res.stats.forEach((s, i) => {
        stats.value[i].value = s.value
        stats.value[i].trend = s.trend
        stats.value[i].trendUp = s.trendUp
      })
    }
    if (res.recentCompetitions) {
      const statusMap = { preparing: '筹备中', enrolling: '报名中', enrollend: '报名已结束', ongoing: '进行中', finished: '已结束' }
      const statusTypeMap = { preparing: 'info', enrolling: 'success', enrollend: 'warning', ongoing: 'warning', finished: '' }
      const catTypeMap = { '数学': '', '计算机': 'danger', '电子': 'info', '综合': 'warning', '创新创业': 'success' }
      recentCompetitions.value = res.recentCompetitions.map(c => ({
        name: c.name,
        category: c.category,
        categoryType: catTypeMap[c.category] || '',
        deadline: c.enrollEnd || '',
        participants: c.participants,
        status: statusMap[c.status] || c.status,
        statusType: statusTypeMap[c.status] || ''
      }))
    }
    if (res.announcements) {
      announcements.value = res.announcements.map(a => ({
        title: a.title,
        date: a.createdAt ? a.createdAt.substring(0, 10) : '',
        type: a.type
      }))
    }
    categoryDistribution.value = res.categoryDistribution || []
    monthlyPublish.value = res.monthlyPublish || []
    chartsLoading.value = false
    await renderCharts()
  } catch {
    chartsLoading.value = false
  }
}

onMounted(() => {
  if (canManage.value) chartsLoading.value = true
  loadDashboard()
  window.addEventListener('resize', onChartResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onChartResize)
  disposeCharts()
})
</script>

<style scoped>
.dashboard {
  animation: pageEnter 0.5s var(--ease-out);
}

@keyframes pageEnter {
  from { opacity: 0; transform: translateY(8px); }
}

.page-header {
  margin-bottom: 32px;
  position: relative;
  padding: 40px 48px;
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--primary-gradient);
  color: #fff;
  box-shadow: 0 12px 32px rgba(59, 130, 246, 0.25);
}

.page-header__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 80% 20%, rgba(232,168,56,0.15) 0%, transparent 50%),
    radial-gradient(circle at 20% 80%, rgba(93,173,226,0.1) 0%, transparent 50%);
}

.page-header__bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    url("data:image/svg+xml,%3Csvg width='40' height='40' viewBox='0 0 40 40' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='%23ffffff' fill-opacity='0.04' fill-rule='evenodd'%3E%3Cpath d='M0 20L20 0l20 20-20 20z'/%3E%3C/g%3E%3C/svg%3E");
}

.page-header__content {
  position: relative;
  z-index: 1;
}

.page-title {
  font-family: var(--font-display);
  font-size: 28px;
  color: #ffffff;
  margin-bottom: 4px;
}

.page-subtitle {
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

/* 竞赛统计图表 */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.chart-card {
  background: var(--glass-bg);
  border: var(--glass-border);
}

.chart-card :deep(.el-card__header) {
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--border);
}

.chart-card :deep(.el-card__body) {
  padding: 12px 16px 16px;
}

.card-header--stack {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.card-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}

.chart-box {
  width: 100%;
  height: 260px;
  margin-bottom: 12px;
}

.chart-table {
  margin-top: 4px;
}

.chart-empty {
  text-align: center;
  padding: 16px;
  font-size: 13px;
  color: var(--text-muted);
}

/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-cards--student .stat-card--student {
  grid-column: span 2;
}

.stat-card {
  background: var(--glass-bg);
  border: var(--glass-border);
  backdrop-filter: blur(16px) saturate(120%);
  -webkit-backdrop-filter: blur(16px) saturate(120%);
  border-radius: var(--radius-lg);
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  overflow: hidden;
  animation: cardSlideUp 0.6s var(--ease-out) both;
  transition: all 0.4s var(--ease-out);
  cursor: default;
  box-shadow: var(--shadow-sm);
}

.stat-card:hover {
  border-color: var(--primary-lighter);
  box-shadow: 0 4px 16px rgba(43, 95, 158, 0.08);
  transform: translateY(-2px);
}

@keyframes cardSlideUp {
  from { opacity: 0; transform: translateY(16px); }
}

.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.stat-card__value {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-card__label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.stat-card__trend {
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 2px;
}

.stat-card__trend.up {
  color: var(--success);
}

.stat-card__trend.down {
  color: var(--danger);
}

/* 网格布局 */
.dashboard-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
}

.grid-card--wide {
  grid-column: 1;
  grid-row: 1 / 3;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 公告列表 */
.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.announcement-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  cursor: pointer;
  padding: 8px;
  margin: -8px;
  border-radius: var(--radius-sm);
  transition: background 0.2s;
}

.announcement-item:hover {
  background: var(--bg-hover);
}

.announcement-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  margin-top: 6px;
  flex-shrink: 0;
}

.announcement-dot.important {
  background: var(--accent);
  box-shadow: 0 0 8px rgba(232, 168, 56, 0.4);
}

.announcement-content {
  flex: 1;
}

.announcement-title {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.4;
}

.announcement-date {
  font-size: 12px;
  color: var(--text-muted);
}

/* 快捷操作 */
.quick-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.quick-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 12px;
  background: var(--bg-hover);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s var(--ease-out);
  border: 1px solid transparent;
}

.quick-action:hover {
  border-color: var(--border-light);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.quick-action span {
  font-size: 13px;
  color: var(--text-secondary);
}

@media (max-width: 1200px) {
  .chart-row {
    grid-template-columns: 1fr;
  }

  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-cards--student .stat-card--student {
    grid-column: span 1;
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .grid-card--wide {
    grid-column: 1;
    grid-row: auto;
  }
}
</style>
