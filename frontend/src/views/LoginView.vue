<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/utils/error'

type Mode = 'login' | 'register'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<Mode>('login')
const submitting = ref(false)
const errorBanner = ref('')
/** 密码可见切换：手机上输密码很容易打错，加这个能少一半重试 */
const showPassword = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

const errors = reactive({
  username: '',
  password: '',
  nickname: '',
})

const isRegister = computed(() => mode.value === 'register')

/** 装饰气泡：位置/大小/延迟都写死，避免每次渲染抖动 */
const bubbles = [
  { id: 1, style: 'left:6%;top:14%;width:34px;height:34px;animation-delay:0s' },
  { id: 2, style: 'left:16%;top:62%;width:18px;height:18px;animation-delay:1.4s' },
  { id: 3, style: 'left:78%;top:10%;width:46px;height:46px;animation-delay:2.1s' },
  { id: 4, style: 'left:88%;top:48%;width:22px;height:22px;animation-delay:0.7s' },
  { id: 5, style: 'left:68%;top:74%;width:30px;height:30px;animation-delay:2.8s' },
  { id: 6, style: 'left:34%;top:6%;width:14px;height:14px;animation-delay:1.9s' },
  { id: 7, style: 'left:44%;top:84%;width:24px;height:24px;animation-delay:3.2s' },
]
const submitText = computed(() => {
  if (submitting.value) return isRegister.value ? '注册中…' : '登录中…'
  return isRegister.value ? '注册并登录' : '登录'
})

function validateUsername(): boolean {
  const value = form.username.trim()
  if (!value) {
    errors.username = '请输入用户名'
    return false
  }
  if (!/^[A-Za-z0-9_]{4,20}$/.test(value)) {
    errors.username = '用户名需为 4-20 位字母、数字或下划线'
    return false
  }
  errors.username = ''
  return true
}

function validatePassword(): boolean {
  if (!form.password) {
    errors.password = '请输入密码'
    return false
  }
  if (form.password.length < 6 || form.password.length > 32) {
    errors.password = '密码长度需为 6-32 位'
    return false
  }
  errors.password = ''
  return true
}

function validateNickname(): boolean {
  const value = form.nickname.trim()
  if (!value) {
    errors.nickname = '请输入昵称'
    return false
  }
  if (value.length > 20) {
    errors.nickname = '昵称长度不能超过 20 位'
    return false
  }
  errors.nickname = ''
  return true
}

function switchMode(next: Mode): void {
  if (mode.value === next) return
  mode.value = next
  errorBanner.value = ''
  errors.username = ''
  errors.password = ''
  errors.nickname = ''
}

function resolveRedirect(): string {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('/login')) {
    return redirect
  }
  return '/'
}

