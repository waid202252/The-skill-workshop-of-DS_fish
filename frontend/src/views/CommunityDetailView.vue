<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { communityApi } from '@/services/api'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import type { CommunityGuideDetailVO } from '@/types/api'
import AppTopbar from '@/components/AppTopbar.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import AskAiModal from '@/components/AskAiModal.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const guide = ref<CommunityGuideDetailVO | null>(null)
const loading = ref(true)
const notFound = ref(false)
const askOpen = ref(false)
const feedbackPending = ref(false)

const typeLabel = computed(() => (guide.value?.type === 'RECIPE' ? '配方' : '指南'))

function goBack(): void {
  if (window.history.length > 1) router.back()
  else void router.push('/community')
}

function goAsk(): void {
  if (!auth.isAuthenticated) {
    void router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  askOpen.value = true
}

async function load(): Promise<void> {
  loading.value = true
  notFound.value = false
  try {
    guide.value = await communityApi.detail(Number(route.params.id))
  } catch (error) {
    notFound.value = true
    showToast(errorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function copyPrompt(): Promise<void> {
  const prompt = guide.value?.promptText
  if (!prompt) return
  try {
    await navigator.clipboard.writeText(prompt)
    showToast('已复制，可以直接粘贴使用', 'success')
  } catch {
    showToast('复制失败，请手动选中文本', 'info')
  }
}

async function rate(helpful: boolean): Promise<void> {
  if (!guide.value || feedbackPending.value) return
  if (!auth.isAuthenticated) {
    void router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  feedbackPending.value = true
  try {
    const result = await communityApi.feedback(guide.value.id, helpful)
    guide.value.helpfulCount = result.helpfulCount
    guide.value.notHelpfulCount = result.notHelpfulCount
    guide.value.myFeedback = result.myFeedback
    showToast(helpful ? '已标记为有用' : '已记录，编辑会补充', 'success')
  } catch (error) {
    showToast(errorMessage(error), 'error')
  } finally {
    feedbackPending.value = false
  }
}

function findSkill(fullName: string): void {
  void router.push({ path: '/skills', query: { q: fullName } })
}

onMounted(load)
</script>

<template>
  <div class="guide-page">
    <AppTopbar active="community" :show-search="false" />

    <main class="guide-scroll">
      <div class="page-container content">
        <div class="crumb-bar">
          <button class="back-btn" type="button" @click="goBack">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="m14.5 5-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            返回社区
          </button>
          <span class="crumb">工坊社区 <span>/</span> <strong>{{ guide?.title || '加载中' }}</strong></span>
        </div>

        <div v-if="loading" class="state">
          <span class="spinner"></span>
          正在打开…
        </div>

        <div v-else-if="notFound" class="empty-block">
          <img class="state-face" src="/faces/face-grumpy.png" alt="" />
          <h3>这篇内容不存在</h3>
          <p>它可能已经下线，或者链接有误。</p>
          <button class="btn btn-primary btn-sm state-action" type="button" @click="router.push('/community')">
            回社区看看
          </button>
        </div>

        <template v-else-if="guide">
          <header class="guide-hero">
            <span class="guide-emoji" aria-hidden="true">{{ guide.emoji || '📘' }}</span>
            <div class="guide-head-main">
              <div class="guide-badges">
                <span class="type-badge">{{ typeLabel }}</span>
                <span v-if="guide.aiGenerated" class="ai-badge">AI 整理 · 试验版</span>
                <span v-if="guide.difficulty" class="meta-badge">{{ guide.difficulty }}</span>
              </div>
              <h1>{{ guide.title }}</h1>
              <p class="guide-summary">{{ guide.summary }}</p>
              <div class="guide-meta">
                <span v-if="guide.duration">{{ guide.duration }}</span>
                <span v-if="guide.cost">{{ guide.cost }}</span>
                <span>{{ guide.viewCount }} 次浏览</span>
                <span>{{ guide.helpfulCount }} 人觉得有用</span>
              </div>
              <div v-if="guide.tags.length" class="guide-tags">
                <span v-for="tag in guide.tags" :key="tag" class="tag">{{ tag }}</span>
              </div>
            </div>

            <div class="guide-actions">
              <button v-if="guide.promptText" class="btn btn-primary" type="button" @click="copyPrompt">
                复制提示词
              </button>
              <button class="btn" type="button" @click="goAsk">问大肥鱼</button>
            </div>
          </header>

          <div class="guide-body">
            <div class="guide-main">
              <section v-if="guide.promptText" class="prompt-card">
                <header class="prompt-head">
                  <div>
                    <p class="card-eyebrow">COPY &amp; USE</p>
                    <h2>可以直接复制的提示词</h2>
                  </div>
                  <button class="btn btn-sm" type="button" @click="copyPrompt">复制</button>
                </header>
                <pre class="prompt-text">{{ guide.promptText }}</pre>
              </section>

              <section class="content-card">
                <MarkdownView :source="guide.content" />
              </section>

              <section v-if="guide.relatedSkills.length" class="related-card">
                <header class="related-head">
                  <div>
                    <p class="card-eyebrow">RELATED SKILLS</p>
                    <h2>用到的技能</h2>
                  </div>
                  <span class="related-hint">先去技能库看安装与 README</span>
                </header>

                <div class="skill-list">
                  <div v-for="skill in guide.relatedSkills" :key="skill.fullName" class="skill-row">
                    <span class="skill-avatar" aria-hidden="true">{{ skill.name.slice(0, 1).toUpperCase() }}</span>
                    <div class="skill-text">
                      <strong>{{ skill.name }}</strong>
                      <span>{{ skill.owner }} · {{ skill.fullName }}</span>
                    </div>
                    <button class="skill-action" type="button" @click="findSkill(skill.fullName)">
                      在技能库找
                    </button>
                    <a
                      class="skill-github"
                      :href="skill.htmlUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      title="打开 GitHub"
                    >
                      ↗
                    </a>
                  </div>
                </div>
              </section>
            </div>

            <aside class="guide-aside">
              <section class="aside-card feedback-card">
                <p class="aside-title">这篇内容</p>
                <h2>对你有帮助吗？</h2>
                <p>你的反馈会决定哪些内容继续更新；点“没帮助”会进入人工补充队列。</p>
                <div class="feedback-actions">
                  <button
                    class="feedback-btn"
                    :class="{ 'feedback-btn--active': guide.myFeedback === true }"
                    type="button"
                    :disabled="feedbackPending"
                    @click="rate(true)"
                  >
                    有用 · {{ guide.helpfulCount }}
                  </button>
                  <button
                    class="feedback-btn"
                    :class="{ 'feedback-btn--active': guide.myFeedback === false }"
                    type="button"
                    :disabled="feedbackPending"
                    @click="rate(false)"
                  >
                    没帮助 · {{ guide.notHelpfulCount }}
                  </button>
                </div>
              </section>

              <section class="aside-card">
                <p class="aside-title">还是卡住？</p>
                <p class="aside-copy">把报错原文、系统版本和已经试过的步骤发给大肥鱼，它会先给一条最短路径。</p>
                <button class="btn btn-primary aside-ask" type="button" @click="goAsk">问大肥鱼</button>
                <p v-if="!auth.isAuthenticated" class="aside-hint">登录后才能提问。</p>
              </section>

              <section class="aside-card aside-note">
                <p class="aside-title">内容说明</p>
                <p>试验期内容由 AI 整理，欢迎边用边纠错。当前不替代人工客服，也不保证所有项目在你机器上可运行。</p>
              </section>
            </aside>
          </div>
        </template>
      </div>
    </main>

    <AskAiModal v-if="askOpen" @close="askOpen = false" />
  </div>
</template>

<style scoped>
.guide-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg);
}

.guide-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.content {
  padding-top: 18px;
  padding-bottom: 64px;
}

.crumb-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
  margin-bottom: 12px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px 0 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-text-secondary);
  font-size: 12px;
}

.back-btn:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text);
}

