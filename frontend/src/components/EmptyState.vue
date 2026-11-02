<template>
  <div class="empty-state">
    <img class="empty-face" src="/faces/face-calm.png" alt="大肥鱼" />
    <p class="empty-title">选一个会话开始聊天</p>
    <p class="empty-hint">从左侧会话列表或联系人中，挑一个开始吧</p>
    <button class="empty-back" type="button" @click="goSkills">← 回工坊看看技能</button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goSkills(): void {
  void router.push('/skills')
}
</script>

<style scoped>
.empty-state {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: var(--color-chat-bg);
}

.empty-face {
  width: 132px;
  height: 132px;
  object-fit: contain;
  margin-bottom: 6px;
  filter: drop-shadow(0 10px 20px rgba(10, 124, 196, 0.16));
}

.empty-title {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: 15px;
  font-weight: 600;
}

.empty-hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.empty-back {
  margin-top: 14px;
  height: 30px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: #fff;
  color: var(--color-text-secondary);
  font-size: 12.5px;
}

.empty-back:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>
