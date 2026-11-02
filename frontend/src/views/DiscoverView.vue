<script setup lang="ts">
/**
 * 「发现」页 —— 登录后的首页。
 *
 * 负责「逛」：欢迎区、分类入口、热门推荐、我的收藏。
 * 搜索、分类跳转仍然交给 /skills 处理，本页不重复实现搜索逻辑。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { communityApi, skillApi } from '@/services/api'
import { ws } from '@/services/ws'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import type { CommunityGuideCardVO, SkillCategory, SkillVO } from '@/types/api'
import AppTopbar from '@/components/AppTopbar.vue'
import SkillCard from '@/components/SkillCard.vue'
import CommunityGuideCard from '@/components/CommunityGuideCard.vue'

const auth = useAuthStore()
const chat = useChatStore()
const router = useRouter()

const categories = ref<SkillCategory[]>([])
const trending = ref<SkillVO[]>([])
const installed = ref<SkillVO[]>([])
const recipes = ref<CommunityGuideCardVO[]>([])
const keyword = ref('')
const loading = ref(true)
const pendingId = ref<number | null>(null)

const nickname = computed(() => auth.user?.nickname ?? '朋友')
const installedCount = computed(() => installed.value.length)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [cats, hot, mine, recipeList] = await Promise.all([
      skillApi.categories().catch(() => [] as SkillCategory[]),
      skillApi.trending().catch(() => [] as SkillVO[]),
      skillApi.installed().catch(() => [] as SkillVO[]),
      communityApi.list({ type: 'RECIPE', limit: 3 }).catch(() => [] as CommunityGuideCardVO[]),
    ])
    categories.value = cats
    trending.value = hot
    installed.value = mine
    recipes.value = recipeList
  } finally {
    loading.value = false
  }
}

/** 搜索 → 交给广场页处理 */
function submitSearch(): void {
  const q = keyword.value.trim()
  void router.push({ path: '/skills', query: q ? { q } : {} })
}

function openCategory(cat: SkillCategory): void {
  void router.push({ path: '/skills', query: { q: cat.query } })
}

/** 直接进详情路由，不再经过 ?open= 的二次跳转 */
function openSkill(skill: SkillVO): void {
  void router.push(`/skills/${skill.id}`)
}

