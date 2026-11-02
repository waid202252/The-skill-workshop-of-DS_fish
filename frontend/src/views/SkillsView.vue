<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { skillApi } from '@/services/api'
import { ws } from '@/services/ws'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import type { SkillCategory, SkillVO } from '@/types/api'
import AppTopbar from '@/components/AppTopbar.vue'
import SkillCard from '@/components/SkillCard.vue'
import SkillRow from '@/components/SkillRow.vue'

type Tab = 'trending' | 'installed'
type ViewMode = 'grid' | 'list'

const auth = useAuthStore()
const chat = useChatStore()
const router = useRouter()
const route = useRoute()

const tab = ref<Tab>('trending')
const viewMode = ref<ViewMode>('grid')
const keyword = ref('')
const skills = ref<SkillVO[]>([])
const loading = ref(false)
const searched = ref(false)
const notice = ref<string | null>(null)
const cached = ref(false)
const total = ref(0)
const pendingId = ref<number | null>(null)
/** 搜索结果当前页码：用于「加载更多」 */
const page = ref(1)
const loadingMore = ref(false)

const categories = ref<SkillCategory[]>([])
const activeCategory = ref<string | null>(null)

const headerHint = computed(() => {
  if (searched.value) return `匹配 ${total.value.toLocaleString()} 个仓库${cached.value ? '（本地缓存）' : ''}`
  if (tab.value === 'installed') return `已安装 ${skills.value.length} 个技能`
  return '按 Star 排序的推荐技能'
})

const sortHint = computed(() => {
  if (tab.value === 'installed' && !searched.value) return '最近收藏优先'
  return '按 Star 排序'
})

const emptyTitle = computed(() => {
  if (tab.value === 'installed' && !searched.value) return '还没装过任何技能'
  return '这次什么都没捞着'
})

const emptyHint = computed(() => {
  if (tab.value === 'installed' && !searched.value) return '去「推荐」里挑一个，收藏后会出现在这里。'
  return '试试搜索「ai video」「agent skill」「text to image」'
})

const resultLabel = computed(() =>
  searched.value ? headerHint.value : `共 ${skills.value.length} 个技能`,
)

async function loadTrending(): Promise<void> {
  loading.value = true
  notice.value = null
  searched.value = false
  cached.value = false
  try {
    skills.value = await skillApi.trending()
    total.value = skills.value.length
  } catch (error) {
    notice.value = errorMessage(error)
    skills.value = []
  } finally {
    loading.value = false
  }
}

