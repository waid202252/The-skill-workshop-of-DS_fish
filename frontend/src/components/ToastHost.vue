<script setup lang="ts">
import { dismissToast, useToastItems } from '@/composables/toast'

const items = useToastItems()
</script>

<template>
  <div class="toast-host">
    <TransitionGroup name="toast">
      <div
        v-for="item in items"
        :key="item.id"
        class="toast"
        :class="`toast--${item.kind}`"
        role="status"
        @click="dismissToast(item.id)"
      >
        <span class="toast-dot" aria-hidden="true"></span>
        <span class="toast-text">{{ item.text }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 20px;
  left: 50%;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  transform: translateX(-50%);
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 440px;
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #fff;
  box-shadow: var(--shadow-md);
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.4;
  pointer-events: auto;
  cursor: pointer;
}

.toast-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
  flex: 0 0 auto;
}

.toast--error .toast-dot {
  background: var(--color-danger);
}

.toast--success .toast-dot {
  background: var(--color-success);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