async function toggleInstall(skill: SkillVO): Promise<void> {
  if (pendingId.value !== null) return
  pendingId.value = skill.id
  const before = skill.installed
  const beforeCount = skill.installCount
  skill.installed = !before
  skill.installCount = Math.max(0, beforeCount + (before ? -1 : 1))
  try {
    if (before) {
      await skillApi.uninstall(skill.id)
      installed.value = installed.value.filter((s) => s.id !== skill.id)
    } else {
      const updated = await skillApi.install(skill.id)
      skill.installCount = updated.installCount
      if (!installed.value.some((s) => s.id === skill.id)) {
        installed.value = [skill, ...installed.value]
      }
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

function goSkills(): void {
  void router.push('/skills')
}

function goCommunity(): void {
  void router.push('/community')
}

function openGuide(id: number): void {
  void router.push(`/community/${id}`)
}

function handleLogout(): void {
  ws.disconnect()
  chat.reset()
  auth.clearSession()
  void router.replace('/login')
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="discover-page">
    <AppTopbar
      v-model="keyword"
      active="discover"
      search-placeholder="搜索 AI 技能，回车去广场看结果"
      @search="submitSearch"
      @logout="handleLogout"
    />

    <main class="discover-scroll">
      <div class="page-container">
        <section class="welcome">
          <div class="welcome-text">
            <p class="eyebrow">DISCOVER</p>
            <h1>发现值得用的 AI 技能</h1>
            <p class="welcome-sub">
              欢迎回来，<strong>{{ nickname }}</strong>。从 GitHub 精选的 Skill，收藏后就能在工坊里找到。
            </p>

            <div class="stats">
              <div class="stat">
                <strong>{{ categories.length }}</strong>
                <span>推荐分类</span>
              </div>
              <div class="stat">
                <strong>{{ trending.length }}</strong>
                <span>热门技能</span>
              </div>
              <div class="stat">
                <strong>{{ installedCount }}</strong>
                <span>我的收藏</span>
              </div>
            </div>
          </div>

          <div class="welcome-art" aria-hidden="true">
            <span class="art-ring"></span>
            <img src="/mascot.png" alt="" />
          </div>
        </section>

        <section class="block">
          <header class="section-head">
            <h2 class="section-title">按分类逛</h2>
            <span class="section-hint">点一个分类，带你去广场看结果</span>
          </header>

          <div class="cat-grid">
            <button
              v-for="cat in categories"
              :key="cat.key"
              class="cat-card"
              type="button"
              @click="openCategory(cat)"
            >
              <span class="cat-name">{{ cat.label }}</span>
              <span class="cat-key">{{ cat.key }}</span>
              <span class="cat-arrow" aria-hidden="true">→</span>
            </button>
          </div>
          <p v-if="!loading && categories.length === 0" class="inline-empty">
            分类加载失败，稍后重试
          </p>
        </section>

        <section v-if="recipes.length" class="block">
          <header class="section-head">
            <h2 class="section-title">本周配方</h2>
            <span class="section-hint">照着做就能出结果，不用先学完</span>
            <button class="section-action" type="button" @click="goCommunity">
              去社区看看 →
            </button>
          </header>

          <div class="recipe-grid">
            <CommunityGuideCard
              v-for="recipe in recipes"
              :key="recipe.id"
              :guide="recipe"
              @open="openGuide"
            />
          </div>
        </section>

        <section class="block">
          <header class="section-head">
            <h2 class="section-title">热门推荐</h2>
            <span class="section-hint">按 Star 排序，来自本地缓存</span>
            <button class="section-action" type="button" @click="goSkills">
              查看全部 {{ trending.length }} 个 →
            </button>
          </header>

          <div v-if="loading" class="skill-grid">
            <div v-for="i in 6" :key="i" class="skeleton skill-skeleton"></div>
          </div>

          <div v-else-if="trending.length" class="skill-grid">
            <SkillCard
              v-for="skill in trending.slice(0, 12)"
              :key="skill.id"
              :skill="skill"
              :pending="pendingId === skill.id"
              @open="openSkill"
              @toggle="toggleInstall"
            />
          </div>

          <div v-else class="empty-block">
            <img class="empty-face" src="/faces/face-calm.png" alt="" />
            <h3>还没有缓存任何技能</h3>
            <p>去广场搜一次，结果会自动存到这里。</p>
            <button class="btn btn-primary btn-sm empty-action" type="button" @click="goSkills">
              去技能广场
            </button>
          </div>
        </section>

        <section v-if="installed.length" class="block">
          <header class="section-head">
            <h2 class="section-title">我的收藏</h2>
            <span class="section-hint">{{ installedCount }} 个</span>
          </header>

          <div class="saved-row">
            <button
              v-for="skill in installed"
              :key="skill.id"
              class="saved-item"
              type="button"
              @click="openSkill(skill)"
            >
              <span class="saved-name">{{ skill.name }}</span>
              <span class="saved-owner">{{ skill.owner }}</span>
              <span class="saved-arrow" aria-hidden="true">→</span>
            </button>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.discover-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg);
}

.discover-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.page-container {
  padding-top: 24px;
  padding-bottom: 64px;
}

/* ---------------- 欢迎区 ---------------- */
.welcome {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 32px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(60% 120% at 100% 0%, rgba(18, 183, 245, 0.1) 0%, rgba(18, 183, 245, 0) 60%),
    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: var(--shadow-sm);
}

.welcome::after {
  content: '';
  position: absolute;
  right: -70px;
  bottom: -120px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  border: 1px dashed rgba(18, 183, 245, 0.22);
  pointer-events: none;
}

.welcome-text {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--color-primary-strong);
  font-size: 10.5px;
  font-weight: var(--weight-bold);
  letter-spacing: 2.6px;
}

.welcome h1 {
  margin: 0;
  color: var(--color-text);
  font-size: var(--text-2xl);
  font-weight: var(--weight-extrabold);
  letter-spacing: -0.02em;
  line-height: 1.18;
}

.welcome-sub {
  margin: 10px 0 0;
  color: var(--color-text-secondary);
  font-size: 13.5px;
  line-height: 1.65;
}

.welcome-sub strong {
  color: var(--color-text);
  font-weight: var(--weight-semibold);
}

.stats {
  display: flex;
  gap: 28px;
  margin-top: 20px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat strong {
  color: var(--color-text);
  font-size: 21px;
  font-weight: var(--weight-extrabold);
  line-height: 1;
}

.stat span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.welcome-art {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 168px;
  height: 168px;
}

.art-ring {
  position: absolute;
  inset: 4px;
  border-radius: 50%;
  background: rgba(18, 183, 245, 0.06);
  border: 1px dashed rgba(18, 183, 245, 0.28);
}

.welcome-art img {
  position: relative;
  z-index: 1;
  width: 138px;
  height: auto;
  filter: drop-shadow(0 12px 20px rgba(8, 80, 120, 0.16));
}

/* ---------------- 区块 ---------------- */
.block {
  margin-top: 32px;
}

.block .section-head {
  margin-bottom: 14px;
}

/* ---------------- 分类 ---------------- */
.cat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.cat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  text-align: left;
  transition:
    border-color var(--transition),
    box-shadow var(--transition),
    transform var(--transition);
}

.cat-card:hover {
  border-color: rgba(18, 183, 245, 0.6);
  box-shadow: 0 4px 12px rgba(16, 24, 40, 0.06);
  transform: translateY(-1px);
}

.cat-name {
  color: var(--color-text);
  font-size: 13.5px;
  font-weight: var(--weight-semibold);
}

.cat-key {
  overflow: hidden;
  max-width: 100%;
  color: var(--color-text-muted);
  font-size: 10.5px;
  letter-spacing: 0.6px;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.cat-arrow {
  position: absolute;
  top: 14px;
  right: 14px;
  color: var(--color-text-muted);
  font-size: 13px;
  opacity: 0;
  transform: translateX(-3px);
  transition:
    opacity var(--transition),
    transform var(--transition),
    color var(--transition);
}

.cat-card:hover .cat-arrow {
  color: var(--color-primary-strong);
  opacity: 1;
  transform: translateX(0);
}

/* ---------------- 技能网格 ---------------- */
.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(272px, 1fr));
  gap: 14px;
}

