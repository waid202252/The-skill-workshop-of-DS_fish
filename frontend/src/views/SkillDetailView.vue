<script setup lang="ts">
/**
 * 技能详情页（/skills/:id）。
 *
 * 布局参考 Raycast Store / Smithery 的插件详情：
 * 左侧主区放 README，右侧固定「元信息 + 获取方式」。
 * 所有收藏、下载、复制命令逻辑保持原样。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { communityApi, skillApi } from '@/services/api'
import { ws } from '@/services/ws'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import { formatCompact } from '@/utils/format'
import type { CommunityGuideCardVO, SkillReadme, SkillSummaryVO, SkillVO } from '@/types/api'
import AppTopbar from '@/components/AppTopbar.vue'
import CommunityGuideCard from '@/components/CommunityGuideCard.vue'
import MarkdownView from '@/components/MarkdownView.vue'

const auth = useAuthStore()
const chat = useChatStore()
const route = useRoute()
const router = useRouter()

const skill = ref<SkillVO | null>(null)
const readme = ref<SkillReadme | null>(null)
const summary = ref<SkillSummaryVO | null>(null)
const summaryLoading = ref(false)
const summaryError = ref('')
const relatedGuides = ref<CommunityGuideCardVO[]>([])
const loading = ref(true)
const readmeLoading = ref(false)
const readmeError = ref<string | null>(null)
const notFound = ref(false)
const busy = ref(false)
const downloading = ref(false)

const skillId = computed(() => Number(route.params.id))
const repoPath = computed(() => (skill.value ? `${skill.value.owner}/${skill.value.name}` : ''))

async function load(): Promise<void> {
  loading.value = true
  notFound.value = false
  try {
    skill.value = await skillApi.detail(skillId.value)
    void loadRelatedGuides(skill.value.fullName)
    void loadReadme().finally(() => {
      void loadSummary()
    })
  } catch (e) {
    notFound.value = true
    readmeError.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function loadReadme(): Promise<void> {
  if (!skill.value) return
  readmeLoading.value = true
  readmeError.value = null
  try {
    readme.value = await skillApi.readme(skill.value.id)
  } catch (e) {
    readmeError.value = errorMessage(e)
  } finally {
    readmeLoading.value = false
  }
}

async function loadRelatedGuides(fullName: string): Promise<void> {
  try {
    relatedGuides.value = await communityApi.guidesForSkill(fullName)
  } catch {
    relatedGuides.value = []
  }
}

async function loadSummary(refresh = false): Promise<void> {
  if (summaryLoading.value) return
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await skillApi.summary(skillId.value, refresh)
  } catch (e) {
    summaryError.value = errorMessage(e)
  } finally {
    summaryLoading.value = false
  }
}

function openGuide(id: number): void {
  void router.push(`/community/${id}`)
}

async function toggleInstall(): Promise<void> {
  const s = skill.value
  if (!s || busy.value) return
  busy.value = true
  const before = s.installed
  s.installed = !before
  s.installCount = Math.max(0, s.installCount + (before ? -1 : 1))
  try {
    if (before) {
      await skillApi.uninstall(s.id)
      showToast('已取消收藏', 'info')
    } else {
      const updated = await skillApi.install(s.id)
      s.installCount = updated.installCount
      showToast(`已收藏 ${s.name}`, 'success')
    }
  } catch (e) {
    s.installed = before
    s.installCount = Math.max(0, s.installCount + (before ? 1 : -1))
    showToast(errorMessage(e), 'error')
  } finally {
    busy.value = false
  }
}

async function copyClone(): Promise<void> {
  const s = skill.value
  if (!s) return
  const cmd = `git clone ${s.htmlUrl}.git`
  try {
    await navigator.clipboard.writeText(cmd)
    showToast('已复制 clone 命令', 'success')
  } catch {
    showToast(`复制失败，命令是：${cmd}`, 'info')
  }
}

async function downloadReadme(): Promise<void> {
  const s = skill.value
  if (!s) return
  downloading.value = true
  try {
    await skillApi.downloadReadme(s.id)
    showToast('已开始下载说明文档', 'success')
  } catch (e) {
    showToast(errorMessage(e), 'error')
  } finally {
    downloading.value = false
  }
}

/** 整包交给 GitHub 的 archive 接口，服务端不代理大文件 */
function downloadZip(): void {
  const s = skill.value
  if (!s) return
  window.open(`${s.htmlUrl}/archive/HEAD.zip`, '_blank', 'noopener')
}

