<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { communityApi } from '@/services/api'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import type { CommunityGuideCardVO, CommunityGuideType } from '@/types/api'
import AppTopbar from '@/components/AppTopbar.vue'
import CommunityGuideCard from '@/components/CommunityGuideCard.vue'
import AskAiModal from '@/components/AskAiModal.vue'

type Tab = 'ALL' | CommunityGuideType

const router = useRouter()
const auth = useAuthStore()

const guides = ref<CommunityGuideCardVO[]>([])
const loading = ref(true)
const activeTab = ref<Tab>('ALL')
const keyword = ref('')
const askOpen = ref(false)

const recipes = computed(() => guides.value.filter((item) => item.type === 'RECIPE'))
const guideCount = computed(() => guides.value.filter((item) => item.type === 'GUIDE').length)
const recipeCount = computed(() => recipes.value.length)

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return guides.value.filter((item) => {
    if (activeTab.value !== 'ALL' && item.type !== activeTab.value) return false
    if (!query) return true
    return (
      item.title.toLowerCase().includes(query) ||
      item.summary.toLowerCase().includes(query) ||
      item.tags.some((tag) => tag.toLowerCase().includes(query))
    )
  })
})

const featured = computed(() =>
  activeTab.value === 'GUIDE' || keyword.value.trim() ? null : recipes.value[0] ?? null,
)

const feed = computed(() =>
  featured.value ? filtered.value.filter((item) => item.id !== featured.value?.id) : filtered.value,
)

const starterGuides = computed(() => guides.value.filter((item) => item.type === 'GUIDE').slice(0, 3))

async function load(): Promise<void> {
  loading.value = true
  try {
    guides.value = await communityApi.list({ limit: 30 })
  } catch (error) {
    showToast(errorMessage(error), 'error')
    guides.value = []
  } finally {
    loading.value = false
  }
}

function openGuide(id: number): void {
  void router.push(`/community/${id}`)
}

function goAsk(): void {
  if (!auth.isAuthenticated) {
    void router.push({ path: '/login', query: { redirect: '/community' } })
    return
  }
  askOpen.value = true
}

function goLogin(): void {
  void router.push({ path: '/login', query: { redirect: '/community' } })
}