async function loadInstalled(): Promise<void> {
  loading.value = true
  notice.value = null
  searched.value = false
  cached.value = false
  try {
    skills.value = await skillApi.installed()
    total.value = skills.value.length
  } catch (error) {
    notice.value = errorMessage(error)
    skills.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 搜索是「按回车/点按钮」触发的，不是输入即搜。
 * 原因：GitHub 未认证配额只有 10 次/分钟，输入即搜几秒就会被打满。
 */
async function submitSearch(fromCategory = false): Promise<void> {
  if (!fromCategory) {
    activeCategory.value = null
  }
  const q = keyword.value.trim()
  if (!q) {
    tab.value = 'trending'
    await loadTrending()
    return
  }
  loading.value = true
  notice.value = null
  try {
    const result = await skillApi.search(q, 1, 24)
    skills.value = result.items
    total.value = result.total
    cached.value = result.cached
    page.value = 1
    searched.value = true
    notice.value = result.degraded ? result.notice : null
  } catch (error) {
    notice.value = errorMessage(error)
    skills.value = []
  } finally {
    loading.value = false
  }
}

/** 加载下一页并追加 */
async function loadMore(): Promise<void> {
  const q = keyword.value.trim()
  if (!q || loadingMore.value) return
  loadingMore.value = true
  try {
    const next = page.value + 1
    const result = await skillApi.search(q, next, 24)
    skills.value = [...skills.value, ...result.items]
    page.value = next
    total.value = result.total
  } catch (error) {
    showToast(errorMessage(error), 'error')
  } finally {
    loadingMore.value = false
  }
}

async function switchTab(next: Tab): Promise<void> {
  if (tab.value === next && !searched.value) return
  tab.value = next
  keyword.value = ''
  activeCategory.value = null
  if (next === 'trending') await loadTrending()
  else await loadInstalled()
}

/** 安装/卸载：先本地翻转，失败再回滚 */
async function toggleInstall(skill: SkillVO): Promise<void> {
  if (pendingId.value !== null) return
  pendingId.value = skill.id
  const before = skill.installed
  const beforeCount = skill.installCount
  skill.installed = !before
  skill.installCount = before ? Math.max(0, beforeCount - 1) : beforeCount + 1
  try {
    if (before) {
      await skillApi.uninstall(skill.id)
      showToast(`已取消收藏 ${skill.name}`, 'info')
      if (tab.value === 'installed' && !searched.value) {
        skills.value = skills.value.filter((item) => item.id !== skill.id)
      }
    } else {
      const updated = await skillApi.install(skill.id)
      skill.installCount = updated.installCount
      showToast(`已收藏 ${skill.name}`, 'success')
    }
  } catch (error) {
    skill.installed = before
    skill.installCount = beforeCount
    showToast(errorMessage(error), 'error')
  } finally {
    pendingId.value = null
  }
}

function openRepo(skill: SkillVO): void {
  window.open(skill.htmlUrl, '_blank', 'noopener')
}

function handleLogout(): void {
  ws.disconnect()
  chat.reset()
  auth.clearSession()
  void router.replace('/login')
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = await skillApi.categories()
  } catch {
    categories.value = []
  }
}

/** 点分类 = 用一个预设查询去搜；再点一次取消 */
async function selectCategory(category: SkillCategory): Promise<void> {
  if (activeCategory.value === category.key) {
    await clearCategory()
    return
  }
  activeCategory.value = category.key
  keyword.value = category.query
  await submitSearch(true)
}

async function clearCategory(): Promise<void> {
  activeCategory.value = null
  keyword.value = ''
  tab.value = 'trending'
  await loadTrending()
}

/** 只要用户手动动了输入框，分类高亮立刻取消 */
function onKeywordInput(): void {
  if (activeCategory.value !== null) {
    activeCategory.value = null
  }
}

function openDetail(skill: SkillVO): void {
  void router.push(`/skills/${skill.id}`)
}

onMounted(async () => {
  void loadCategories()
  await loadTrending()

  // 兼容从「发现」页带参数跳进来：?q=关键词 或 ?open=技能id
  const q = typeof route.query.q === 'string' ? route.query.q : ''
  const open = typeof route.query.open === 'string' ? Number(route.query.open) : 0

  if (q.trim()) {
    keyword.value = q
    searched.value = false
    await submitSearch()
  }
  if (Number.isFinite(open) && open > 0) {
    try {
      const skill = await skillApi.detail(open)
      openDetail(skill)
    } catch {
      // 技能不存在就当没传，不影响页面
    }
  }
})
</script>

<template>
  <div class="skills-page">
    <AppTopbar
      v-model="keyword"
      active="skills"
      :search-disabled="loading"
      search-placeholder="搜索 AI 技能，例如 ai video generation"
      @input="onKeywordInput"
      @search="submitSearch()"
      @logout="handleLogout"
    />

    <div class="skills-body">
      <!-- 左侧筛选栏：参考 Modrinth 的 discover 布局 -->
      <aside class="filters">
        <div class="filter-block">
          <p class="filter-title">浏览</p>
          <div class="filter-list">
            <button
              class="filter-item"
              :class="{ 'filter-item--active': tab === 'trending' && !searched }"
              type="button"
              @click="switchTab('trending')"
            >
              推荐
            </button>
            <button
              class="filter-item"
              :class="{ 'filter-item--active': tab === 'installed' && !searched }"
              type="button"
              @click="switchTab('installed')"
            >
              已安装
            </button>
          </div>
        </div>

        <div v-if="categories.length" class="filter-block">
          <p class="filter-title">分类</p>
          <div class="filter-list">
            <button
              class="filter-item"
              :class="{ 'filter-item--active': activeCategory === null }"
              type="button"
              @click="clearCategory"
            >
              全部
            </button>
            <button
              v-for="cat in categories"
              :key="cat.key"
              class="filter-item"
              :class="{ 'filter-item--active': activeCategory === cat.key }"
              type="button"
              :title="cat.query"
              @click="selectCategory(cat)"
            >
              {{ cat.label }}
            </button>
          </div>
        </div>

        <p class="filter-note">
          GitHub 搜索有配额限制，按回车或点「搜索」才会发起请求；分类会复用同一套搜索。
        </p>
      </aside>

      <main class="results">
        <header class="results-head">
          <div class="results-title">
            <p class="eyebrow">SKILLS</p>
            <h1>技能广场</h1>
            <p>从 GitHub 搜索、收藏并管理你的 AI 技能。</p>
          </div>

          <div class="view-toggle" role="group" aria-label="切换视图">
            <button
              class="view-btn"
              :class="{ 'view-btn--active': viewMode === 'grid' }"
              type="button"
              title="卡片视图"
              aria-label="卡片视图"
              @click="viewMode = 'grid'"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                <rect x="4" y="4" width="6.5" height="6.5" rx="1.4" />
                <rect x="13.5" y="4" width="6.5" height="6.5" rx="1.4" />
                <rect x="4" y="13.5" width="6.5" height="6.5" rx="1.4" />
                <rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.4" />
              </svg>
            </button>
            <button
              class="view-btn"
              :class="{ 'view-btn--active': viewMode === 'list' }"
              type="button"
              title="列表视图"
              aria-label="列表视图"
              @click="viewMode = 'list'"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                <path d="M4 6h16M4 12h16M4 18h16" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </header>

        <!-- 移动端：顶栏搜索隐藏后仍要能搜 -->
        <div class="mobile-search">
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索 AI 技能…"
            @input="onKeywordInput"
            @keyup.enter="submitSearch()"
          />
          <button class="btn btn-primary btn-sm" type="button" :disabled="loading" @click="submitSearch()">
            {{ loading ? '搜索中…' : '搜索' }}
          </button>
        </div>

        <div class="mobile-tabs">
          <button
            class="mobile-tab"
            :class="{ 'mobile-tab--active': tab === 'trending' && !searched }"
            type="button"
            @click="switchTab('trending')"
          >
            推荐
          </button>
          <button
            class="mobile-tab"
            :class="{ 'mobile-tab--active': tab === 'installed' && !searched }"
            type="button"
            @click="switchTab('installed')"
          >
            已安装
          </button>
        </div>

        <div v-if="categories.length" class="mobile-cats">
          <button
            class="mobile-cat"
            :class="{ 'mobile-cat--active': activeCategory === null }"
            type="button"
            @click="clearCategory"
          >
            全部
          </button>
          <button
            v-for="cat in categories"
            :key="cat.key"
            class="mobile-cat"
            :class="{ 'mobile-cat--active': activeCategory === cat.key }"
            type="button"
            @click="selectCategory(cat)"
          >
            {{ cat.label }}
          </button>
        </div>

        <div class="result-bar">
          <span class="result-hint">{{ resultLabel }}</span>
          <span class="sort-hint">{{ sortHint }}</span>
        </div>

        <p v-if="notice" class="notice">{{ notice }}</p>

        <div v-if="loading" class="skill-grid">
          <div v-for="i in 6" :key="i" class="skeleton result-skeleton"></div>
        </div>

        <div v-else-if="skills.length === 0" class="empty-block">
          <img class="state-face" src="/faces/face-grumpy.png" alt="" />
          <h3>{{ emptyTitle }}</h3>
          <p>{{ emptyHint }}</p>
        </div>

        <div v-else-if="viewMode === 'grid'" class="skill-grid">
          <SkillCard
            v-for="skill in skills"
            :key="skill.id"
            :skill="skill"
            :pending="pendingId === skill.id"
            @open="openDetail"
            @toggle="toggleInstall"
          />
        </div>

        <div v-else class="skill-list">
          <SkillRow
            v-for="skill in skills"
            :key="skill.id"
            :skill="skill"
            :pending="pendingId === skill.id"
            @open="openDetail"
            @toggle="toggleInstall"
            @open-repo="openRepo"
          />
        </div>

        <div v-if="searched && skills.length > 0 && skills.length < total" class="more-wrap">
          <button class="btn btn-sm" type="button" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : `加载更多（已显示 ${skills.length} / ${total}）` }}
          </button>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.skills-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg);
}