.crumb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.crumb span {
  color: var(--color-border-strong);
}

.crumb strong {
  overflow: hidden;
  color: var(--color-text);
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.state-action {
  margin-top: 10px;
}

.state-face {
  width: 96px;
  height: 96px;
  object-fit: contain;
  margin-bottom: 4px;
  filter: drop-shadow(0 8px 16px rgba(18, 183, 245, 0.16));
}

/* ---------------- 头部 ---------------- */
.guide-hero {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(70% 120% at 100% 0%, rgba(18, 183, 245, 0.07) 0%, rgba(18, 183, 245, 0) 65%),
    var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.guide-emoji {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  flex: 0 0 auto;
  border-radius: 16px;
  background: var(--color-primary-softer);
  font-size: 28px;
}

.guide-head-main {
  flex: 1;
  min-width: 0;
}

.guide-badges {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.type-badge {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 10px;
  font-weight: var(--weight-semibold);
}

.ai-badge {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-warning-soft);
  color: #95520a;
  font-size: 10px;
  font-weight: var(--weight-semibold);
}

.meta-badge {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-subtle);
  color: var(--color-text-muted);
  font-size: 10px;
}

.guide-hero h1 {
  margin: 8px 0 0;
  color: var(--color-text);
  font-size: 26px;
  font-weight: var(--weight-extrabold);
  letter-spacing: -0.02em;
  line-height: 1.3;
}

.guide-summary {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: 13.5px;
  line-height: 1.65;
}

.guide-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.guide-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.guide-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 0 0 auto;
}

