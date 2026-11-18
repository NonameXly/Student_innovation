<template>
  <el-dialog
    :model-value="modelValue"
    title="上传项目材料"
    width="520px"
    destroy-on-close
    class="project-upload-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <p class="dialog-hint">请填写项目信息并选择附件（队长）。支持 pdf、zip、rar、office 文档、7z。</p>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
      <el-form-item label="项目名称" prop="projectName">
        <el-input v-model="form.projectName" maxlength="200" show-word-limit placeholder="请输入项目名称" />
      </el-form-item>
      <el-form-item label="项目描述" prop="projectDescription">
        <el-input
          v-model="form.projectDescription"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="可选，简要说明项目内容"
        />
      </el-form-item>
      <el-form-item label="附件" prop="fileList">
        <el-upload
          ref="uploadRef"
          v-model:file-list="form.fileList"
          drag
          :limit="1"
          :auto-upload="false"
          accept=".pdf,.zip,.rar,.doc,.docx,.ppt,.pptx,.7z"
          :on-change="touchFileListRule"
          :on-remove="touchFileListRule"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '../utils/request'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  competitionId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref(null)
const uploadRef = ref(null)
const submitting = ref(false)

const form = reactive({
  projectName: '',
  projectDescription: '',
  fileList: []
})

const rules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  fileList: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: '请选择要上传的附件',
      trigger: 'change'
    }
  ]
}

function reset() {
  form.projectName = ''
  form.projectDescription = ''
  form.fileList = []
  uploadRef.value?.clearFiles()
  formRef.value?.clearValidate()
}

function handleClosed() {
  reset()
}

function close() {
  emit('update:modelValue', false)
}

function touchFileListRule() {
  formRef.value?.validateField('fileList').catch(() => {})
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reset()
    }
  }
)

async function submit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  const raw = form.fileList[0]?.raw
  if (!raw) {
    ElMessage.warning('请选择要上传的附件')
    return
  }
  if (!props.competitionId) {
    ElMessage.error('缺少竞赛信息')
    return
  }
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('competitionId', String(props.competitionId))
    fd.append('projectName', form.projectName.trim())
    fd.append('projectDescription', form.projectDescription.trim())
    fd.append('file', raw)
    await request.post('/apply/project-upload', fd, { timeout: 120000 })
    ElMessage.success('上传成功')
    emit('update:modelValue', false)
    emit('success')
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.dialog-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 0 16px;
  line-height: 1.5;
}
.upload-icon {
  font-size: 42px;
  color: var(--text-muted);
  margin-bottom: 8px;
}
</style>
