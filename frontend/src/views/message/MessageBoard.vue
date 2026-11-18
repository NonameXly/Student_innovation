<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">留言板</h1>
        <p class="page-subtitle">交流科创经验，分享竞赛心得</p>
      </div>
    </div>

    <!-- 发言区 -->
    <el-card class="post-card">
      <div class="post-input-area">
        <el-avatar :size="40" class="post-avatar">{{ userStore.userInfo?.name?.charAt(0) || 'U' }}</el-avatar>
        <div class="post-input-wrap">
          <el-input v-model="newMessage" type="textarea" :rows="3" placeholder="分享你的科创经验、竞赛心得或提问..." maxlength="500" show-word-limit resize="none" />
          <div class="post-actions">
            <el-button type="primary" :disabled="!newMessage.trim()" @click="handlePost">
              <el-icon><Position /></el-icon>发布
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 搜索 -->
    <el-card class="search-card">
      <div class="search-row">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索留言内容、作者或回复..."
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleClearSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>搜索
        </el-button>
        <el-button @click="handleClearSearch">重置</el-button>
      </div>
      <p v-if="searchKeyword.trim()" class="search-hint">
        当前关键词：<strong>{{ searchKeyword.trim() }}</strong>，共 {{ total }} 条匹配
      </p>
    </el-card>

    <!-- 留言列表 -->
    <div v-loading="loading" class="message-list">
      <div v-if="!loading && messages.length === 0" class="list-empty">
        {{ searchKeyword.trim() ? '未找到匹配的留言' : '暂无留言，来发表第一条吧' }}
      </div>
      <div v-for="(msg, i) in messages" :key="msg.id" class="msg-card" :style="{ animationDelay: `${i * 0.06}s` }">
        <div class="msg-card__left">
          <el-avatar :size="42" class="msg-avatar" :style="{ background: avatarColors[i % avatarColors.length] }">
            {{ msg.author.charAt(0) }}
          </el-avatar>
        </div>
        <div class="msg-card__body">
          <div class="msg-header">
            <span class="msg-author">{{ msg.author }}</span>
            <el-tag v-if="msg.tag" size="small" effect="plain" type="info">{{ msg.tag }}</el-tag>
            <span class="msg-time">{{ msg.time }}</span>
          </div>
          <p class="msg-content">{{ msg.content }}</p>
          <div class="msg-footer">
            <button class="msg-action" :class="{ liked: msg.liked }" @click="toggleLike(msg)">
              <el-icon><Star /></el-icon> {{ msg.likes }}
            </button>
            <button class="msg-action" @click="toggleReply(msg)">
              <el-icon><ChatDotRound /></el-icon> {{ msg.replies.length }}
            </button>
            <button v-if="canDeleteMessage(msg)" type="button" class="msg-action msg-action--danger" @click="handleDelete(msg)">
              <el-icon><Delete /></el-icon> 删除
            </button>
          </div>

          <!-- 回复区域 -->
          <div v-if="msg.showReplies" class="reply-section">
            <div v-for="reply in msg.replies" :key="reply.id" class="reply-item">
              <el-avatar :size="28" class="reply-avatar">{{ reply.author.charAt(0) }}</el-avatar>
              <div class="reply-body">
                <span class="reply-author">{{ reply.author }}</span>
                <span class="reply-content">{{ reply.content }}</span>
                <span class="reply-time">{{ reply.time }}</span>
              </div>
            </div>
            <div class="reply-input">
              <el-input v-model="msg.replyInput" placeholder="回复此留言..." size="small" @keyup.enter="handleReply(msg)">
                <template #append>
                  <el-button @click="handleReply(msg)">回复</el-button>
                </template>
              </el-input>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="messages.length > 0 && messages.length < total" class="load-more">
      <el-button text type="primary" :loading="loading" @click="loadMore">加载更多</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useUserStore } from '../../stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const userStore = useUserStore()

/** 教师/管理员可删任意帖；学生等仅可删除本人发布的留言 */
const canDeleteMessage = (msg) => {
  if (['admin', 'teacher'].includes(userStore.role)) return true
  const uid = userStore.userInfo?.userId
  if (uid === undefined || uid === null || uid === '') return false
  const mid = msg.userId
  if (mid === undefined || mid === null) return false
  return Number(uid) === Number(mid)
}
const newMessage = ref('')
const searchKeyword = ref('')
const page = ref(1)
const total = ref(0)
const loading = ref(false)

const avatarColors = ['#2a5298', '#22766a', '#6b3fa0', '#a03a5f', '#8a7a2a', '#1a7a5c']

const messages = ref([])

const listParams = () => {
  const params = { page: page.value, size: 20 }
  const kw = searchKeyword.value.trim()
  if (kw) params.keyword = kw
  return params
}

const mapMessageList = (list) =>
  (list || []).map((m) => ({ ...m, showReplies: false, replyInput: '' }))

const loadMessages = async () => {
  loading.value = true
  try {
    const res = await request.get('/message/list', { params: listParams() })
    messages.value = mapMessageList(res.list)
    total.value = res.total ?? messages.value.length
  } catch {
    messages.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadMessages()
}

const handleClearSearch = () => {
  searchKeyword.value = ''
  page.value = 1
  loadMessages()
}

let searchDebounceTimer = null
watch(searchKeyword, () => {
  clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    page.value = 1
    loadMessages()
  }, 350)
})