async function submit(): Promise<void> {
  errorBanner.value = ''
  const usernameValid = validateUsername()
  const passwordValid = validatePassword()
  const nicknameValid = isRegister.value ? validateNickname() : true
  if (!usernameValid || !passwordValid || !nicknameValid) return

  submitting.value = true
  try {
    if (isRegister.value) {
      await auth.register({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim(),
      })
    } else {
      await auth.login(form.username.trim(), form.password)
    }
    await router.replace(resolveRedirect())
  } catch (error) {
    errorBanner.value = errorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="brand-decor brand-decor--one"></div>
      <div class="brand-decor brand-decor--two"></div>
      <div class="brand-decor brand-decor--three"></div>

      <!-- 上浮的气泡，纯装饰 -->
      <div class="bubbles" aria-hidden="true">
        <span v-for="bubble in bubbles" :key="bubble.id" class="bubble" :style="bubble.style"></span>
      </div>

      <!-- 底部浪花，把渐变和内容隔开 -->
      <svg class="wave" viewBox="0 0 1440 120" preserveAspectRatio="none" aria-hidden="true">
        <path
          d="M0,64 C240,120 480,8 720,40 C960,72 1200,120 1440,64 L1440,120 L0,120 Z"
          fill="rgba(255,255,255,0.10)"
        />
        <path
          d="M0,88 C260,44 520,116 760,84 C1000,52 1240,96 1440,80 L1440,120 L0,120 Z"
          fill="rgba(255,255,255,0.08)"
        />
      </svg>

      <!-- 右侧漂浮的技能卡片：纯装饰，同时暗示产品在做什么 -->
      <div class="float-cards" aria-hidden="true">
        <div class="fc fc--1">
          <span class="fc-badge">视频</span>
          <span class="fc-name">ai-video-generation</span>
          <span class="fc-star">★ 31.8k</span>
        </div>
        <div class="fc fc--2">
          <span class="fc-badge fc-badge--purple">图像</span>
          <span class="fc-name">text-to-image</span>
          <span class="fc-star">★ 18.2k</span>
        </div>
        <div class="fc fc--3">
          <span class="fc-badge fc-badge--green">Agent</span>
          <span class="fc-name">agent-skill</span>
          <span class="fc-star">★ 12.6k</span>
        </div>
      </div>

      <div class="brand-content">
        <span class="hud-corner hud-corner--tl" aria-hidden="true"></span>
        <span class="hud-corner hud-corner--br" aria-hidden="true"></span>

        <div class="brand-topline">
          <span class="hud-tag">01 / HOME</span>
          <span class="hud-line"></span>
          <span class="hud-tag hud-tag--dim">GITHUB SKILL FEED</span>
        </div>

        <div class="brand-badge">
          <img src="/faces/face-wink.png" alt="" />
          <span>AI 技能推荐</span>
        </div>

        <div class="brand-mascot">
          <span class="mascot-ring" aria-hidden="true"></span>
          <span class="mascot-ring mascot-ring--2" aria-hidden="true"></span>
          <span class="mascot-halo"></span>
          <img src="/mascot.png" alt="大肥鱼" />
          <span class="mascot-tag">DAFEIYU · NO.001</span>
        </div>

        <h1 class="brand-name">大肥鱼<span>的工坊</span></h1>
        <p class="brand-en">DAFEIYU WORKSHOP</p>

        <div class="brand-band">
          <p class="brand-slogan">从 GitHub 捞最值得用的 AI 技能</p>
        </div>

        <div class="brand-stats">
          <div class="stat">
            <strong>08</strong>
            <span>推荐分类</span>
          </div>
          <div class="stat">
            <strong>03</strong>
            <span>下载方式</span>
          </div>
          <div class="stat">
            <strong>∞</strong>
            <span>技能来源</span>
          </div>
        </div>
      </div>

      <p class="brand-footer">大肥鱼的工坊 · 技能推荐与下载</p>
    </section>

    <section class="form-panel">
      <div class="form-deco" aria-hidden="true">
        <span class="deco-grid"></span>
        <span class="deco-watermark">LOGIN</span>
        <span class="deco-slash"></span>
        <span class="deco-dot"></span>
        <span class="deco-side">DAFEIYU WORKSHOP · 2026</span>
      </div>

      <footer class="form-footbar">
        <span class="footbar-line"></span>
        <span>大肥鱼的工坊</span>
        <span class="footbar-dot">·</span>
        <span>技能推荐与下载</span>
        <span class="footbar-dot">·</span>
        <span>v1.0</span>
      </footer>

      <div class="form-card">
        <p class="form-eyebrow">SIGN IN / 登录</p>
        <div class="form-head">
          <h2>{{ isRegister ? '创建账号' : '欢迎回来' }}</h2>
          <p>{{ isRegister ? '注册后就能开始捞技能了' : '登录你的工坊账号，继续捞技能' }}</p>
        </div>

        <div class="tabs" role="tablist">
          <button
            class="tab"
            :class="{ 'tab--active': mode === 'login' }"
            type="button"
            role="tab"
            :aria-selected="mode === 'login'"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            class="tab"
            :class="{ 'tab--active': mode === 'register' }"
            type="button"
            role="tab"
            :aria-selected="mode === 'register'"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <Transition name="banner">
          <div v-if="errorBanner" class="error-banner" role="alert">
            <span class="error-icon">!</span>
            <span class="error-text">{{ errorBanner }}</span>
          </div>
        </Transition>

        <form class="login-form" novalidate @submit.prevent="submit">
          <label class="field">
            <span class="field-label">用户名</span>
            <input
              v-model="form.username"
              class="text-field"
              :class="{ 'is-error': errors.username }"
              type="text"
              autocomplete="username"
              maxlength="20"
              placeholder="4-20 位字母、数字或下划线"
              @blur="validateUsername"
            />
            <span v-if="errors.username" class="field-error">{{ errors.username }}</span>
          </label>

          <label v-if="isRegister" class="field">
            <span class="field-label">昵称</span>
            <input
              v-model="form.nickname"
              class="text-field"
              :class="{ 'is-error': errors.nickname }"
              type="text"
              autocomplete="nickname"
              maxlength="20"
              placeholder="别人看到的名字（1-20 位）"
              @blur="validateNickname"
            />
            <span v-if="errors.nickname" class="field-error">{{ errors.nickname }}</span>
          </label>

          <label class="field">
            <span class="field-label">密码</span>
            <div class="password-wrap">
              <input
                v-model="form.password"
                class="text-field"
                :class="{ 'is-error': errors.password }"
                :type="showPassword ? 'text' : 'password'"
                :autocomplete="isRegister ? 'new-password' : 'current-password'"
                maxlength="32"
                placeholder="6-32 位密码"
                @blur="validatePassword"
              />
              <button
                class="pw-toggle"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </div>
            <span v-if="errors.password" class="field-error">{{ errors.password }}</span>
          </label>

          <button class="submit-button" type="submit" :disabled="submitting">
            <span v-if="submitting" class="button-spinner"></span>
            {{ submitText }}
          </button>
        </form>

        <p class="form-foot">
          {{ isRegister ? '已有账号？' : '还没有账号？' }}
          <button type="button" class="foot-link" @click="switchMode(isRegister ? 'login' : 'register')">
            {{ isRegister ? '去登录' : '立即注册' }}
          </button>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 0.92fr);
  height: 100%;
  background: #fff;
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px 64px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(152deg, #0a4f7d 0%, #0d86c9 42%, #22b7f0 74%, #5fd4f5 100%);
}

.brand-decor {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.09);
  pointer-events: none;
}

