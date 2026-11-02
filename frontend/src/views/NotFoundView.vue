<script setup lang="ts">
/**
 * 404 页。
 *
 * 之前兜底路由是 `pathMatch(.*)* → /discover`，输入错误地址会被悄悄重定向，
 * 用户不知道是自己打错了还是系统出了问题。现在明确告诉对方"这个地址不存在"，
 * 并给两个确定性出口。
 */
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome(): void {
  void router.push('/discover')
}

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    goHome()
  }
}
</script>

<template>
  <div class="notfound-page">
    <div class="notfound-card">
      <img class="notfound-face" src="/faces/face-grumpy.png" alt="大肥鱼" />
      <p class="notfound-code">404</p>
      <h1>这个地址没捞到东西</h1>
      <p class="notfound-sub">页面可能已经被移走，或者链接打错了一个字。</p>
      <div class="notfound-actions">
        <button class="btn-primary" type="button" @click="goHome">回工坊首页</button>
        <button class="btn-ghost" type="button" @click="goBack">返回上一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notfound-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(760px 520px at 80% 10%, rgba(18, 183, 245, 0.12), transparent 62%),
    radial-gradient(620px 460px at 10% 90%, rgba(34, 160, 240, 0.1), transparent 62%),
    #f5f8fb;
}

.notfound-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 420px;
}

.notfound-face {
  width: 148px;
  height: 148px;
  object-fit: contain;
  filter: drop-shadow(0 12px 24px rgba(10, 124, 196, 0.18));
}

.notfound-code {
  margin: 6px 0 0;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 6px;
  color: var(--color-primary, #0a7cc4);
}

.notfound-card h1 {
  margin: 8px 0 0;
  font-size: 22px;
  color: var(--color-text, #1b2733);
}

.notfound-sub {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--color-text-muted, #8a97a6);
}

.notfound-actions {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}

.btn-primary,
.btn-ghost {
  height: 36px;
  padding: 0 18px;
  border-radius: 9px;
  font-size: 13px;
}

.btn-primary {
  border: none;
  background: var(--color-primary, #0a7cc4);
  color: #fff;
}

.btn-ghost {
  border: 1px solid var(--color-border, #e0e7ef);
  background: #fff;
  color: var(--color-text-secondary, #55636f);
}

.btn-ghost:hover {
  border-color: var(--color-primary, #0a7cc4);
  color: var(--color-primary, #0a7cc4);
}
</style>
