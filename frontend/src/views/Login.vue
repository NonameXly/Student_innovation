<template>
  <div class="login-page" :style="{ backgroundImage: `url(${loginBg})` }">
    <!-- 背景装饰 -->
    <div class="bg-grid"></div>
    <div class="bg-glow bg-glow--1"></div>
    <div class="bg-glow bg-glow--2"></div>
    <div class="bg-glow bg-glow--3"></div>
    <div class="floating-shapes">
      <div class="shape shape--1"></div>
      <div class="shape shape--2"></div>
      <div class="shape shape--3"></div>
      <div class="shape shape--4"></div>
      <div class="shape shape--5"></div>
    </div>
    <!-- 粒子点阵 -->
    <div class="particles">
      <span v-for="n in 20" :key="n" class="particle" :style="particleStyle(n)"></span>
    </div>

    <div class="login-container">
      <!-- 左侧品牌区域 -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-badge">
            <el-icon :size="28"><Trophy /></el-icon>
          </div>
          <h1 class="brand-title">学生科创<br/>管理平台</h1>
          <p class="brand-subtitle">Student Innovation Management</p>
          <div class="brand-divider"></div>
          <p class="brand-desc">
            激发创新思维，培育科技英才。<br/>
            一站式管理竞赛报名、成绩评定与知识分享。
          </p>
          <div class="stats-row">
            <div class="stat-item">
              <span class="stat-number">1,200+</span>
              <span class="stat-label">参赛学生</span>
            </div>
            <div class="stat-item">
              <span class="stat-number">86</span>
              <span class="stat-label">科创竞赛</span>
            </div>
            <div class="stat-item">
              <span class="stat-number">95%</span>
              <span class="stat-label">满意度</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="form-section">
        <div class="form-wrapper">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-hint">请输入账号密码登录系统</p>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            size="large"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <div class="form-options">
              <el-checkbox v-model="rememberMe">记住我</el-checkbox>
              <a href="#" class="forgot-link">忘记密码？</a>
            </div>

            <el-form-item>
              <el-button
                type="primary"
                class="login-btn"
                :loading="loading"
                @click="handleLogin"
              >
                {{ loading ? '登录中...' : '登 录' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>还没有账号？</span>
            <router-link to="/register" class="register-link">立即注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import loginBg from '../assets/login-bg.svg'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref()
const loading = ref(false)
const rememberMe = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const particleStyle = (n) => {
  const size = 2 + (n % 4) * 1.5
  const left = ((n * 37 + 13) % 100)
  const top = ((n * 53 + 7) % 100)
  const delay = (n * 0.4) % 8
  const duration = 6 + (n % 5) * 2
  return {
    width: size + 'px',
    height: size + 'px',
    left: left + '%',
    top: top + '%',
    animationDelay: delay + 's',
    animationDuration: duration + 's'
  }
}

const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await request.post('/auth/login', loginForm)
    userStore.setUser({
      token: res.token,
      userId: res.userId,
      role: res.role,
      username: res.username,
      name: res.name
    })
    ElMessage.success('登录成功')
    const home =
      res.role === 'student' ? '/student/dashboard' : res.role === 'teacher' ? '/teacher/dashboard' : '/dashboard'
    router.push(home)
  } catch (error) {
    // 拦截器通常会提示错误，这里增加兜底，避免静默失败
    if (!error?.handled) {
      ElMessage.error('登录失败，请检查账号密码后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f0f2f5;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  position: relative;
  overflow: hidden;
  padding: 20px;
}

/* 网格背景 */
.bg-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(30,58,95,0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(30,58,95,0.035) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 70%);
}

/* 光晕效果 */
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.bg-glow--1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(43,95,158,0.12) 0%, transparent 70%);
  top: -20%;
  right: -10%;
  animation: glowFloat1 12s ease-in-out infinite;
}

.bg-glow--2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(212,137,28,0.08) 0%, transparent 70%);
  bottom: -15%;
  left: -5%;
  animation: glowFloat2 15s ease-in-out infinite;
}

.bg-glow--3 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(93,173,226,0.06) 0%, transparent 70%);
  top: 50%;
  left: 40%;
  transform: translate(-50%, -50%);
  animation: glowFloat3 18s ease-in-out infinite;
}

@keyframes glowFloat1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-30px, 20px) scale(1.1); }
  66% { transform: translate(20px, -15px) scale(0.95); }
}

@keyframes glowFloat2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(25px, -20px) scale(1.05); }
  66% { transform: translate(-15px, 25px) scale(1.1); }
}

@keyframes glowFloat3 {
  0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.6; }
  50% { transform: translate(-50%, -50%) scale(1.2); opacity: 1; }
}

/* 浮动装饰形状 */
.floating-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.shape {
  position: absolute;
  border: 1.5px solid rgba(43, 95, 158, 0.08);
}

.shape--1 {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  top: 8%;
  left: 12%;
  animation: floatShape 20s linear infinite;
}

.shape--2 {
  width: 120px;
  height: 120px;
  bottom: 18%;
  right: 18%;
  border-radius: var(--radius-lg);
  transform: rotate(45deg);
  animation: floatShape 15s linear infinite reverse;
}