.brand-decor--one {
  width: 420px;
  height: 420px;
  top: -140px;
  right: -120px;
}

.brand-decor--two {
  width: 260px;
  height: 260px;
  bottom: -90px;
  left: -70px;
  background: rgba(255, 255, 255, 0.07);
}

.brand-decor--three {
  width: 120px;
  height: 120px;
  top: 56%;
  right: 16%;
  background: rgba(255, 255, 255, 0.08);
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 460px;
  /* 左侧内容本来就是左对齐，配合 HUD 角标需要留出边距 */
  margin-left: 6px;
  animation: fade-in 0.5s ease;
}

/* ---------- HUD 装饰层（二次元官网常见的编号 / 细线 / 角标） ---------- */
.brand-topline {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.hud-tag {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 2.2px;
  color: rgba(255, 255, 255, 0.88);
}

.hud-tag--dim {
  color: rgba(255, 255, 255, 0.5);
}

.hud-line {
  width: 46px;
  height: 1px;
  background: rgba(255, 255, 255, 0.42);
}

.hud-corner {
  position: absolute;
  width: 26px;
  height: 26px;
  border: 1px solid rgba(255, 255, 255, 0.38);
  pointer-events: none;
}

.hud-corner--tl {
  top: -16px;
  left: -16px;
  border-right: none;
  border-bottom: none;
}

.hud-corner--br {
  bottom: -16px;
  right: -16px;
  border-left: none;
  border-top: none;
}

/* 立绘背后的同心圆环 */
.mascot-ring {
  position: absolute;
  left: 50%;
  top: 50%;
  translate: -50% -50%;
  width: 192px;
  height: 192px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.24);
  pointer-events: none;
}

.mascot-ring--2 {
  width: 236px;
  height: 236px;
  border-style: dashed;
  border-color: rgba(255, 255, 255, 0.14);
  animation: ring-spin 28s linear infinite;
}

@keyframes ring-spin {
  to {
    rotate: 360deg;
  }
}

.mascot-tag {
  position: absolute;
  left: 50%;
  bottom: -4px;
  translate: -50% 0;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.26);
  font-size: 9.5px;
  letter-spacing: 1.6px;
  color: rgba(255, 255, 255, 0.82);
  white-space: nowrap;
}

/* 双语标题：中文主标题 + 英文大写字距标题 */
.brand-en {
  margin: 7px 0 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 5.5px;
  color: rgba(255, 255, 255, 0.6);
}

/* 斜切色带承载 slogan */
.brand-band {
  position: relative;
  align-self: flex-start;
  max-width: 100%;
  margin-top: 20px;
  padding: 7px 18px;
  transform: skewX(-12deg);
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.22), rgba(255, 255, 255, 0.03));
  border-left: 3px solid rgba(255, 255, 255, 0.9);
}