.skills-body {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 32px;
  flex: 1;
  width: 100%;
  max-width: 1280px;
  min-height: 0;
  margin: 0 auto;
  padding: 0 24px;
}

/* ---------------- 筛选栏 ---------------- */
.filters {
  min-height: 0;
  padding: 28px 20px 32px 0;
  overflow-y: auto;
  border-right: 1px solid var(--color-border);
}

.filter-block + .filter-block {
  margin-top: 26px;
}

.filter-title {
  margin: 0 0 8px;
  color: var(--color-text-muted);
  font-size: 10.5px;
  font-weight: var(--weight-bold);
  letter-spacing: 1.4px;
  text-transform: uppercase;
}

.filter-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.filter-item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: left;
  transition:
    background var(--transition),
    color var(--transition);
}

.filter-item:hover {
  background: rgba(148, 163, 184, 0.12);
  color: var(--color-text);
}

.filter-item--active {
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-weight: var(--weight-semibold);
}

.filter-note {
  margin: 28px 0 0;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.56);
  color: var(--color-text-muted);
  font-size: 11.5px;
  line-height: 1.65;
}

/* ---------------- 结果区 ---------------- */
.results {
  min-height: 0;
  padding: 28px 2px 56px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.results-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.eyebrow {
  margin: 0 0 7px;
  color: var(--color-primary-strong);
  font-size: 10px;
  font-weight: var(--weight-bold);
  letter-spacing: 2.4px;
}

.results-title h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 30px;
  font-weight: var(--weight-extrabold);
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.results-title p:last-child {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.view-toggle {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel);
}

.view-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  transition:
    background var(--transition),
    color var(--transition);
}

