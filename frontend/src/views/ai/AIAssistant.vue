<template>
  <div class="ai-page">
    <div class="chat-container">
      <!-- 侧边栏 - 对话列表 -->
      <aside class="chat-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-header">
          <h3 v-if="!sidebarCollapsed">AI 助手</h3>
          <el-button text circle @click="sidebarCollapsed = !sidebarCollapsed">
            <el-icon><Fold v-if="!sidebarCollapsed" /><Expand v-else /></el-icon>
          </el-button>
        </div>
        <el-button class="new-chat-btn" type="primary" @click="handleNewChat" v-if="!sidebarCollapsed">
          <el-icon><Plus /></el-icon>新对话
        </el-button>
        <div class="chat-list" v-if="!sidebarCollapsed">
          <div
            v-for="c in chatList"
            :key="c.id"
            class="chat-item"
            :class="{ active: Number(currentChat) === Number(c.id) }"
            @click="selectChat(c.id)"
          >
            <el-icon size="14"><ChatDotRound /></el-icon>
            <span class="chat-title">{{ c.title }}</span>
            <span class="chat-time">{{ c.time }}</span>
          </div>
        </div>
        <div class="sidebar-footer" v-if="!sidebarCollapsed">
          <div class="model-info">
            <el-icon><Cpu /></el-icon>
            <span>Dify 智能助手</span>
          </div>
        </div>
      </aside>

      <!-- 主聊天区 -->
      <main class="chat-main">
        <!-- 空状态 / 加载历史 -->
        <div class="chat-welcome" v-if="!currentChat && messages.length === 0 && !historyLoading">
          <div class="welcome-icon">
            <el-icon :size="48"><MagicStick /></el-icon>
          </div>
          <h2>科创智能助手</h2>
          <p>我可以帮你进行数据分析、竞赛咨询、答疑解惑</p>
        </div>

        <div class="chat-history-loading" v-else-if="historyLoading">
          <el-icon class="loading-spin" :size="36"><Loading /></el-icon>
          <span>加载对话记录...</span>
        </div>

        <!-- 消息列表 -->
        <div class="messages-area" ref="messagesRef" v-else-if="messages.length > 0">
          <div v-for="msg in messages" :key="msg.id ?? `local-${msg.time}-${msg.role}`" class="message-row" :class="msg.role">
            <div class="message-avatar">
              <el-avatar :size="32" v-if="msg.role === 'assistant'" :style="{ background: 'var(--accent)' }">
                <el-icon><MagicStick /></el-icon>
              </el-avatar>
              <el-avatar :size="32" v-else :style="{ background: 'var(--primary)' }">我</el-avatar>
            </div>
            <div class="message-body">
              <div class="message-bubble">{{ msg.content }}</div>
              <div class="message-meta">{{ msg.time }}</div>
            </div>
          </div>
          <div v-if="loading" class="message-row assistant">
            <div class="message-avatar">
              <el-avatar :size="32" :style="{ background: 'var(--accent)' }"><el-icon><MagicStick /></el-icon></el-avatar>
            </div>
            <div class="message-body">
              <div class="message-bubble typing">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-empty-thread" v-else>
          <p class="empty-thread-text">该对话暂无消息，在下方输入即可开始</p>
        </div>

        <!-- 输入区 -->
        <div class="chat-input-area">
          <div class="input-wrapper">
            <el-input
              v-model="inputText"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 5 }"
              placeholder="输入你的问题..."
              @keydown.enter.exact.prevent="handleSend"
              resize="none"
            />
            <el-button class="send-btn" type="primary" circle :disabled="!inputText.trim() || loading" @click="handleSend">
              <el-icon><Promotion /></el-icon>
            </el-button>
          </div>
          <div class="input-hint">按 Enter 发送，Shift+Enter 换行</div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import request from '../../utils/request'

const sidebarCollapsed = ref(false)
const currentChat = ref(null)
const inputText = ref('')
const loading = ref(false)
const historyLoading = ref(false)
const messagesRef = ref()

const chatList = ref([])

const messages = ref([])

const now = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}

const loadChatList = async () => {
  const res = await request.get('/ai/chat-list')
  chatList.value = res.list || []
}

const selectChat = async (id) => {
  const nid = Number(id)
  if (!Number.isFinite(nid)) return
  if (Number(currentChat.value) === nid && messages.value.length > 0 && !historyLoading.value) {
    return
  }
  currentChat.value = nid
  historyLoading.value = true
  messages.value = []
  try {
    const res = await request.get(`/ai/chat/${nid}/messages`)
    messages.value = (res.list || []).map((m) => ({
      id: m.id != null ? Number(m.id) : undefined,
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content != null ? String(m.content) : '',
      time: m.time != null ? String(m.time) : ''
    }))
  } catch {
    messages.value = []
  } finally {
    historyLoading.value = false
    await scrollToBottom()
  }
}

onMounted(() => {
  loadChatList()
})