onMounted(() => { loadMessages() })

const handlePost = async () => {
  if (!newMessage.value.trim()) return
  await request.post('/message', { content: newMessage.value })
  newMessage.value = ''
  ElMessage.success('发布成功')
  page.value = 1
  loadMessages()
}

const toggleLike = async (msg) => {
  if (msg.liked) {
    await request.delete(`/message/${msg.id}/like`)
    msg.liked = false
    msg.likes = (msg.likes || 1) - 1
  } else {
    await request.post(`/message/${msg.id}/like`)
    msg.liked = true
    msg.likes = (msg.likes || 0) + 1
  }
}

const toggleReply = (msg) => { msg.showReplies = !msg.showReplies }

const handleReply = async (msg) => {
  if (!msg.replyInput?.trim()) return
  await request.post(`/message/${msg.id}/reply`, { content: msg.replyInput })
  msg.replyInput = ''
  loadMessages()
}

const loadMore = async () => {
  page.value++
  try {
    const res = await request.get('/message/list', { params: listParams() })
    const list = mapMessageList(res.list)
    if (list.length === 0) {
      ElMessage.info('没有更多留言了')
      page.value--
    } else {
      messages.value.push(...list)
    }
  } catch {
    page.value--
  }
}

const handleDelete = async (msg) => {
  try {
    await ElMessageBox.confirm('确定删除这条留言及其全部回复吗？', '删除留言', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/message/${msg.id}`)
    messages.value = messages.value.filter((m) => m.id !== msg.id)
    total.value = Math.max(0, total.value - 1)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.page-container { animation: pageEnter 0.5s var(--ease-out); max-width: 800px; }
@keyframes pageEnter { from { opacity: 0; transform: translateY(8px); } }
.page-header { margin-bottom: 24px; }
.page-title { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 4px; }
.page-subtitle { color: var(--text-secondary); font-size: 14px; }

.post-card { background: var(--bg-card); border-color: var(--border); margin-bottom: 24px; }
.post-input-area { display: flex; gap: 14px; }
.post-avatar { background: var(--accent); color: var(--primary); font-weight: 700; flex-shrink: 0; }
.post-input-wrap { flex: 1; }
.post-actions { display: flex; justify-content: flex-end; align-items: center; margin-top: 10px; }

.search-card { background: var(--bg-card); border-color: var(--border); margin-bottom: 16px; }
.search-row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.search-input { flex: 1; min-width: 200px; }
.search-hint { margin: 10px 0 0; font-size: 13px; color: var(--text-secondary); }
.search-hint strong { color: var(--text-primary); }

.list-empty {
  text-align: center;
  padding: 48px 16px;
  color: var(--text-muted);
  font-size: 14px;
  background: var(--bg-card);
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
}

.message-list { display: flex; flex-direction: column; gap: 16px; min-height: 80px; }
.msg-card {
  display: flex; gap: 14px; background: var(--bg-card); border: 1px solid var(--border);
  border-radius: var(--radius-md); padding: 18px; transition: border-color 0.2s;
  animation: cardSlideUp 0.4s var(--ease-out) both;
}
.msg-card:hover { border-color: var(--border-light); }
@keyframes cardSlideUp { from { opacity: 0; transform: translateY(12px); } }

.msg-avatar { flex-shrink: 0; color: #fff; font-weight: 600; }
.msg-card__body { flex: 1; min-width: 0; }
.msg-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.msg-author { font-weight: 600; color: var(--text-primary); font-size: 14px; }
.msg-time { margin-left: auto; font-size: 12px; color: var(--text-muted); }
.msg-content { color: var(--text-secondary); font-size: 14px; line-height: 1.7; margin-bottom: 12px; }

.msg-footer { display: flex; gap: 16px; }
.msg-action {
  display: flex; align-items: center; gap: 4px; background: none; border: none;
  color: var(--text-muted); cursor: pointer; font-size: 13px; padding: 4px 8px;
  border-radius: var(--radius-sm); transition: all 0.2s;
}
.msg-action:hover { background: var(--bg-hover); color: var(--text-secondary); }
.msg-action.liked { color: var(--accent); }
.msg-action--danger { margin-left: auto; color: var(--danger, #f56c6c); }
.msg-action--danger:hover { color: #f78989; background: rgba(245, 108, 108, 0.08); }

.reply-section { margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }
.reply-item { display: flex; gap: 10px; margin-bottom: 12px; }
.reply-avatar { flex-shrink: 0; background: var(--bg-hover); color: var(--text-secondary); font-size: 12px; }
.reply-body { font-size: 13px; line-height: 1.6; }
.reply-author { font-weight: 600; color: var(--text-primary); margin-right: 6px; }
.reply-content { color: var(--text-secondary); }
.reply-time { display: block; color: var(--text-muted); font-size: 12px; margin-top: 2px; }
.reply-input { margin-top: 10px; }

.load-more { text-align: center; margin-top: 20px; }
</style>