/* ---------------- 两栏 ---------------- */
.guide-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
  gap: 18px;
  margin-top: 18px;
}

.guide-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.content-card,
.prompt-card,
.related-card {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.content-card {
  padding: 22px 24px;
}

.prompt-card {
  padding: 18px 20px;
}

.prompt-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-eyebrow {
  margin: 0 0 4px;
  color: var(--color-primary-strong);
  font-size: 9.5px;
  font-weight: var(--weight-bold);
  letter-spacing: 1.5px;
}

.prompt-head h2,
.related-head h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 15px;
  font-weight: var(--weight-bold);
}

.prompt-text {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #f8fafc;
  color: #334155;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.related-card {
  padding: 18px 20px;
}

.related-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.related-hint {
  color: var(--color-text-muted);
  font-size: 10px;
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skill-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel);
}

.skill-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 9px;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 13px;
  font-weight: var(--weight-bold);
}

.skill-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.skill-text strong {
  overflow: hidden;
  color: var(--color-text);
  font-size: 12px;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-text span {
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-action {
  margin-left: auto;
  height: 28px;
  padding: 0 10px;
  flex: 0 0 auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-text-secondary);
  font-size: 10.5px;
}

.skill-action:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
}

.skill-github {
  color: var(--color-text-muted);
  font-size: 14px;
  flex: 0 0 auto;
}

.skill-github:hover {
  color: var(--color-primary-strong);
}

/* ---------------- 侧栏 ---------------- */
.guide-aside {
  position: sticky;
  top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.aside-card {
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.aside-title {
  margin: 0 0 10px;
  color: var(--color-text-muted);
  font-size: 10px;
  font-weight: var(--weight-bold);
  letter-spacing: 1.2px;
  text-transform: uppercase;
}

.feedback-card h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
  font-weight: var(--weight-bold);
}

.feedback-card > p:not(.aside-title) {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.65;
}

.feedback-actions {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}

.feedback-btn {
  flex: 1;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-text-secondary);
  font-size: 10.5px;
}

.feedback-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
}

.feedback-btn--active {
  border-color: rgba(18, 183, 245, 0.45);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-weight: var(--weight-semibold);
}

.aside-copy {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.7;
}

.aside-ask {
  width: 100%;
  margin-top: 12px;
  height: 34px;
  font-size: 12px;
}

.aside-hint {
  margin: 7px 0 0;
  color: var(--color-text-muted);
  font-size: 10px;
  text-align: center;
}

.aside-note p:last-child {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 10.5px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .guide-body {
    grid-template-columns: 1fr;
  }

  .guide-aside {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .guide-hero {
    flex-wrap: wrap;
  }

  .guide-actions {
    width: 100%;
    flex-direction: row;
  }

  .guide-actions .btn {
    flex: 1;
  }

  .guide-hero h1 {
    font-size: 22px;
  }
}

@media (max-width: 620px) {
  .content {
    padding-top: 12px;
  }

  .guide-hero {
    padding: 18px;
  }

  .guide-emoji {
    width: 46px;
    height: 46px;
    font-size: 23px;
  }

  .guide-hero h1 {
    font-size: 20px;
  }

  .content-card {
    padding: 18px;
  }

  .guide-aside {
    grid-template-columns: 1fr;
  }

  .related-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }
}
</style>