.brand-band .brand-slogan {
  margin: 0;
  transform: skewX(12deg);
  font-size: 14px;
  letter-spacing: 0.5px;
}

/* 数据条：用数字代替自夸文案 */
.brand-stats {
  display: flex;
  gap: 30px;
  margin-top: 26px;
}

.stat {
  display: flex;
  flex-direction: column;
}

.stat strong {
  font-size: 23px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 1px;
}

.stat span {
  margin-top: 5px;
  font-size: 11px;
  letter-spacing: 0.6px;
  color: rgba(255, 255, 255, 0.6);
}

/* 装饰气泡 */
.bubbles {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.bubble {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 30%, rgba(255, 255, 255, 0.6), rgba(255, 255, 255, 0.05) 72%);
  border: 1px solid rgba(255, 255, 255, 0.26);
  animation: bubble-float 9s ease-in-out infinite;
}

@keyframes bubble-float {
  0%,
  100% {
    transform: translateY(0) scale(1);
    opacity: 0.8;
  }
  50% {
    transform: translateY(-20px) scale(1.05);
    opacity: 1;
  }
}

/* 底部浪花 */
.wave {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 120px;
  pointer-events: none;
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  align-self: flex-start;
  padding: 4px 13px 4px 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(6px);
  font-size: 12px;
  letter-spacing: 0.5px;
}

.brand-badge img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
  flex: 0 0 auto;
}

/* 右侧漂浮技能卡片 */
.float-cards {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}

.fc {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 13px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.26);
  backdrop-filter: blur(8px);
  box-shadow: 0 10px 24px rgba(3, 40, 70, 0.2);
  white-space: nowrap;
  animation: fc-float 7s ease-in-out infinite;
}

.fc--1 {
  right: 6%;
  top: 20%;
  animation-delay: 0s;
  transform: rotate(-3deg);
}

.fc--2 {
  right: 11%;
  top: 47%;
  animation-delay: 1.1s;
  transform: rotate(2.5deg);
}

.fc--3 {
  right: 4%;
  top: 72%;
  animation-delay: 2.3s;
  transform: rotate(-1.5deg);
}

.fc-badge {
  padding: 1px 7px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
  color: #0a6fa8;
  font-size: 10.5px;
  font-weight: 600;
}

.fc-badge--purple {
  color: #7b5cf0;
}

.fc-badge--green {
  color: #0f9d76;
}

.fc-name {
  font-size: 11.5px;
  color: #fff;
  opacity: 0.95;
}

.fc-star {
  font-size: 11px;
  color: #ffd479;
}

@keyframes fc-float {
  0%,
  100% {
    translate: 0 0;
  }
  50% {
    translate: 0 -12px;
  }
}

.brand-mascot {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 200px;
  height: 200px;
  margin-bottom: 2px;
}

/* 角色背后的柔光，让透明底立绘不会"浮"在渐变上 */
.mascot-halo {
  position: absolute;
  inset: 4px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(255, 255, 255, 0.42) 0%,
    rgba(255, 255, 255, 0.1) 58%,
    rgba(255, 255, 255, 0) 74%
  );
}

.brand-mascot img {
  position: relative;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  filter: drop-shadow(0 14px 26px rgba(4, 42, 74, 0.34));
  animation: mascot-bob 5.5s ease-in-out infinite;
}

@keyframes mascot-bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-mascot img,
  .bubble,
  .fc {
    animation: none;
  }
}

.brand-name {
  margin: 18px 0 0;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-name span {
  font-size: 0.62em;
  font-weight: 500;
  opacity: 0.92;
  letter-spacing: 0.5px;
  margin-left: 2px;
}

.brand-slogan {
  margin: 14px 0 0;
  font-size: 17px;
  letter-spacing: 1px;
  opacity: 0.92;
}

.brand-footer {
  position: absolute;
  left: 64px;
  bottom: 34px;
  margin: 0;
  font-size: 12px;
  opacity: 0.65;
}

.form-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  overflow-y: auto;
  /* 极淡的角落光晕，避免大片死白 */
  background:
    radial-gradient(760px 520px at 100% 0%, rgba(18, 183, 245, 0.1), transparent 62%),
    radial-gradient(620px 460px at 0% 100%, rgba(34, 160, 240, 0.08), transparent 62%),
    #fff;
}

