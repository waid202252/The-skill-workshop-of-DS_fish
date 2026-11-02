<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { fileApi, userApi } from '@/services/api'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import UserAvatar from './UserAvatar.vue'

const emit = defineEmits<{
  close: []
}>()

const auth = useAuthStore()

const nickname = ref(auth.user?.nickname ?? '')
const signature = ref(auth.user?.signature ?? '')
const avatar = ref<string | null>(auth.user?.avatar ?? null)
const uploading = ref(false)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const nicknameError = computed(() => {
  const value = nickname.value.trim()
  if (value.length === 0) return '昵称不能为空'
  if (value.length > 20) return '昵称最多 20 个字符'
  return ''
})

const signatureError = computed(() => (signature.value.length > 128 ? '签名最多 128 个字符' : ''))

const dirty = computed(
  () =>
    nickname.value.trim() !== (auth.user?.nickname ?? '') ||
    (signature.value.trim() || '') !== (auth.user?.signature ?? '') ||
    avatar.value !== (auth.user?.avatar ?? null),
)

const canSubmit = computed(
  () => !submitting.value && !uploading.value && !nicknameError.value && !signatureError.value && dirty.value,
)

function pickAvatar(): void {
  fileInputRef.value?.click()
}

async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showToast('头像需要是图片文件', 'error')
    return
  }
  uploading.value = true
  try {
    const result = await fileApi.upload(file)
    avatar.value = result.url
  } catch (error) {
    showToast(`头像上传失败：${errorMessage(error)}`, 'error')
  } finally {
    uploading.value = false
  }
}

function clearAvatar(): void {
  avatar.value = null
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const updated = await userApi.updateMe({
      nickname: nickname.value.trim(),
      signature: signature.value.trim(),
      // 契约里 avatar 是可选字符串；传空串表示清除头像（后端会落成 null）
      avatar: avatar.value ?? '',
    })
    auth.user = updated
    showToast('资料已更新', 'success')
    emit('close')
  } catch (error) {
    showToast(errorMessage(error), 'error')
  } finally {
    submitting.value = false
  }
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="modal-mask" @click.self="emit('close')">
      <div class="modal-card">
        <header class="modal-header">
          <h2>编辑资料</h2>
          <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="modal-body">
          <div class="avatar-row">
            <button class="avatar-picker" type="button" :disabled="uploading" @click="pickAvatar">
              <UserAvatar :src="avatar" :name="nickname || '我'" :size="64" />
              <span class="avatar-mask">{{ uploading ? '上传中…' : '更换头像' }}</span>
            </button>
            <div class="avatar-tips">
              <p class="tip-main">点击头像上传新图片</p>
              <p class="tip-sub">支持 png / jpg / gif / webp，单张不超过 20MB</p>
              <button v-if="avatar" class="link-btn" type="button" @click="clearAvatar">移除头像</button>
            </div>
            <input ref="fileInputRef" class="hidden-input" type="file" accept="image/*" @change="onAvatarChange" />
          </div>

          <label class="field-label" for="profile-nickname">昵称</label>
          <input
            id="profile-nickname"
            v-model="nickname"
            class="text-field"
            type="text"
            maxlength="20"
            placeholder="1-20 个字符"
          />
          <p v-if="nicknameError" class="field-error">{{ nicknameError }}</p>

          <label class="field-label field-label--gap" for="profile-signature">个性签名</label>
          <textarea
            id="profile-signature"
            v-model="signature"
            class="text-area"
            rows="3"
            maxlength="128"
            placeholder="介绍一下自己（最多 128 字）"
          ></textarea>
          <p class="field-counter">{{ signature.length }}/128</p>
          <p v-if="signatureError" class="field-error">{{ signatureError }}</p>

          <p class="account-line">账号：<span>@{{ auth.user?.username }}</span>（账号名创建后不可修改）</p>
        </div>

        <footer class="modal-footer">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button class="btn btn-primary" type="button" :disabled="!canSubmit" @click="submit">
            {{ submitting ? '保存中…' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(17, 24, 39, 0.42);
  animation: fade-in 0.16s ease;
}

.modal-card {
  display: flex;
  flex-direction: column;
  width: 440px;
  max-height: min(620px, 86vh);
  border-radius: var(--radius-lg);
  background: #fff;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h2 {
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
}

.modal-close {
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 20px;
  line-height: 1;
}

.modal-close:hover {
  background: rgba(0, 0, 0, 0.05);
}

.modal-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}

.avatar-picker {
  position: relative;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  line-height: 0;
  overflow: hidden;
  flex: 0 0 auto;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(17, 24, 39, 0.55);
  color: #fff;
  font-size: 11px;
  line-height: 1.2;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.avatar-picker:hover .avatar-mask,
.avatar-picker:disabled .avatar-mask {
  opacity: 1;
}

.avatar-tips {
  min-width: 0;
}

.tip-main {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12.5px;
}

.tip-sub {
  margin: 3px 0 0;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.link-btn {
  margin-top: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: 12px;
}

.hidden-input {
  display: none;
}

.field-label {
  display: block;
  margin-bottom: 7px;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  font-weight: 500;
}

.field-label--gap {
  margin-top: 16px;
}

.text-field,
.text-area {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  outline: none;
  background: var(--color-side);
  color: var(--color-text);
  font-size: 13px;
  font-family: inherit;
}

.text-field {
  height: 34px;
}

.text-area {
  resize: vertical;
  line-height: 1.5;
}

.text-field:focus,
.text-area:focus {
  border-color: var(--color-primary);
  background: #fff;
}

.field-error {
  margin: 5px 0 0;
  color: var(--color-danger);
  font-size: 11.5px;
}

.field-counter {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 11px;
  text-align: right;
}

.account-line {
  margin: 18px 0 0;
  padding-top: 14px;
  border-top: 1px dashed var(--color-border);
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.account-line span {
  color: var(--color-text-secondary);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}

.modal-footer .btn-primary {
  min-width: 92px;
}
</style>