function openExternal(url: string | null): void {
  if (url) window.open(url, '_blank', 'noopener')
}

function goBack(): void {
  if (window.history.length > 1) router.back()
  else void router.push('/skills')
}

function handleLogout(): void {
  ws.disconnect()
  chat.reset()
  auth.clearSession()
  void router.replace('/login')
}

onMounted(load)
</script>

<template>
  <div class="detail-page">
    <AppTopbar active="skills" :show-search="false" @logout="handleLogout" />

    <main class="detail-scroll">
      <div class="page-container content">
        <div class="crumb-bar">
          <button class="back-btn" type="button" @click="goBack">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="m14.5 5-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            返回
          </button>
          <span class="crumb">
            技能广场
            <span class="crumb-sep">/</span>
            <strong>{{ repoPath || '加载中' }}</strong>
          </span>
        </div>

        <p v-if="loading" class="state">
          <span class="spinner"></span>
          正在读取技能信息…
        </p>

        <div v-else-if="notFound" class="empty-block">
          <img class="state-face" src="/faces/face-grumpy.png" alt="" />
          <h3>这个技能不存在</h3>
          <p>它可能已经被移除，或者链接有误。</p>
          <button class="btn btn-primary btn-sm state-action" type="button" @click="router.push('/skills')">
            回广场搜索
          </button>
        </div>

        <template v-else-if="skill">
          <section class="hero">
            <img v-if="skill.ownerAvatar" class="hero-avatar" :src="skill.ownerAvatar" alt="" />
            <span v-else class="hero-avatar hero-avatar--fallback">{{ skill.owner.slice(0, 1) }}</span>

            <div class="hero-main">
              <h1>
                <span class="hero-owner">{{ skill.owner }}/</span>{{ skill.name }}
              </h1>
              <p class="hero-desc">{{ skill.description || '该仓库没有填写简介' }}</p>

              <div class="metrics">
                <span class="metric">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                    <path d="m12 3.6 2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8L12 3.6Z" stroke-linejoin="round" />
                  </svg>
                  {{ formatCompact(skill.stars) }}
                </span>
                <span class="metric">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                    <path d="M7 4v9a5 5 0 0 0 5 5h5M17 4v7" stroke-linecap="round" />
                  </svg>
                  {{ formatCompact(skill.forks) }}
                </span>
                <span v-if="skill.language" class="metric">
                  <span class="lang-dot"></span>{{ skill.language }}
                </span>
                <span class="metric">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                    <path d="M6.5 4.5h11a1 1 0 0 1 1 1v15l-6.5-4.2-6.5 4.2v-15a1 1 0 0 1 1-1Z" stroke-linejoin="round" />
                  </svg>
                  {{ formatCompact(skill.installCount) }} 人收藏
                </span>
              </div>

              <div v-if="skill.topics.length" class="topics">
                <span v-for="t in skill.topics.slice(0, 8)" :key="t" class="tag">{{ t }}</span>
              </div>
            </div>

            <div class="hero-actions">
              <button class="btn btn-primary" type="button" :disabled="busy" @click="toggleInstall">
                {{ busy ? '处理中…' : skill.installed ? '已收藏，点击取消' : '收藏这个技能' }}
              </button>
              <button class="btn" type="button" @click="openExternal(skill.htmlUrl)">
                打开 GitHub ↗
              </button>
            </div>
          </section>

          <section class="summary-card" aria-live="polite">
            <header class="summary-head">
              <div class="summary-title">
                <span class="summary-mark" aria-hidden="true">🐋</span>
                <h2>大肥鱼总结</h2>
                <span v-if="summary?.aiGenerated" class="summary-source">
                  基于 README · {{ summary.model }}
                </span>
                <span v-else-if="summary" class="summary-fallback">规则摘要</span>
              </div>
              <button
                class="btn btn-sm"
                type="button"
                :disabled="summaryLoading"
                @click="loadSummary(true)"
              >
                {{ summaryLoading ? '生成中…' : '重新总结' }}
              </button>
            </header>

            <div v-if="summaryLoading" class="summary-loading">
              <span class="spinner"></span>
              <div>
                <p>大肥鱼正在读 README 并写点评…</p>
                <span>首次加载模型可能要 20–60 秒，可以先往下看原文。</span>
              </div>
            </div>

            <div v-else-if="summaryError" class="summary-error">
              <p>大肥鱼总结暂时不可用：{{ summaryError }}</p>
              <button class="btn btn-sm" type="button" @click="loadSummary(true)">重试</button>
            </div>

            <template v-else-if="summary">
              <MarkdownView :source="summary.summary" class="summary-body" />
              <p v-if="summary.notice" class="summary-notice">{{ summary.notice }}</p>
              <p class="summary-foot">大肥鱼总结 · 仅供参考，以 README 原文为准。</p>
            </template>

            <div v-else class="summary-loading">
              <span class="spinner"></span>
              <p>正在准备 README…</p>
            </div>
          </section>

          <div class="body">
            <section class="doc">
              <header class="doc-head">
                <h2>{{ readme?.fileName || 'README' }}</h2>
                <a
                  v-if="readme?.sourceUrl"
                  class="doc-src"
                  :href="readme.sourceUrl"
                  target="_blank"
                  rel="noopener"
                >
                  查看原始文件 ↗
                </a>
              </header>

              <p v-if="readmeLoading" class="state">
                <span class="spinner"></span>
                正在拉取说明文档…
              </p>

              <div v-else-if="readmeError" class="state">
                <p>{{ readmeError }}</p>
                <button class="btn btn-sm" type="button" @click="loadReadme">重试</button>
              </div>

              <pre v-else class="doc-body">{{ readme?.markdown }}</pre>
            </section>

            <aside class="side">
              <section class="side-card">
                <h3>仓库信息</h3>
                <dl>
                  <div><dt>仓库</dt><dd>{{ skill.fullName }}</dd></div>
                  <div><dt>GitHub ID</dt><dd>{{ skill.githubId }}</dd></div>
                  <div><dt>Star</dt><dd>{{ formatCompact(skill.stars) }}</dd></div>
                  <div><dt>Fork</dt><dd>{{ formatCompact(skill.forks) }}</dd></div>
                  <div><dt>语言</dt><dd>{{ skill.language || '未标注' }}</dd></div>
                  <div v-if="skill.homepage">
                    <dt>主页</dt>
                    <dd><a :href="skill.homepage" target="_blank" rel="noopener">访问 ↗</a></dd>
                  </div>
                </dl>
              </section>

              <section class="side-card">
                <h3>获取方式</h3>
                <div class="side-actions">
                  <button class="btn side-btn" type="button" @click="copyClone">复制 clone 命令</button>
                  <button class="btn side-btn" type="button" :disabled="downloading" @click="downloadReadme">
                    {{ downloading ? '准备中…' : '下载说明文档' }}
                  </button>
                  <button class="btn side-btn" type="button" @click="downloadZip">下载整包 ZIP</button>
                  <button class="btn side-btn" type="button" @click="openExternal(skill.htmlUrl)">
                    在 GitHub 打开
                  </button>
                </div>
                <p class="side-hint">本产品只做推荐与下载，不执行技能。</p>
              </section>
            </aside>
          </div>

          <section v-if="relatedGuides.length" class="related-block">
            <header class="section-head">
              <h2 class="section-title">相关配方与指南</h2>
              <span class="section-hint">先看别人怎么用，再决定要不要收藏</span>
              <button class="section-action" type="button" @click="router.push('/community')">
                去社区 →
              </button>
            </header>
            <div class="related-grid">
              <CommunityGuideCard
                v-for="item in relatedGuides"
                :key="item.id"
                :guide="item"
                @open="openGuide"
              />
            </div>
          </section>
        </template>
      </div>
    </main>
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg);
}