const handleSend = async () => {
  if (!inputText.value.trim() || loading.value) return
  const text = inputText.value.trim()
    messages.value.push({
      id: undefined,
      role: 'user',
      content: text,
      time: now()
    })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  try {
    // 如果没有当前对话，先创建一个
    if (!currentChat.value) {
      const chatRes = await request.post('/ai/chat', { title: text.slice(0, 20) })
      currentChat.value = chatRes.id || chatRes
      await loadChatList()
    }
    const res = await request.post('/ai/chat/message', { chatId: currentChat.value, message: text })
    messages.value.push({
      id: undefined,
      role: 'assistant',
      content: res.response || '',
      time: now()
    })
  } catch (e) {
    messages.value.push({
      id: undefined,
      role: 'assistant',
      content: '<p>抱歉，AI 服务暂时不可用，请稍后再试。</p>',
      time: now()
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const handleNewChat = () => {
  currentChat.value = null
  messages.value = []
  historyLoading.value = false
}
</script>

<style scoped>
.ai-page { height: calc(100vh - 64px); display: flex; }
.chat-container { display: flex; width: 100%; height: 100%; }

/* 侧边栏 */
.chat-sidebar {
  width: 260px; min-width: 260px;
  background: var(--bg-card); border-right: 1px solid var(--border);
  display: flex; flex-direction: column; transition: all 0.3s var(--ease-out);
}
.chat-sidebar.collapsed { width: 52px; min-width: 52px; }
.sidebar-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px; border-bottom: 1px solid var(--border);
}
.sidebar-header h3 { font-family: var(--font-display); font-size: 16px; color: var(--text-primary); margin: 0; }
.new-chat-btn { margin: 12px 16px; }
.chat-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.chat-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px; border-radius: var(--radius-md);
  cursor: pointer; transition: background 0.2s; margin-bottom: 2px;
  color: var(--text-secondary); font-size: 13px;
}
.chat-item:hover { background: var(--bg-hover); }
.chat-item.active { background: rgba(43, 95, 158, 0.08); color: var(--primary-light); }
.chat-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-time { font-size: 11px; color: var(--text-muted); flex-shrink: 0; }
.sidebar-footer { padding: 12px 16px; border-top: 1px solid var(--border); }
.model-info { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-muted); }

/* 主聊天区 */
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

/* 欢迎页 */
.chat-welcome {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; animation: welcomeIn 0.6s var(--ease-out);
}
@keyframes welcomeIn { from { opacity: 0; transform: translateY(20px); } }
.welcome-icon {
  width: 80px; height: 80px; border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), #f59e0b);
  display: flex; align-items: center; justify-content: center; color: #fff;
  margin-bottom: 20px; box-shadow: 0 8px 32px rgba(43, 95, 158, 0.2);
}
.chat-welcome h2 { font-family: var(--font-display); font-size: 28px; color: var(--text-primary); margin-bottom: 8px; }
.chat-welcome p { color: var(--text-secondary); margin-bottom: 0; }

.chat-history-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 14px;
}

.loading-spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.chat-empty-thread {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.empty-thread-text {
  margin: 0;
  font-size: 14px;
  color: var(--text-muted);
}

/* 消息区 */
.messages-area { flex: 1; overflow-y: auto; padding: 24px; }
.message-row { display: flex; gap: 12px; margin-bottom: 20px; animation: msgIn 0.3s var(--ease-out); }
@keyframes msgIn { from { opacity: 0; transform: translateY(8px); } }
.message-row.user { flex-direction: row-reverse; }
.message-avatar { flex-shrink: 0; }
.message-body { max-width: 70%; }
.message-bubble {
  padding: 12px 16px; border-radius: 12px; font-size: 14px; line-height: 1.7;
  background: var(--bg-card); border: 1px solid var(--border); color: var(--text-primary);
}
.message-row.user .message-bubble {
  background: var(--primary-light); border-color: transparent; color: #ffffff;
}
.message-meta { font-size: 11px; color: var(--text-muted); margin-top: 4px; padding: 0 4px; }
.message-row.user .message-meta { text-align: right; }

/* 打字动画 */
.typing { display: flex; gap: 4px; padding: 16px 20px; }
.dot {
  width: 8px; height: 8px; border-radius: 50%; background: var(--text-muted);
  animation: bounce 1.4s infinite ease-in-out;
}
.dot:nth-child(2) { animation-delay: 0.16s; }
.dot:nth-child(3) { animation-delay: 0.32s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1); } }

/* 输入区 */
.chat-input-area { padding: 16px 24px 20px; border-top: 1px solid var(--border); background: var(--bg-main); }
.input-wrapper { display: flex; align-items: flex-end; gap: 10px; }
.input-wrapper :deep(.el-textarea__inner) {
  background: var(--bg-card); border-color: var(--border); border-radius: 12px;
  padding: 10px 16px; font-size: 14px; color: var(--text-primary);
  transition: border-color 0.3s;
}
.input-wrapper :deep(.el-textarea__inner:focus) { border-color: var(--accent); }
.send-btn { width: 40px; height: 40px; flex-shrink: 0; }
.input-hint { text-align: center; font-size: 11px; color: var(--text-muted); margin-top: 6px; }
</style>