.view-btn:hover {
  color: var(--color-text);
}

.view-btn--active {
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.mobile-search,
.mobile-tabs,
.mobile-cats {
  display: none;
}

.result-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.result-hint {
  color: var(--color-text-secondary);
  font-size: 12.5px;
}

.sort-hint {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.notice {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid rgba(224, 131, 37, 0.28);
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: #8a4b08;
  font-size: 12px;
  line-height: 1.55;
}

.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.result-skeleton {
  height: 196px;
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.more-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.state-face {
  width: 104px;
  height: 104px;
  object-fit: contain;
  margin-bottom: 4px;
  filter: drop-shadow(0 8px 16px rgba(18, 183, 245, 0.16));
}

@media (max-width: 1080px) {
  .skills-body {
    grid-template-columns: 200px minmax(0, 1fr);
    gap: 24px;
  }

  .skill-grid {
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  }
}

@media (max-width: 900px) {
  .skills-body {
    display: block;
    padding: 0 16px;
    overflow-y: auto;
  }

  .filters {
    display: none;
  }

  .results {
    padding: 20px 0 48px;
    overflow: visible;
  }

  .mobile-search {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }

  .mobile-search input {
    flex: 1;
    min-width: 0;
    height: 40px;
    padding: 0 12px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    outline: none;
    background: var(--color-panel);
    color: var(--color-text);
    font-size: 13px;
  }

  .mobile-search input:focus {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px var(--color-primary-soft);
  }

  .mobile-tabs,
  .mobile-cats {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .mobile-tabs {
    margin-bottom: 8px;
  }

  .mobile-cats {
    margin-bottom: 12px;
  }

  .mobile-tab,
  .mobile-cat {
    flex: 0 0 auto;
    height: 30px;
    padding: 0 12px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-pill);
    background: var(--color-panel);
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  .mobile-tab--active,
  .mobile-cat--active {
    border-color: rgba(18, 183, 245, 0.55);
    background: var(--color-primary-soft);
    color: var(--color-primary-strong);
    font-weight: var(--weight-semibold);
  }
}

@media (max-width: 620px) {
  .results-title h1 {
    font-size: 26px;
  }

  .results-title p:last-child {
    display: none;
  }

  .skill-grid {
    grid-template-columns: 1fr;
  }

  .result-bar {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .sort-hint {
    margin-left: 0;
  }
}
</style>
