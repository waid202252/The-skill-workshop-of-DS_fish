<script setup lang="ts">
import type { SkillVO } from '@/types/api'
import { avatarColor, avatarLetter, formatCompact } from '@/utils/format'

const props = defineProps<{
  skill: SkillVO
  pending: boolean
}>()

const emit = defineEmits<{
  open: [skill: SkillVO]
  toggle: [skill: SkillVO]
  'open-repo': [skill: SkillVO]
}>()

function open(): void {
  emit('open', props.skill)
}
</script>

<template>
  <article
    class="skill-row"
    role="button"
    tabindex="0"
    @click="open"
    @keydown.enter.prevent="open"
    @keydown.space.prevent="open"
  >
    <img v-if="skill.ownerAvatar" class="row-avatar" :src="skill.ownerAvatar" :alt="skill.owner" loading="lazy" />
    <span
      v-else
      class="row-avatar row-avatar--fallback"
      :style="{ background: avatarColor(skill.owner) }"
      aria-hidden="true"
    >
      {{ avatarLetter(skill.owner) }}
    </span>

    <div class="row-main">
      <div class="row-title">
        <h3 :title="skill.fullName">{{ skill.name }}</h3>
        <span class="row-by">by {{ skill.owner }}</span>
      </div>
      <p class="row-desc">{{ skill.description || '这个仓库还没有填写简介' }}</p>
      <div v-if="skill.topics.length" class="row-tags">
        <span v-for="topic in skill.topics.slice(0, 5)" :key="topic" class="tag">{{ topic }}</span>
      </div>
    </div>

    <div class="row-stats">
      <span class="stat">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path d="m12 3.6 2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8L12 3.6Z" stroke-linejoin="round" />
        </svg>
        {{ formatCompact(skill.stars) }}
      </span>
      <span class="stat">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path d="M6.5 4.5h11a1 1 0 0 1 1 1v15l-6.5-4.2-6.5 4.2v-15a1 1 0 0 1 1-1Z" stroke-linejoin="round" />
        </svg>
        {{ formatCompact(skill.installCount) }} 人收藏
      </span>
      <span class="stat stat--muted">{{ skill.language || '未标注语言' }}</span>
    </div>

    <div class="row-actions">
      <button
        class="install-btn"
        :class="{ 'install-btn--on': skill.installed }"
        type="button"
        :disabled="pending"
        @click.stop="emit('toggle', skill)"
      >
        {{ pending ? '处理中' : skill.installed ? '已收藏' : '收藏' }}
      </button>
      <button
        class="repo-btn"
        type="button"
        title="在 GitHub 打开"
        aria-label="在 GitHub 打开"
        @click.stop="emit('open-repo', skill)"
      >
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path d="M7 17 17 7M9 7h8v8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>
  </article>
</template>

<style scoped>
.skill-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-height: 108px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition:
    border-color var(--transition),
    box-shadow var(--transition),
    transform var(--transition);
}

.skill-row:hover {
  border-color: rgba(18, 183, 245, 0.55);
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.07);
  transform: translateY(-1px);
}

.skill-row:focus-visible {
  border-color: var(--color-primary);
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.row-avatar {
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  border-radius: 12px;
  object-fit: cover;
  background: var(--color-bg);
}

.row-avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 19px;
  font-weight: var(--weight-bold);
}

.row-main {
  flex: 1;
  min-width: 0;
  padding-top: 1px;
}

.row-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.row-title h3 {
  margin: 0;
  overflow: hidden;
  color: var(--color-text);
  font-size: 16px;
  font-weight: var(--weight-semibold);
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-by {
  flex: 0 0 auto;
  color: var(--color-text-muted);
  font-size: 12px;
}

.row-desc {
  display: -webkit-box;
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.row-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.row-stats {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 7px;
  flex: 0 0 auto;
  min-width: 118px;
  padding-top: 3px;
}

.stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.stat svg {
  color: var(--color-text-muted);
}

.stat:first-child svg {
  color: #d9a43a;
}

.stat--muted {
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.row-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex: 0 0 auto;
  padding-top: 2px;
}

.install-btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--color-primary-strong);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-primary-strong);
  font-size: 12px;
  font-weight: var(--weight-semibold);
  white-space: nowrap;
  transition:
    background var(--transition),
    color var(--transition),
    border-color var(--transition);
}

.install-btn:hover:not(:disabled) {
  background: var(--color-primary-softer);
}

.install-btn--on {
  border-color: var(--color-border);
  background: var(--color-bg-subtle);
  color: var(--color-text-muted);
}

.install-btn:disabled {
  cursor: wait;
  opacity: 0.6;
}

.repo-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-text-muted);
  transition:
    border-color var(--transition),
    color var(--transition);
}

.repo-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
}

@media (max-width: 760px) {
  .row-stats {
    display: none;
  }

  .row-avatar {
    width: 40px;
    height: 40px;
  }
}
</style>
