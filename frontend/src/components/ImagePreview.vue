<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

defineProps<{ url: string }>()
const emit = defineEmits<{ close: [] }>()

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="image-preview" @click.self="emit('close')">
      <button class="preview-close" type="button" aria-label="关闭预览" @click="emit('close')">×</button>
      <img :src="url" alt="图片预览" class="preview-image" />
      <p class="preview-tip">点击空白处或按 Esc 关闭</p>
    </div>
  </Teleport>
</template>

<style scoped>
.image-preview {
  position: fixed;
  inset: 0;
  z-index: 1500;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 40px;
  background: rgba(12, 16, 22, 0.72);
  backdrop-filter: blur(2px);
  animation: fade-in 0.18s ease;
}

.preview-image {
  max-width: min(880px, 88vw);
  max-height: 78vh;
  border-radius: 8px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
  background: #fff;
  object-fit: contain;
}

.preview-close {
  position: absolute;
  top: 18px;
  right: 22px;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 20px;
  line-height: 1;
}

.preview-close:hover {
  background: rgba(255, 255, 255, 0.26);
}

.preview-tip {
  margin: 0;
  color: rgba(255, 255, 255, 0.62);
  font-size: 12px;
}
</style>