/* 右上角一个小水印，呼应左侧的海洋主题 */
.form-panel::after {
  content: '';
  position: absolute;
  top: 26px;
  right: 30px;
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: rgba(18, 183, 245, 0.08);
  pointer-events: none;
}

.form-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 376px;
  animation: fade-in 0.35s ease;
}

/* ---------- 右侧装饰层（网点 + 水印 + 斜线，打破死白） ---------- */
.form-deco {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.deco-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(10, 124, 196, 0.18) 1px, transparent 1px);
  background-size: 18px 18px;
  -webkit-mask-image: radial-gradient(760px 620px at 78% 18%, #000 0%, transparent 72%);
  mask-image: radial-gradient(760px 620px at 78% 18%, #000 0%, transparent 72%);
  opacity: 0.6;
}

.deco-watermark {
  position: absolute;
  right: -18px;
  bottom: 4%;
  font-size: 150px;
  font-weight: 800;
  letter-spacing: 10px;
  line-height: 1;
  color: rgba(10, 124, 196, 0.042);
  white-space: nowrap;
  user-select: none;
}

/* 右缘竖排标签 */
.deco-side {
  position: absolute;
  right: 18px;
  top: 50%;
  translate: 0 -50%;
  writing-mode: vertical-rl;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 4px;
  color: rgba(10, 124, 196, 0.28);
  white-space: nowrap;
}

/* 底部信息条 */
.form-footbar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 11px;
  color: var(--color-text-muted);
  opacity: 0.75;
}

.footbar-line {
  width: 26px;
  height: 1px;
  background: currentColor;
  opacity: 0.4;
  margin-right: 4px;
}

.footbar-dot {
  opacity: 0.5;
}

.deco-slash {
  position: absolute;
  left: 46px;
  top: -10%;
  width: 1px;
  height: 120%;
  background: linear-gradient(transparent, rgba(10, 124, 196, 0.2), transparent);
  rotate: 9deg;
}

.deco-dot {
  position: absolute;
  right: 46px;
  top: 110px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(18, 183, 245, 0.4);
  box-shadow: 0 0 0 7px rgba(18, 183, 245, 0.08);
}

.form-eyebrow {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 12px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 2.6px;
  color: var(--color-primary);
}

.form-eyebrow::before {
  content: '';
  width: 20px;
  height: 2px;
  background: var(--color-primary);
}

.form-head h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
}

.form-head p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.tabs {
  display: flex;
  gap: 24px;
  margin-top: 28px;
  border-bottom: 1px solid var(--color-border);
}

.tab {
  position: relative;
  padding: 0 2px 11px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 14px;
  font-weight: 500;
  transition: color 0.15s ease;
}

.tab:hover {
  color: var(--color-text-secondary);
}

.tab--active {
  color: var(--color-text);
  font-weight: 600;
}

.tab--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--color-primary);
}

.error-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 16px;
  padding: 9px 11px;
  border: 1px solid rgba(245, 72, 59, 0.25);
  border-radius: 8px;
  background: rgba(245, 72, 59, 0.07);
  color: var(--color-danger);
  font-size: 12.5px;
  line-height: 1.5;
}

.error-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  margin-top: 1px;
  border-radius: 50%;
  background: var(--color-danger);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.error-text {
  word-break: break-word;
}

.banner-enter-active,
.banner-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.banner-enter-from,
.banner-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 22px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

/* 密码框 + 右侧「显示/隐藏」按钮 */
.password-wrap {
  position: relative;
  display: flex;
}

.password-wrap .text-field {
  /* 给按钮留出位置，避免文字被压到按钮下面 */
  padding-right: 52px;
}

.pw-toggle {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  padding: 3px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 11.5px;
  line-height: 1;
}

.pw-toggle:hover {
  background: rgba(10, 124, 196, 0.08);
  color: var(--color-primary);
}

.field-label {
  color: var(--color-text-secondary);
  font-size: 12.5px;
  font-weight: 500;
}

.field-error {
  color: var(--color-danger);
  font-size: 11.5px;
}

.submit-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 42px;
  margin-top: 6px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(120deg, #12b7f5, #3f8cf7);
  color: #fff;
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: 1px;
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}

.submit-button:hover:not(:disabled) {
  transform: translateY(-1px);
  opacity: 0.94;
}

.submit-button:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.button-spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.form-foot {
  margin: 22px 0 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
  text-align: center;
}

.foot-link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: 12.5px;
}

.foot-link:hover {
  text-decoration: underline;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }
}
</style>
