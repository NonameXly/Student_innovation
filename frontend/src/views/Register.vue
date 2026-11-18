<template>
  <div class="register-page" :style="{ backgroundImage: `url(${loginBg})` }">
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

    <div class="particles">
      <span v-for="n in 20" :key="n" class="particle" :style="particleStyle(n)"></span>
    </div>

    <div class="register-container">
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-badge">
            <el-icon :size="28"><Trophy /></el-icon>
          </div>
          <h1 class="brand-title">加入学生科创<br />管理平台</h1>
          <p class="brand-subtitle">Create Your Innovation Account</p>
          <div class="brand-divider"></div>
          <p class="brand-desc">
            创建账号后即可参与竞赛报名、<br />
            查看评审进度并沉淀你的科创成果。
          </p>
          <div class="benefit-list">
            <div class="benefit-item">统一管理竞赛与报名记录</div>
            <div class="benefit-item">实时跟踪成绩与通知动态</div>
            <div class="benefit-item">沉淀个人项目与成长档案</div>
          </div>
        </div>
      </div>

      <div class="form-section">
        <div class="form-wrapper">
          <h2 class="form-title">创建账号</h2>
          <p class="form-hint">请填写以下信息完成注册</p>

          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="rules"
            size="large"
            @keyup.enter="handleRegister"
          >
            <el-form-item prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>

            <el-form-item prop="name">
              <el-input
                v-model="registerForm.name"
                placeholder="请输入姓名"
                :prefix-icon="Avatar"
                clearable
              />
            </el-form-item>

            <el-form-item prop="studentId">
              <el-input
                v-model="registerForm.studentId"
                placeholder="请输入学号"
                :prefix-icon="Stamp"
                clearable
                maxlength="30"
                show-word-limit
              />
            </el-form-item>

            <el-form-item prop="email">
              <el-input
                v-model="registerForm.email"
                placeholder="请输入邮箱"
                :prefix-icon="Message"
                clearable
              />
            </el-form-item>

            <el-form-item prop="college">
              <el-select
                v-model="registerForm.college"
                placeholder="请选择学校"
                :prefix-icon="OfficeBuilding"
                filterable
                clearable
                class="full-width"
                :loading="schoolLoading"
              >
                <el-option
                  v-for="item in schoolOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <el-form-item prop="agreeProtocol">
              <el-checkbox v-model="registerForm.agreeProtocol">
                我已阅读并同意服务条款与隐私政策
              </el-checkbox>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                class="register-btn"
                :loading="loading"
                @click="handleRegister"
              >
                {{ loading ? '注册中...' : '注 册' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>已有账号？</span>
            <router-link to="/login" class="login-link">去登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Message, Trophy, Avatar, OfficeBuilding, Stamp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import loginBg from '../assets/login-bg.svg'

const router = useRouter()
const registerFormRef = ref()
const loading = ref(false)
const schoolLoading = ref(false)
const schoolOptions = ref([])

const registerForm = reactive({
  username: '',
  name: '',
  studentId: '',
  email: '',
  college: '',
  password: '',
  confirmPassword: '',
  agreeProtocol: false
})

const validateConfirmPassword = (_, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const validateAgreement = (_, value, callback) => {
  if (!value) {
    callback(new Error('请先同意服务条款与隐私政策'))
    return
  }
  callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 个字符', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  studentId: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { max: 30, message: '学号最多 30 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  college: [{ required: true, message: '请选择学校', trigger: 'change' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
  agreeProtocol: [{ validator: validateAgreement, trigger: 'change' }]
}

const particleStyle = (n) => {
  const size = 2 + (n % 4) * 1.5
  const left = (n * 37 + 13) % 100
  const top = (n * 53 + 7) % 100
  const delay = (n * 0.4) % 8
  const duration = 6 + (n % 5) * 2
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${left}%`,
    top: `${top}%`,
    animationDelay: `${delay}s`,
    animationDuration: `${duration}s`
  }
}

const loadSchools = async () => {
  schoolLoading.value = true
  try {
    const res = await request.get('/school/list', { params: { page: 1, size: 200 } })
    const list = Array.isArray(res?.list) ? res.list : Array.isArray(res) ? res : []
    schoolOptions.value = list
      .map((item) => ({
        label: item.name || item.college || item.schoolName || item.label,
        value: item.name || item.college || item.schoolName || item.value
      }))
      .filter((item) => item.label && item.value)
  } catch (error) {
    if (!error?.handled) {
      ElMessage.error('学校列表加载失败，请稍后刷新重试')
    }
  } finally {
    schoolLoading.value = false
  }
}

const handleRegister = async () => {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await request.post('/auth/register', {
      username: registerForm.username,
      name: registerForm.name,
      studentId: (registerForm.studentId || '').trim(),
      email: registerForm.email,
      college: registerForm.college,
      password: registerForm.password
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    if (!error?.handled) {
      ElMessage.error('注册失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSchools()
})
</script>

<style scoped>
.register-page {
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

.bg-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(30, 58, 95, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(30, 58, 95, 0.035) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 70%);
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.bg-glow--1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(43, 95, 158, 0.12) 0%, transparent 70%);
  top: -20%;
  right: -10%;
  animation: glowFloat1 12s ease-in-out infinite;
}

.bg-glow--2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(212, 137, 28, 0.08) 0%, transparent 70%);
  bottom: -15%;
  left: -5%;
  animation: glowFloat2 15s ease-in-out infinite;
}

.bg-glow--3 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(93, 173, 226, 0.06) 0%, transparent 70%);
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
}

.shape--2 {
  width: 120px;
  height: 120px;
  bottom: 18%;
  right: 18%;
  border-radius: var(--radius-lg);
  transform: rotate(45deg);
}

.shape--3 {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  top: 55%;
  left: 6%;
}

.shape--4 {
  width: 150px;
  height: 150px;
  border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%;
  top: 15%;
  right: 25%;
  border-color: rgba(212, 137, 28, 0.06);
}

.shape--5 {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  bottom: 30%;
  left: 35%;
  background: rgba(43, 95, 158, 0.02);
  border-color: rgba(43, 95, 158, 0.05);
}

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

.register-container {
  display: flex;
  width: 1080px;
  max-width: 100%;
  min-height: 620px;
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
}

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
}

.brand-title {
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
  margin-bottom: 8px;
}

.brand-subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  letter-spacing: 2px;
  text-transform: uppercase;
}

.brand-divider {
  width: 40px;
  height: 3px;
  background: var(--accent);
  border-radius: 2px;
  margin: 24px 0;
}

.brand-desc {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.65);
  line-height: 1.8;
  margin-bottom: 24px;
}

.benefit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.benefit-item {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
}

.form-section {
  flex: 1;
  padding: 48px 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-wrapper {
  width: 100%;
  max-width: 360px;
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
  margin-bottom: 28px;
}

.register-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-md) !important;
  letter-spacing: 4px;
}

.full-width {
  width: 100%;
}

.form-footer {
  text-align: center;
  margin-top: 18px;
  font-size: 14px;
  color: var(--text-secondary);
}

.login-link {
  color: var(--accent);
  font-weight: 500;
  margin-left: 4px;
  text-decoration: none;
  cursor: pointer;
}
.login-link:hover {
  text-decoration: underline;
}

@media (max-width: 900px) {
  .register-container {
    flex-direction: column;
    min-height: auto;
  }

  .brand-section {
    padding: 32px 24px;
  }

  .brand-title {
    font-size: 28px;
  }

  .form-section {
    padding: 32px 24px;
  }
}
</style>
