<script setup lang="ts">
import { computed } from 'vue'
import type { CommunityGuideCardVO } from '@/types/api'

const props = defineProps<{ guide: CommunityGuideCardVO }>()
const emit = defineEmits<{ open: [id: number] }>()

const typeLabel = computed(() => (props.guide.type === 'RECIPE' ? '配方' : '指南'))

function open(): void {
  emit('open', props.guide.id)
}
</script>

<template>
  <article
    class="community-card"
    :class="{ 'community-card--recipe': guide.type === 'RECIPE' }"
    role="button"
    tabindex="0"
    @click="open"
    @keydown.enter.prevent="open"
    @keydown.space.prevent="open"
  >
    <header class="card-top">
      <span class="card-emoji" aria-hidden="true">{{ guide.emoji || '📘' }}</span>
      <span class="type-badge">{{ typeLabel }}</span>
      <span v-if="guide.aiGenerated" class="ai-badge">AI 整理</span>
      <span v-if="guide.helpfulCount > 0" class="helpful">{{ guide.helpfulCount }} 人觉得有用</span>
    </header>

    <h3>{{ guide.title }}</h3>
    <p class="summary">{{ guide.summary }}</p>

    <div v-if="guide.tags.length" class="tags">
      <span v-for="tag in guide.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</span>
    </div>

    <footer class="card-foot">
      <span v-if="guide.difficulty">{{ guide.difficulty }}</span>
      <span v-if="guide.duration">{{ guide.duration }}</span>
      <span v-if="guide.cost">{{ guide.cost }}</span>
      <span class="open-arrow" aria-hidden="true">→</span>
    </footer>
  </article>
</template>

<style scoped>
.community-card {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 186px;
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

.community-card:hover {
  border-color: rgba(18, 183, 245, 0.55);
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.07);
  transform: translateY(-1px);
}

.community-card:focus-visible {
  border-color: var(--color-primary);
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.community-card--recipe {
  background:
    radial-gradient(70% 100% at 100% 0%, rgba(18, 183, 245, 0.06) 0%, rgba(18, 183, 245, 0) 65%),
    var(--color-panel);
}

.card-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.card-emoji {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--color-primary-softer);
  font-size: 18px;
  line-height: 1;
}

.type-badge {
  padding: 2px 7px;
  border-radius: var(--radius-pill);
  background: #eef2f6;
  color: #526173;
  font-size: 10px;
  font-weight: var(--weight-semibold);
}

.community-card--recipe .type-badge {
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.ai-badge {
  padding: 2px 7px;
  border-radius: var(--radius-pill);
  background: var(--color-warning-soft);
  color: #95520a;
  font-size: 10px;
  font-weight: var(--weight-semibold);
}

.helpful {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 10px;
  white-space: nowrap;
}

.community-card h3 {
  margin: 2px 0 0;
  color: var(--color-text);
  font-size: 15px;
  font-weight: var(--weight-semibold);
  line-height: 1.45;
}

.summary {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 9px;
  border-top: 1px solid var(--color-divider);
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.card-foot span {
  white-space: nowrap;
}

.open-arrow {
  margin-left: auto;
  color: var(--color-primary-strong);
  font-size: 13px;
  opacity: 0;
  transform: translateX(-3px);
  transition:
    opacity var(--transition),
    transform var(--transition);
}

.community-card:hover .open-arrow {
  opacity: 1;
  transform: translateX(0);
}
</style>