.recipe-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.skill-skeleton {
  height: 196px;
}

.inline-empty {
  margin: 10px 0 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

/* ---------------- 空状态 ---------------- */
.empty-face {
  width: 96px;
  height: 96px;
  object-fit: contain;
  margin-bottom: 4px;
  filter: drop-shadow(0 8px 16px rgba(18, 183, 245, 0.16));
}

.empty-action {
  margin-top: 12px;
}

/* ---------------- 收藏 ---------------- */
.saved-row {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 6px;
}

.saved-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  flex: 0 0 auto;
  min-width: 168px;
  padding: 12px 34px 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  text-align: left;
  transition:
    border-color var(--transition),
    background var(--transition);
}

.saved-item:hover {
  border-color: rgba(18, 183, 245, 0.6);
  background: #fbfdff;
}

.saved-name {
  overflow: hidden;
  max-width: 160px;
  color: var(--color-text);
  font-size: 12.5px;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.saved-owner {
  overflow: hidden;
  max-width: 160px;
  color: var(--color-text-muted);
  font-size: 10.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.saved-arrow {
  position: absolute;
  top: 50%;
  right: 12px;
  color: var(--color-text-muted);
  font-size: 12px;
  transform: translateY(-50%);
}

@media (max-width: 860px) {
  .welcome {
    padding: 24px;
  }

  .welcome-art {
    display: none;
  }

  .welcome h1 {
    font-size: 28px;
  }

  .cat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .page-container {
    padding-top: 16px;
  }

  .stats {
    gap: 20px;
  }

  .cat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .section-hint {
    display: none;
  }

  .skill-grid {
    grid-template-columns: 1fr;
  }
}
</style>