function goSkills(): void {
  void router.push('/skills')
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="community-page">
    <AppTopbar active="community" :show-search="false" />

    <main class="community-scroll">
      <div class="page-container">
        <header class="community-hero">
          <div>
            <p class="eyebrow">COMMUNITY</p>
            <h1>工坊社区</h1>
            <p class="hero-sub">
              不用先学完，先拿一个配方做出结果。当前是试验版，内容由 AI 整理，欢迎边用边纠错。
            </p>
            <div class="hero-stats">
              <span><strong>{{ guideCount }}</strong> 篇入门指南</span>
              <span><strong>{{ recipeCount }}</strong> 个可运行配方</span>
              <span class="ai-note">AI 整理 · 试验版</span>
            </div>
          </div>

          <div class="hero-actions">
            <button class="btn btn-primary" type="button" @click="goAsk">
              问大肥鱼
            </button>
            <button class="btn" type="button" @click="goSkills">去技能库</button>
          </div>
        </header>

        <div class="community-tabs">
          <div class="tabs" role="tablist" aria-label="社区内容类型">
            <button
              class="tab"
              :class="{ 'tab--active': activeTab === 'ALL' }"
              type="button"
              role="tab"
              :aria-selected="activeTab === 'ALL'"
              @click="activeTab = 'ALL'"
            >
              全部
            </button>
            <button
              class="tab"
              :class="{ 'tab--active': activeTab === 'RECIPE' }"
              type="button"
              role="tab"
              :aria-selected="activeTab === 'RECIPE'"
              @click="activeTab = 'RECIPE'"
            >
              配方
            </button>
            <button
              class="tab"
              :class="{ 'tab--active': activeTab === 'GUIDE' }"
              type="button"
              role="tab"
              :aria-selected="activeTab === 'GUIDE'"
              @click="activeTab = 'GUIDE'"
            >
              指南
            </button>
          </div>

          <div class="community-search">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true">
              <circle cx="11" cy="11" r="6.5" />
              <path d="m16.2 16.2 4 4" stroke-linecap="round" />
            </svg>
            <input v-model="keyword" type="text" placeholder="搜索配方或问题…" aria-label="搜索社区" />
            <button v-if="keyword" class="clear-btn" type="button" aria-label="清空" @click="keyword = ''">×</button>
          </div>
        </div>

        <div class="community-layout">
          <section class="feed">
            <article v-if="featured" class="featured" role="button" tabindex="0" @click="openGuide(featured.id)" @keydown.enter.prevent="openGuide(featured.id)">
              <div class="featured-text">
                <div class="featured-badges">
                  <span class="featured-label">本周配方</span>
                  <span class="ai-badge">AI 整理</span>
                </div>
                <h2>{{ featured.emoji }} {{ featured.title }}</h2>
                <p>{{ featured.summary }}</p>
                <div class="featured-meta">
                  <span v-if="featured.difficulty">{{ featured.difficulty }}</span>
                  <span v-if="featured.duration">{{ featured.duration }}</span>
                  <span v-if="featured.cost">{{ featured.cost }}</span>
                </div>
              </div>
              <div class="featured-arrow" aria-hidden="true">→</div>
            </article>

            <div v-if="loading" class="feed-grid">
              <div v-for="i in 6" :key="i" class="skeleton card-skeleton"></div>
            </div>

            <div v-else-if="feed.length" class="feed-grid">
              <CommunityGuideCard
                v-for="guide in feed"
                :key="guide.id"
                :guide="guide"
                @open="openGuide"
              />
            </div>

            <div v-else class="empty-block">
              <img class="empty-face" src="/faces/face-calm.png" alt="" />
              <h3>没有匹配的内容</h3>
              <p>换个关键词，或者直接问大肥鱼。</p>
              <button class="btn btn-primary btn-sm empty-action" type="button" @click="goAsk">问大肥鱼</button>
            </div>
          </section>

          <aside class="community-aside">
            <section class="aside-card ask-card">
              <span class="ask-emoji" aria-hidden="true">🐟</span>
              <h2>卡住了？</h2>
              <p>把问题说具体一点，大肥鱼会先基于社区指南给一条最短路径。</p>
              <div class="ask-examples">
                <span>没有显卡能跑吗？</span>
                <span>API Key 怎么填？</span>
                <span>为什么我总报错？</span>
              </div>
              <button class="btn btn-primary ask-btn" type="button" @click="goAsk">问大肥鱼</button>
              <p v-if="!auth.isAuthenticated" class="login-hint">
                登录后才能提问，<button type="button" @click="goLogin">去登录</button>
              </p>
            </section>

            <section v-if="starterGuides.length" class="aside-card">
              <p class="aside-title">新手路线</p>
              <button
                v-for="(guide, index) in starterGuides"
                :key="guide.id"
                class="starter-item"
                type="button"
                @click="openGuide(guide.id)"
              >
                <span class="starter-index">{{ index + 1 }}</span>
                <span class="starter-text">
                  <strong>{{ guide.title }}</strong>
                  <span>{{ guide.duration || '几分钟' }}</span>
                </span>
              </button>
            </section>

            <section class="aside-card aside-note">
              <p class="aside-title">关于内容</p>
              <p>试验期先由 AI 整理基础问题和配方，再由编辑逐步复核。发现错误可以直接点“没帮助”，会进入补充队列。</p>
            </section>
          </aside>
        </div>
      </div>
    </main>

    <AskAiModal v-if="askOpen" @close="askOpen = false" />
  </div>
</template>

<style scoped>
.community-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg);
}

.community-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.community-scroll .page-container {
  padding-top: 26px;
  padding-bottom: 64px;
}

.community-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 7px;
  color: var(--color-primary-strong);
  font-size: 10px;
  font-weight: var(--weight-bold);
  letter-spacing: 2.4px;
}

.community-hero h1 {
  margin: 0;
  color: var(--color-text);
  font-size: var(--text-2xl);
  font-weight: var(--weight-extrabold);
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.hero-sub {
  max-width: 720px;
  margin: 9px 0 0;
  color: var(--color-text-secondary);
  font-size: 13.5px;
  line-height: 1.65;
}

.hero-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 14px;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.hero-stats strong {
  color: var(--color-text);
  font-size: 15px;
  font-weight: var(--weight-bold);
}

.ai-note {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-warning-soft);
  color: #95520a;
  font-size: 10px;
}

.hero-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.community-tabs {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
}

.tabs {
  display: flex;
  gap: 4px;
}

.tab {
  height: 32px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  font-weight: var(--weight-medium);
}

.tab:hover {
  background: var(--color-bg-subtle);
  color: var(--color-text);
}

.tab--active {
  border-color: rgba(18, 183, 245, 0.28);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-weight: var(--weight-semibold);
}

.community-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 260px;
  height: 34px;
  margin-left: auto;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  color: var(--color-text-muted);
}

.community-search:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.community-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--color-text);
  font-size: 12px;
}

.clear-btn {
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(100, 116, 139, 0.16);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1;
}

.community-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
  gap: 24px;
}