.detail-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.content {
  padding-top: 18px;
  padding-bottom: 64px;
}

/* ---------------- 面包屑 ---------------- */
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
  font-weight: var(--weight-medium);
  transition:
    border-color var(--transition),
    color var(--transition);
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

.crumb-sep {
  color: var(--color-border-strong);
}

.crumb strong {
  overflow: hidden;
  color: var(--color-text);
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------------- 状态 ---------------- */
.state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 0;
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
.hero {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.hero-avatar {
  width: 64px;
  height: 64px;
  flex: 0 0 auto;
  border-radius: var(--radius-md);
  object-fit: cover;
  background: var(--color-bg);
}

.hero-avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary-strong);
  font-size: 24px;
  font-weight: var(--weight-bold);
  background: var(--color-primary-soft);
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.hero-main h1 {
  margin: 0;
  overflow: hidden;
  color: var(--color-text);
  font-size: 24px;
  font-weight: var(--weight-extrabold);
  letter-spacing: -0.02em;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-owner {
  color: var(--color-text-muted);
  font-weight: var(--weight-medium);
}

.hero-desc {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: 13.5px;
  line-height: 1.65;
}

.metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.metric {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 11.5px;
  white-space: nowrap;
}

.metric svg {
  color: var(--color-text-muted);
}

.metric:first-child svg {
  color: #d9a43a;
}

.lang-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-primary);
}

