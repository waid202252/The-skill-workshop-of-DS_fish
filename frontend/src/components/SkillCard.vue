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
}>()

function open(): void {
  emit('open', props.skill)
}
</script>

<template>
  <article
    class="skill-card"
    role="button"
    tabindex="0"
    @click="open"
    @keydown.enter.prevent="open"
    @keydown.space.prevent="open"
  >
    <header class="card-head">
      <img v-if="skill.ownerAvatar" class="owner-avatar" :src="skill.ownerAvatar" :alt="skill.owner" loading="lazy" />
      <span
        v-else
        class="owner-avatar owner-avatar--fallback"
        :style="{ background: avatarColor(skill.owner) }"
        aria-hidden="true"
      >
        {{ avatarLetter(skill.owner) }}
      </span>

      <div class="card-titles">
        <h3 :title="skill.fullName">{{ skill.name }}</h3>
        <p>by {{ skill.owner }}</p>
      </div>

      <button
        class="install-btn"
        :class="{ 'install-btn--on': skill.installed }"
        type="button"
        :disabled="pending"
        @click.stop="emit('toggle', skill)"
      >
        {{ pending ? '处理中' : skill.installed ? '已收藏' : '收藏' }}
      </button>
    </header>

    <p class="card-desc">{{ skill.description || '这个仓库还没有填写简介' }}</p>

    <div v-if="skill.topics.length" class="card-tags">
      <span v-for="topic in skill.topics.slice(0, 3)" :key="topic" class="tag">{{ topic }}</span>
    </div>
    <div v-else class="card-tags-spacer"></div>

    <footer class="card-foot">
      <span class="metric" title="GitHub Star">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path d="m12 3.6 2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8L12 3.6Z" stroke-linejoin="round" />
        </svg>
        {{ formatCompact(skill.stars) }}
      </span>
      <span class="metric" title="收藏人数">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path d="M6.5 4.5h11a1 1 0 0 1 1 1v15l-6.5-4.2-6.5 4.2v-15a1 1 0 0 1 1-1Z" stroke-linejoin="round" />
        </svg>
        {{ formatCompact(skill.installCount) }}
      </span>
      <span v-if="skill.language" class="tag tag--lang">{{ skill.language }}</span>
    </footer>
  </article>
</template>

<style scoped>
.skill-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 196px;
  padding: 16px;
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

.skill-card:hover {
  border-color: rgba(18, 183, 245, 0.55);
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.07);
  transform: translateY(-1px);
}

.skill-card:focus-visible {
  border-color: var(--color-primary);
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.owner-avatar {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border-radius: 12px;
  object-fit: cover;
  background: var(--color-bg);
}

.owner-avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: var(--weight-bold);
}

.card-titles {
  flex: 1;
  min-width: 0;
  line-height: 1.3;
}

.card-titles h3 {
  margin: 0;
  overflow: hidden;
  color: var(--color-text);
  font-size: 14.5px;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-titles p {
  margin: 3px 0 0;
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.install-btn {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--color-primary-strong);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-primary-strong);
  font-size: 12px;
  font-weight: var(--weight-semibold);
  transition:
    background var(--transition),
    border-color var(--transition),
    color var(--transition);
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

.card-desc {
  display: -webkit-box;
  min-height: 42px;
  margin: 0;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
  overflow: hidden;
}

.card-tags-spacer {
  min-height: 22px;
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--color-divider);
}

.metric {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-muted);
  font-size: 11.5px;
  white-space: nowrap;
}

.metric svg {
  color: var(--color-text-muted);
}

.metric:first-child svg {
  color: #d9a43a;
}

.tag--lang {
  margin-left: auto;
  max-width: 96px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
}
</style>