/* ---------------- 精选配方 ---------------- */
.featured {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
  padding: 24px;
  overflow: hidden;
  border: 1px solid rgba(18, 183, 245, 0.28);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(60% 120% at 100% 0%, rgba(18, 183, 245, 0.14) 0%, rgba(18, 183, 245, 0) 65%),
    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  cursor: pointer;
  transition:
    border-color var(--transition),
    box-shadow var(--transition);
}

.featured:hover {
  border-color: rgba(18, 183, 245, 0.55);
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.07);
}

.featured-text {
  min-width: 0;
}

.featured-badges {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.featured-label {
  color: var(--color-primary-strong);
  font-size: 10px;
  font-weight: var(--weight-bold);
  letter-spacing: 1.6px;
}

.ai-badge {
  padding: 2px 7px;
  border-radius: var(--radius-pill);
  background: var(--color-warning-soft);
  color: #95520a;
  font-size: 10px;
  font-weight: var(--weight-semibold);
}

.featured h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
  font-weight: var(--weight-bold);
  letter-spacing: -0.01em;
  line-height: 1.35;
}

.featured p {
  display: -webkit-box;
  max-width: 640px;
  margin: 8px 0 0;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.featured-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.featured-arrow {
  margin-left: auto;
  color: var(--color-primary-strong);
  font-size: 24px;
  flex: 0 0 auto;
}

/* ---------------- 信息流 ---------------- */
.feed-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 14px;
}

.card-skeleton {
  height: 186px;
}

.empty-face {
  width: 92px;
  height: 92px;
  object-fit: contain;
  margin-bottom: 4px;
  filter: drop-shadow(0 8px 16px rgba(18, 183, 245, 0.16));
}

.empty-action {
  margin-top: 10px;
}

/* ---------------- 侧栏 ---------------- */
.community-aside {
  position: sticky;
  top: 20px;
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

.ask-card {
  background:
    radial-gradient(90% 100% at 100% 0%, rgba(18, 183, 245, 0.1) 0%, rgba(18, 183, 245, 0) 70%),
    var(--color-panel);
}

.ask-emoji {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--color-primary-softer);
  font-size: 22px;
}

.ask-card h2 {
  margin: 10px 0 0;
  color: var(--color-text);
  font-size: 16px;
  font-weight: var(--weight-bold);
}

.ask-card > p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 11.5px;
  line-height: 1.65;
}

.ask-examples {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 10px;
}

.ask-examples span {
  padding: 2px 7px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-subtle);
  color: var(--color-text-muted);
  font-size: 9.5px;
}

.ask-btn {
  width: 100%;
  margin-top: 12px;
  height: 34px;
  font-size: 12.5px;
}

.login-hint {
  margin: 8px 0 0 !important;
  color: var(--color-text-muted) !important;
  font-size: 10.5px !important;
  text-align: center;
}

.login-hint button {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-primary-strong);
  font-size: 10.5px;
}

.aside-title {
  margin: 0 0 10px;
  color: var(--color-text-muted);
  font-size: 10px;
  font-weight: var(--weight-bold);
  letter-spacing: 1.2px;
  text-transform: uppercase;
}

.starter-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 0;
  border: none;
  border-bottom: 1px solid var(--color-divider);
  background: transparent;
  text-align: left;
}

.starter-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.starter-item:hover .starter-text strong {
  color: var(--color-primary-strong);
}

.starter-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  border-radius: 6px;
  background: var(--color-primary-softer);
  color: var(--color-primary-strong);
  font-size: 10px;
  font-weight: var(--weight-bold);
}

.starter-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.starter-text strong {
  overflow: hidden;
  color: var(--color-text);
  font-size: 11.5px;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--transition);
}

.starter-text span {
  color: var(--color-text-muted);
  font-size: 9.5px;
}

.aside-note p:last-child {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 10.5px;
  line-height: 1.7;
}

@media (max-width: 1080px) {
  .community-layout {
    grid-template-columns: minmax(0, 1fr) 270px;
  }
}

@media (max-width: 920px) {
  .community-layout {
    grid-template-columns: 1fr;
  }

  .community-aside {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ask-card {
    grid-row: span 2;
  }
}

@media (max-width: 720px) {
  .community-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .hero-actions {
    width: 100%;
  }

  .hero-actions .btn {
    flex: 1;
  }

  .community-tabs {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .community-search {
    width: 100%;
    margin-left: 0;
  }

  .featured {
    padding: 18px;
  }

  .featured h2 {
    font-size: 18px;
  }

  .feed-grid {
    grid-template-columns: 1fr;
  }

  .community-aside {
    display: flex;
  }

  .community-scroll .page-container {
    padding-top: 20px;
  }

  .community-hero h1 {
    font-size: 26px;
  }
}
</style>
