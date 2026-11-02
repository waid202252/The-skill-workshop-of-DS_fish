<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { fileApi } from '@/services/api'
import type { AttachmentPayload } from '@/types/ui'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'

const props = withDefaults(
  defineProps<{
    disabled?: boolean
  }>(),
  { disabled: false },
)

const emit = defineEmits<{
  send: [text: string]
  attachment: [payload: AttachmentPayload]
}>()

/** 服务端限制：单文件 20MB */
const MAX_FILE_SIZE = 20 * 1024 * 1024
const ALLOWED_ACCEPT = '.png,.jpg,.jpeg,.gif,.webp,.bmp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.zip,.rar,.7z,.mp3,.mp4'

const text = ref('')
const uploadingCount = ref(0)
const dragging = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
let dragDepth = 0

function resizeTextarea(): void {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 132)}px`
}

function focusTextarea(): void {
  textareaRef.value?.focus()
}

function submit(): void {
  if (props.disabled) return
  const content = text.value.trim()
  if (!content) {
    focusTextarea()
    return
  }
  emit('send', content)
  text.value = ''
  void nextTick(() => {
    resizeTextarea()
    focusTextarea()
  })
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter') return
  if (event.shiftKey || event.isComposing) return
  event.preventDefault()
  submit()
}

async function handleFiles(files: File[], forceImage: boolean): Promise<void> {
  for (const file of files) {
    if (file.size > MAX_FILE_SIZE) {
      showToast(`「${file.name}」超过 20MB，无法发送`, 'error')
      continue
    }
    const isImage = forceImage || file.type.startsWith('image/')
    uploadingCount.value += 1
    try {
      const result = await fileApi.upload(file)
      emit('attachment', {
        type: isImage ? 'IMAGE' : 'FILE',
        content: result.url,
        fileName: result.name,
        fileSize: result.size,
      })
    } catch (error) {
      showToast(`上传失败：${errorMessage(error)}`, 'error')
    } finally {
      uploadingCount.value -= 1
    }
  }
}

function openImagePicker(): void {
  if (props.disabled) return
  imageInputRef.value?.click()
}

function openFilePicker(): void {
  if (props.disabled) return
  fileInputRef.value?.click()
}

function handleInputChange(event: Event, forceImage: boolean): void {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (files.length > 0) void handleFiles(files, forceImage)
}

function handlePaste(event: ClipboardEvent): void {
  if (props.disabled) return
  const items = event.clipboardData?.items
  if (!items) return
  const files: File[] = []
  for (const item of Array.from(items)) {
    if (item.kind !== 'file') continue
    const file = item.getAsFile()
    if (file) files.push(file)
  }
  if (files.length === 0) return
  event.preventDefault()
  void handleFiles(files, false)
}

function handleDragEnter(event: DragEvent): void {
  event.preventDefault()
  dragDepth += 1
  dragging.value = true
}

function handleDragOver(event: DragEvent): void {
  event.preventDefault()
}

function handleDragLeave(event: DragEvent): void {
  event.preventDefault()
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragging.value = false
}

function handleDrop(event: DragEvent): void {
  event.preventDefault()
  dragDepth = 0
  dragging.value = false
  if (props.disabled) return
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return
  void handleFiles(Array.from(files), false)
}

function handleInput(): void {
  resizeTextarea()
}
</script>

<template>
  <div
    class="chat-input"
    :class="{ 'chat-input--dragging': dragging }"
    @dragenter="handleDragEnter"
    @dragover="handleDragOver"
    @dragleave="handleDragLeave"
    @drop="handleDrop"
  >
    <div class="input-toolbar">
      <button class="tool-button" type="button" title="发送图片" :disabled="disabled" @click="openImagePicker">
        <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.7">
          <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
          <circle cx="9" cy="10" r="1.6" />
          <path d="m5 17 4.4-4.2a1.6 1.6 0 0 1 2.2 0l2.2 2.1 2-1.8a1.6 1.6 0 0 1 2.1 0L20.5 16" stroke-linecap="round" />
        </svg>
      </button>
      <button class="tool-button" type="button" title="发送文件" :disabled="disabled" @click="openFilePicker">
        <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.7">
          <path
            d="M20 11.5 12.3 19a4.6 4.6 0 0 1-6.5-6.5l7.8-7.7a3.1 3.1 0 0 1 4.4 4.4l-7.8 7.7a1.6 1.6 0 0 1-2.2-2.2l7.1-7"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <span v-if="uploadingCount > 0" class="uploading">
        <span class="uploading-spinner"></span>
        上传中{{ uploadingCount > 1 ? ` (${uploadingCount})` : '' }}
      </span>
      <span class="toolbar-tip">支持粘贴截图 / 拖拽文件</span>
    </div>

    <textarea
      ref="textareaRef"
      v-model="text"
      class="input-textarea"
      rows="1"
      :disabled="disabled"
      placeholder="输入消息，Enter 发送，Shift+Enter 换行"
      @keydown="handleKeydown"
      @input="handleInput"
      @paste="handlePaste"
    ></textarea>

    <div class="input-footer">
      <span class="input-hint">Enter 发送 / Shift+Enter 换行</span>
      <button class="send-button" type="button" :disabled="disabled || !text.trim()" @click="submit">
        发送
      </button>
    </div>

    <input
      ref="imageInputRef"
      class="hidden-input"
      type="file"
      accept="image/*"
      multiple
      @change="handleInputChange($event, true)"
    />
    <input
      ref="fileInputRef"
      class="hidden-input"
      type="file"
      :accept="ALLOWED_ACCEPT"
      multiple
      @change="handleInputChange($event, false)"
    />

    <div v-if="dragging" class="drop-overlay">
      <div class="drop-card">松开鼠标，发送文件</div>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  position: relative;
  flex: 0 0 auto;
  padding: 10px 18px 12px;
  border-top: 1px solid var(--color-border);
  background: var(--color-panel);
}

.chat-input--dragging {
  outline: 2px dashed var(--color-primary);
  outline-offset: -5px;
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 28px;
}

.tool-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.tool-button:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.05);
  color: var(--color-text-secondary);
}

.tool-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.uploading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 4px;
  color: var(--color-primary);
  font-size: 11.5px;
}

.uploading-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(18, 183, 245, 0.25);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.toolbar-tip {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 11px;
}

.input-textarea {
  display: block;
  width: 100%;
  min-height: 52px;
  max-height: 132px;
  margin-top: 4px;
  padding: 0;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--color-text);
  font-size: 13.5px;
  line-height: 1.6;
  overflow-y: auto;
}

.input-textarea::placeholder {
  color: var(--color-text-muted);
}

.input-textarea:disabled {
  cursor: not-allowed;
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.input-hint {
  color: var(--color-text-muted);
  font-size: 11px;
}

.send-button {
  height: 32px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary-strong);
  color: #fff;
  font-size: 12.5px;
  font-weight: var(--weight-semibold);
  transition:
    background var(--transition),
    opacity var(--transition);
}

.send-button:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.send-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.hidden-input {
  display: none;
}

.drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.82);
  pointer-events: none;
}

.drop-card {
  padding: 10px 18px;
  border: 1px dashed var(--color-primary);
  border-radius: 8px;
  color: var(--color-primary);
  font-size: 13px;
}
</style>