.topics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.hero-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 0 0 auto;
}

/* ---------------- 大肥鱼总结 ---------------- */
.summary-card {
  margin-top: 18px;
  padding: 20px 24px;
  border: 1px solid rgba(18, 183, 245, 0.24);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(70% 120% at 100% 0%, rgba(18, 183, 245, 0.09) 0%, rgba(18, 183, 245, 0) 65%),
    var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.summary-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.summary-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 14px;
}

.summary-title h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
  font-weight: var(--weight-bold);
}

.summary-source {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-primary-softer);
  color: var(--color-primary-strong);
  font-size: 10px;
}

.summary-fallback {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-warning-soft);
  color: #95520a;
  font-size: 10px;
}

.summary-loading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-height: 72px;
  padding: 8px 0 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.summary-loading p {
  margin: 0 0 3px;
  color: var(--color-text);
  font-size: 12.5px;
}

.summary-loading span:not(.spinner) {
  color: var(--color-text-muted);
  font-size: 10.5px;
  line-height: 1.6;
}

.summary-loading .spinner {
  margin-top: 2px;
  flex: 0 0 auto;
}

.summary-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0 4px;
  color: var(--color-danger);
  font-size: 12px;
}

.summary-error p {
  margin: 0;
}

.summary-body {
  margin-top: 2px;
}

.summary-notice {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: #8a4b08;
  font-size: 10.5px;
  line-height: 1.6;
}

.summary-foot {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--color-divider);
  color: var(--color-text-muted);
  font-size: 10px;
}

/* ---------------- 两栏 ---------------- */
.body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
  gap: 18px;
  margin-top: 18px;
}

.doc {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.doc-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
}

.doc-head h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 14px;
  font-weight: var(--weight-semibold);
}

.doc-src {
  margin-left: auto;
  color: var(--color-primary-strong);
  font-size: 11.5px;
  font-weight: var(--weight-medium);
}

.doc-body {
  max-height: 70vh;
  margin: 0;
  padding: 18px;
  overflow: auto;
  color: #2b3a47;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------------- 侧栏 ---------------- */
.side {
  position: sticky;
  top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-card {
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  box-shadow: var(--shadow-sm);
}

.side-card h3 {
  margin: 0 0 12px;
  color: var(--color-text);
  font-size: 13px;
  font-weight: var(--weight-semibold);
}

.side-card dl {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 0;
}

.side-card dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.side-card dt {
  flex: 0 0 auto;
  color: var(--color-text-muted);
}

.side-card dd {
  margin: 0;
  overflow: hidden;
  color: var(--color-text);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-card dd a {
  color: var(--color-primary-strong);
}

.side-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.side-btn {
  width: 100%;
  height: 34px;
  font-size: 12.5px;
}

.side-hint {
  margin: 12px 0 0;
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.55;
}

.related-block {
  margin-top: 28px;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

@media (max-width: 900px) {
  .body {
    grid-template-columns: 1fr;
  }

  .side {
    position: static;
  }

  .hero {
    flex-wrap: wrap;
  }

  .hero-actions {
    width: 100%;
    flex-direction: row;
  }

  .hero-actions .btn {
    flex: 1;
  }

  .hero-main h1 {
    white-space: normal;
    word-break: break-word;
  }
}

@media (max-width: 620px) {
  .content {
    padding-top: 12px;
  }

  .hero {
    padding: 18px;
  }

  .hero-avatar {
    width: 52px;
    height: 52px;
  }

  .hero-main h1 {
    font-size: 20px;
  }

  .summary-card {
    padding: 16px 18px;
  }

  .summary-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-head .btn {
    width: 100%;
  }

  .doc-body {
    font-size: 12px;
  }
}
</style>
