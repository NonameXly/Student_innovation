<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ canManage ? '公告管理' : '公告通知' }}</h1>
        <p class="page-subtitle">{{ canManage ? '发布和管理平台公告通知' : '查看平台公告通知' }}</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="openForm()">
        <el-icon><Plus /></el-icon>发布公告
      </el-button>
    </div>

    <div class="announcement-layout">
      <div class="ann-list-wrap">
        <div v-for="(item, i) in announcements" :key="item.id" class="ann-item" :class="{ active: selected?.id === item.id }" @click="handleSelectItem(item)" :style="{ animationDelay: `${i * 0.05}s` }">
          <div class="ann-item__header">
            <el-tag v-if="item.pinned" size="small" type="danger" effect="plain">置顶</el-tag>
            <el-tag :type="item.type === 'important' ? 'warning' : 'info'" size="small" effect="plain">
              {{ item.type === 'important' ? '重要' : '普通' }}
            </el-tag>
            <span class="ann-item__date">{{ item.date }}</span>
          </div>
          <h3 class="ann-item__title">{{ item.title }}</h3>
          <p class="ann-item__preview">{{ item.content.slice(0, 80) }}...</p>
          <div class="ann-item__footer">
            <span class="ann-item__author"><el-icon><User /></el-icon> {{ item.author }}</span>
            <span class="ann-item__views"><el-icon><View /></el-icon> {{ item.views }}</span>
          </div>
        </div>
      </div>

      <div class="ann-detail-wrap" v-if="selected">
        <el-card class="detail-card">
          <div class="detail-header">
            <h2 class="detail-title">{{ selected.title }}</h2>
            <div class="detail-meta">
              <span><el-icon><User /></el-icon> {{ selected.author }}</span>
              <span><el-icon><Calendar /></el-icon> {{ selected.date }}</span>
              <span><el-icon><View /></el-icon> {{ selected.views }} 次浏览</span>
            </div>
          </div>
          <div class="detail-body" v-html="selected.content.replace(/\n/g, '<br/>')"></div>
          <div v-if="canManage" class="detail-actions">
            <el-button text @click="openForm(selected)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button text :type="selected.pinned ? '' : 'warning'" @click="togglePin(selected)">
              <el-icon><Top /></el-icon>{{ selected.pinned ? '取消置顶' : '置顶' }}
            </el-button>
            <el-popconfirm title="确定删除该公告吗？" @confirm="handleDelete(selected.id)">
              <template #reference>
                <el-button text type="danger"><el-icon><Delete /></el-icon>删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>
      </div>
      <div class="ann-detail-wrap ann-empty" v-else>
        <el-empty description="请选择一条公告查看详情" />
      </div>
    </div>

    <!-- 发布/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="formData.id ? '编辑公告' : '发布公告'" width="600px" destroy-on-close>
      <el-form :model="formData" label-position="top" ref="formRef" :rules="formRules">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入公告标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="公告类型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio value="important">重要公告</el-radio>
            <el-radio value="normal">普通公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="10" placeholder="请输入公告内容..." maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="formData.pinned">置顶公告</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">{{ formData.id ? '更新' : '发布' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const canManage = computed(() => ['admin', 'teacher'].includes(userStore.role))
const selected = ref(null)
const formVisible = ref(false)
const formRef = ref()

const formData = reactive({ id: null, title: '', type: 'normal', content: '', pinned: false })
const formRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

const announcements = ref([])

const loadList = async () => {
  const res = await request.get('/announcement/list', { params: { page: 1, size: 50 } })
  announcements.value = (res.list || []).map(a => ({
    ...a,
    date: a.createdAt ? String(a.createdAt).replace('T', ' ').substring(0, 16) : ''
  }))
}

onMounted(() => { loadList() })

const openForm = (item) => {
  if (item) {
    Object.assign(formData, { ...item })
  } else {
    Object.assign(formData, { id: null, title: '', type: 'normal', content: '', pinned: false })
  }
  formVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (formData.id) {
    await request.put(`/announcement/${formData.id}`, formData)
  } else {
    await request.post('/announcement', formData)
  }
  ElMessage.success(formData.id ? '更新成功' : '发布成功')
  formVisible.value = false
  loadList()
}

const togglePin = async (item) => {
  await request.put(`/announcement/${item.id}/pin`, { pinned: !item.pinned })
  item.pinned = !item.pinned
  ElMessage.success(item.pinned ? '已置顶' : '已取消置顶')
}

const handleDelete = async (id) => {
  await request.delete(`/announcement/${id}`)
  announcements.value = announcements.value.filter(a => a.id !== id)
  if (selected.value?.id === id) selected.value = null
  ElMessage.success('删除成功')
}

const handleSelectItem = async (item) => {
  selected.value = item
  await request.post(`/announcement/${item.id}/view`)
  item.views = (item.views || 0) + 1
}
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }

.announcement-layout { display: grid; grid-template-columns: 400px 1fr; gap: 20px; }

.ann-list-wrap { display: flex; flex-direction: column; gap: 12px; max-height: calc(100vh - 200px); overflow-y: auto; padding-right: 8px; }
.ann-item {
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-md);
  padding: 16px 18px; cursor: pointer; transition: all 0.25s var(--ease-out);
  animation: cardSlideUp 0.4s var(--ease-out) both;
}
.ann-item:hover { border-color: var(--border-light); background: var(--bg-hover); }
.ann-item.active { border-color: var(--accent); box-shadow: var(--shadow-glow); }

@keyframes cardSlideUp { from { opacity: 0; transform: translateY(12px); } }

.ann-item__header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ann-item__date { margin-left: auto; font-size: 12px; color: var(--text-muted); }
.ann-item__title { font-size: 15px; color: var(--text-primary); margin-bottom: 6px; font-weight: 600; }
.ann-item__preview { font-size: 13px; color: var(--text-secondary); line-height: 1.5; }
.ann-item__footer { display: flex; justify-content: space-between; margin-top: 10px; font-size: 12px; color: var(--text-muted); }
.ann-item__author, .ann-item__views { display: flex; align-items: center; gap: 4px; }

.detail-card { background: var(--bg-card); border-color: var(--border); position: sticky; top: 20px; }
.detail-title { font-family: var(--font-display); font-size: 22px; color: var(--text-primary); margin-bottom: 12px; }
.detail-meta { display: flex; gap: 20px; color: var(--text-muted); font-size: 13px; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid var(--border); }
.detail-meta span { display: flex; align-items: center; gap: 4px; }
.detail-body { color: var(--text-secondary); font-size: 14px; line-height: 2; min-height: 200px; }
.detail-actions { display: flex; gap: 8px; margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--border); }
.ann-empty { display: flex; align-items: center; justify-content: center; min-height: 400px; }
</style>