.shape--3 {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  top: 55%;
  left: 6%;
  animation: floatShape 25s linear infinite;
}

.shape--4 {
  width: 150px;
  height: 150px;
  border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%;
  top: 15%;
  right: 25%;
  border-color: rgba(212, 137, 28, 0.06);
  animation: morphShape 18s ease-in-out infinite;
}

.shape--5 {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  bottom: 30%;
  left: 35%;
  background: rgba(43, 95, 158, 0.02);
  border-color: rgba(43, 95, 158, 0.05);
  animation: floatShape 22s linear infinite reverse;
}

@keyframes floatShape {
  0% { transform: translateY(0) rotate(0deg); }
  25% { transform: translateY(-20px) rotate(90deg); }
  50% { transform: translateY(0) rotate(180deg); }
  75% { transform: translateY(20px) rotate(270deg); }
  100% { transform: translateY(0) rotate(360deg); }
}

@keyframes morphShape {
  0%, 100% { border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%; transform: rotate(0deg); }
  25% { border-radius: 58% 42% 38% 62% / 47% 56% 44% 53%; transform: rotate(45deg); }
  50% { border-radius: 45% 55% 62% 38% / 53% 38% 62% 47%; transform: rotate(90deg); }
  75% { border-radius: 60% 40% 48% 52% / 38% 62% 38% 62%; transform: rotate(135deg); }
}

/* 粒子动画 */
.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  background: rgba(43, 95, 158, 0.15);
  border-radius: 50%;
  animation: particleFloat linear infinite;
}

@keyframes particleFloat {
  0%, 100% { transform: translateY(0) scale(1); opacity: 0.3; }
  25% { transform: translateY(-15px) scale(1.2); opacity: 0.6; }
  50% { transform: translateY(-5px) scale(0.8); opacity: 0.2; }
  75% { transform: translateY(-20px) scale(1.1); opacity: 0.5; }
}

/* 登录容器 */
.login-container {
  display: flex;
  width: 980px;
  max-width: 100%;
  min-height: 580px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(1.2);
  -webkit-backdrop-filter: blur(20px) saturate(1.2);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow:
    0 8px 32px rgba(30, 58, 95, 0.08),
    0 2px 8px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.6);
  position: relative;
  z-index: 1;
  animation: containerReveal 0.8s var(--ease-out) both;
}

@keyframes containerReveal {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.97);
  }
}

/* 左侧品牌区域 */
.brand-section {
  flex: 1;
  background:
    linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 50%, #3d7ec7 100%);
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.brand-section::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 80%, rgba(232,168,56,0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(93,173,226,0.1) 0%, transparent 50%);
}

.brand-section::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.03'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
  opacity: 0.8;
}

.brand-content {
  position: relative;
  z-index: 1;
}

.brand-badge {
  width: 56px;
  height: 56px;
  background: var(--accent);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  margin-bottom: 24px;
  box-shadow: 0 4px 16px rgba(232, 168, 56, 0.3);
  animation: badgeBounce 0.6s var(--ease-spring) 0.3s both;
}

@keyframes badgeBounce {
  from { opacity: 0; transform: scale(0.5) rotate(-10deg); }
}

.brand-title {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
  margin-bottom: 8px;
  animation: slideUp 0.6s var(--ease-out) 0.4s both;
}

.brand-subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  letter-spacing: 2px;
  text-transform: uppercase;
  animation: slideUp 0.6s var(--ease-out) 0.5s both;
}

.brand-divider {
  width: 40px;
  height: 3px;
  background: var(--accent);
  border-radius: 2px;
  margin: 24px 0;
  animation: slideUp 0.6s var(--ease-out) 0.55s both;
}

.brand-desc {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.65);
  line-height: 1.8;
  margin-bottom: 32px;
  animation: slideUp 0.6s var(--ease-out) 0.6s both;
}

.stats-row {
  display: flex;
  gap: 24px;
  animation: slideUp 0.6s var(--ease-out) 0.7s both;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-number {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: var(--accent);
}

.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 2px;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(16px); }
}

/* 右侧表单区域 */
.form-section {
  flex: 1;
  padding: 48px 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-wrapper {
  width: 100%;
  max-width: 340px;
  animation: formReveal 0.7s var(--ease-out) 0.5s both;
}

@keyframes formReveal {
  from { opacity: 0; transform: translateX(20px); }
}

.form-title {
  font-family: var(--font-display);
  font-size: 28px;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.form-hint {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 32px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.forgot-link {
  font-size: 13px;
  color: var(--text-secondary);
}

.forgot-link:hover {
  color: var(--accent);
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-md) !important;
  letter-spacing: 4px;
  transition: all 0.3s var(--ease-out);
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(43, 95, 158, 0.25);
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--text-secondary);
}

.register-link {
  color: var(--accent);
  font-weight: 500;
  margin-left: 4px;
  text-decoration: none;
  cursor: pointer;
}
.register-link:hover {
  text-decoration: underline;
}

/* 响应式 */
@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
    min-height: auto;
  }

  .brand-section {
    padding: 32px 24px;
  }

  .brand-title {
    font-size: 28px;
  }

  .stats-row {
    display: none;
  }

  .form-section {
    padding: 32px 24px;
  }
}
</style>
