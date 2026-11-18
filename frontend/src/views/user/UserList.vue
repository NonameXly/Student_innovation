<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">系统用户权限与角色管理</p>
      </div>
      <el-button type="primary" @click="handleAddUser"><el-icon><Plus /></el-icon>创建用户</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div v-for="(s, i) in stats" :key="i" class="stat-chip" :style="{ animationDelay: `${i * 0.06}s` }">
        <el-icon :size="18" :style="{ color: s.color }"><component :is="s.icon" /></el-icon>
        <span class="stat-num">{{ s.value }}</span>
        <span class="stat-label">{{ s.label }}</span>
      </div>
    </div>

    <el-card class="filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="搜索用户名/姓名/邮箱..." prefix-icon="Search" clearable class="filter-input" />
        <el-select v-model="filters.role" placeholder="角色" clearable>
          <el-option label="管理员" value="admin" />
          <el-option label="教师" value="teacher" />
          <el-option label="学生" value="student" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable>
          <el-option label="正常" value="active" />
          <el-option label="禁用" value="disabled" />
        </el-select>
        <el-button @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="users" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" class="user-avatar" :style="{ background: row.avatarColor }">
                {{ row.name.charAt(0) }}
              </el-avatar>
              <div>
                <div class="user-name">{{ row.name }}</div>
                <div class="user-email">{{ row.email }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="college" label="学院/部门" width="130" />
        <el-table-column label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="roleMap[row.role]?.type" size="small" effect="plain">{{ roleMap[row.role]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.active" size="small" @change="(v) => handleStatusChange(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="lastLogin" label="最近登录" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="handleEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-popconfirm title="确定删除该用户?" @confirm="handleDelete(row)" width="200">
              <template #reference>
                <el-button text size="small" type="danger"><el-icon><Delete /></el-icon>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :total="page.total" layout="total, sizes, prev, pager, next" background />
      </div>
    </el-card>

    <!-- 用户表单弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑用户' : '创建用户'" width="520px" destroy-on-close>
      <el-form :model="userForm" label-position="top">
        <div class="form-row">
          <el-form-item label="姓名" required class="flex-1">
            <el-input v-model="userForm.name" placeholder="真实姓名" />
          </el-form-item>
          <el-form-item label="用户名" required class="flex-1">
            <el-input v-model="userForm.username" placeholder="登录账号" />
          </el-form-item>
        </div>
        <el-form-item label="邮箱" required>
          <el-input v-model="userForm.email" placeholder="user@example.com" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="角色" required class="flex-1">
            <el-select v-model="userForm.role" style="width: 100%">
              <el-option label="管理员" value="admin" />
              <el-option label="教师" value="teacher" />
              <el-option label="学生" value="student" />
            </el-select>
          </el-form-item>
          <el-form-item label="学院/部门" class="flex-1">
            <el-input v-model="userForm.college" placeholder="所属学院或部门" />
          </el-form-item>
        </div>
        <el-form-item v-if="userForm.role === 'student'" label="学号" required>
          <el-input v-model="userForm.studentId" placeholder="学生学号" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="密码" :required="!isEdit">
          <el-input v-model="userForm.password" type="password" show-password placeholder="不修改请留空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, markRaw, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User, UserFilled, Avatar, Promotion } from '@element-plus/icons-vue'
import request from '../../utils/request'

const filters = reactive({ keyword: '', role: '', status: '' })
const page = reactive({ current: 1, size: 20, total: 0 })
const formVisible = ref(false)
const isEdit = ref(false)
const userForm = reactive({ id: null, name: '', username: '', email: '', role: 'student', college: '', studentId: '', password: '' })

const roleMap = {
  admin: { type: 'danger', text: '管理员' },
  teacher: { type: 'warning', text: '教师' },
  student: { type: '', text: '学生' }
}

const avatarColors = ['#e8a838', '#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444']

const stats = ref([
  { label: '总用户', value: 0, icon: markRaw(User), color: 'var(--accent)' },
  { label: '学生', value: 0, icon: markRaw(Avatar), color: 'var(--info)' },
  { label: '教师', value: 0, icon: markRaw(UserFilled), color: 'var(--success)' },
  { label: '管理员', value: 0, icon: markRaw(Promotion), color: 'var(--danger)' }
])

const users = ref([])

const loadList = async () => {
  const res = await request.get('/user/list', { params: { keyword: filters.keyword, role: filters.role, status: filters.status, page: page.current, size: page.size } })
  users.value = (res.list || []).map((u, i) => ({ ...u, avatarColor: avatarColors[i % avatarColors.length] }))
  page.total = res.total || 0
}

const loadStats = async () => {
  const res = await request.get('/user/statistics')
  stats.value[0].value = res.totalUsers || 0
  stats.value[1].value = res.students || 0
  stats.value[2].value = res.teachers || 0
  stats.value[3].value = res.admins || 0
}

onMounted(() => { loadList(); loadStats() })

const handleSearch = () => { page.current = 1; loadList() }
const handleAddUser = () => { isEdit.value = false; Object.assign(userForm, { id: null, name: '', username: '', email: '', role: 'student', college: '', studentId: '', password: '' }); formVisible.value = true }
const handleEdit = (row) => { isEdit.value = true; Object.assign(userForm, { ...row, password: '' }); formVisible.value = true }
const handleSave = async () => {
  if (userForm.role === 'student' && !(userForm.studentId || '').trim()) {
    ElMessage.warning('学生用户须填写学号')
    return
  }
  const body = { ...userForm }
  if (body.role !== 'student') {
    delete body.studentId
  }
  if (isEdit.value) {
    await request.put(`/user/${userForm.id}`, body)
  } else {
    await request.post('/user', body)
  }
  ElMessage.success(isEdit.value ? '用户更新成功' : '用户创建成功')
  formVisible.value = false
  loadList(); loadStats()
}
const handleDelete = async (row) => {
  await request.delete(`/user/${row.id}`)
  ElMessage.success(`已删除用户：${row.name}`)
  loadList(); loadStats()
}
const handleStatusChange = async (row, val) => {
  await request.put(`/user/${row.id}/status`, { active: val })
  ElMessage.success(`${row.name} 已${val ? '启用' : '禁用'}`)
}
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }

.stat-row { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
.stat-chip {
  display: flex; align-items: center; gap: 8px;
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: 10px 20px;
  animation: chipIn 0.4s var(--ease-out) both;
  transition: border-color 0.3s;
}
.stat-chip:hover { border-color: var(--border-light); }
@keyframes chipIn { from { opacity: 0; transform: scale(0.92); } }
.stat-num { font-weight: 700; font-size: 18px; color: var(--text-primary); }
.stat-label { font-size: 13px; color: var(--text-muted); }

.filter-card { margin-bottom: 16px; background: var(--bg-card); border-color: var(--border); }
.filter-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 260px; }
.table-card { background: var(--bg-card); border-color: var(--border); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }

.user-cell { display: flex; align-items: center; gap: 10px; }
.user-avatar { font-size: 14px; font-weight: 600; color: #fff; flex-shrink: 0; }
.user-name { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.user-email { font-size: 12px; color: var(--text-muted); }

.form-row { display: flex; gap: 16px; }
.flex-1 { flex: 1; }
</style>
