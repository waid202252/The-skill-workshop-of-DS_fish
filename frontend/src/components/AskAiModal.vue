<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { communityApi } from '@/services/api'
import { errorMessage } from '@/utils/error'
import type { CommunityAskVO } from '@/types/api'
import MarkdownView from './MarkdownView.vue'

const emit = defineEmits<{ close: [] }>()
const router = useRouter()

const question = ref('')
const loading = ref(false)
const result = ref<CommunityAskVO | null>(null)
const error = ref('')
const feedback = ref<boolean | null>(null)
const feedbackPending = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const suggestions = [
  '没有显卡能玩 AI 技能吗？',
  'API Key 到底填在哪里？',
  'Agent 和 RAG 有什么区别？',
  '为什么别人能跑通我却报错？',
]

const canAsk = computed(() => question.value.trim().length >= 2 && !loading.value)

async function ask(): Promise<void> {
  if (!canAsk.value) return
  loading.value = true
  error.value = ''
  result.value = null
  feedback.value = null
  try {
    result.value = await communityApi.ask(question.value.trim())
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function useSuggestion(text: string): void {
  question.value = text
  void nextTick(() => textareaRef.value?.focus())
}

async function sendFeedback(helpful: boolean): Promise<void> {
  if (!result.value || feedbackPending.value) return
  feedbackPending.value = true
  try {
    await communityApi.questionFeedback(result.value.questionId, helpful)
    feedback.value = helpful
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    feedbackPending.value = false
  }
}

function reset(): void {
  result.value = null
  feedback.value = null
  error.value = ''
  question.value = ''
  void nextTick(() => textareaRef.value?.focus())
}

function openGuide(id: number): void {
  emit('close')
  void router.push(`/community/${id}`)
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    void ask()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  void nextTick(() => textareaRef.value?.focus())
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="ask-mask" @click.self="emit('close')">
      <section class="ask-card" role="dialog" aria-modal="true" aria-label="问大肥鱼">
        <header class="ask-head">
          <div class="ask-title">
            <span class="ask-avatar" aria-hidden="true">🐟</span>
            <div>
              <h2>问大肥鱼</h2>
              <p>基于工坊指南回答，不确定会明确说</p>
            </div>
          </div>
          <button class="ask-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="ask-body">
          <template v-if="!result">
            <p class="ask-intro">把问题说具体一点：想做什么、卡在哪一步、报错是什么。</p>

            <div class="suggestions">
              <button
                v-for="item in suggestions"
                :key="item"
                class="suggestion"
                type="button"
                @click="useSuggestion(item)"
              >
                {{ item }}
              </button>
            </div>

            <textarea
              ref="textareaRef"
              v-model="question"
              class="ask-textarea"
              rows="4"
              maxlength="500"
              placeholder="例如：我只有轻薄本，没有显卡，能跑 ComfyUI 吗？"
            ></textarea>

            <div class="ask-actions">
              <span class="ask-hint">Ctrl / ⌘ + Enter 发送</span>
              <button class="btn btn-primary btn-sm" type="button" :disabled="!canAsk" @click="ask">
                {{ loading ? '思考中…' : '问一下' }}
              </button>
            </div>
          </template>

          <template v-else>
            <div class="question-echo">
              <span>你问</span>
              <p>{{ question }}</p>
            </div>

            <div class="answer-meta">
              <span class="ai-label">{{ result.mock ? 'AI 模拟模式' : 'AI 首答' }}</span>
              <span class="ai-note">基于社区指南 · 仅供参考</span>
            </div>

            <MarkdownView :source="result.answer" class="answer" />

            <div v-if="result.relatedGuides.length" class="related">
              <p class="related-title">可以继续看</p>
              <button
                v-for="guide in result.relatedGuides"
                :key="guide.id"
                class="related-item"
                type="button"
                @click="openGuide(guide.id)"
              >
                <span class="related-emoji">{{ guide.emoji || '📘' }}</span>
                <span class="related-text">
                  <strong>{{ guide.title }}</strong>
                  <span>{{ guide.summary }}</span>
                </span>
                <span class="related-arrow">→</span>
              </button>
            </div>

            <div class="feedback-row">
              <template v-if="feedback === null">
                <span>这个回答解决你的问题了吗？</span>
                <button class="feedback-btn" type="button" :disabled="feedbackPending" @click="sendFeedback(true)">
                  有帮助
                </button>
                <button class="feedback-btn" type="button" :disabled="feedbackPending" @click="sendFeedback(false)">
                  没解决
                </button>
              </template>
              <span v-else class="feedback-done">
                {{ feedback ? '已记录，谢谢反馈。' : '已记录，编辑会补充这篇内容。' }}
              </span>
            </div>

            <div class="ask-actions ask-actions--after">
              <button class="btn btn-sm" type="button" @click="reset">再问一个</button>
              <button class="btn btn-primary btn-sm" type="button" @click="emit('close')">知道了</button>
            </div>
          </template>

          <p v-if="error" class="ask-error">{{ error }}</p>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ask-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(17, 24, 39, 0.44);
  animation: fade-in 0.16s ease;
}

.ask-card {
  display: flex;
  flex-direction: column;
  width: 620px;
  max-width: 100%;
  max-height: min(760px, 90vh);
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-panel);
  box-shadow: var(--shadow-lg);
}

.ask-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.ask-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ask-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--color-primary-softer);
  font-size: 20px;
}

.ask-title h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 15px;
  font-weight: var(--weight-bold);
}

.ask-title p {
  margin: 2px 0 0;
  color: var(--color-text-muted);
  font-size: 11px;
}

.ask-close {
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 20px;
  line-height: 1;
}

.ask-close:hover {
  background: var(--color-bg-subtle);
}

.ask-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px 20px;
}

.ask-intro {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  line-height: 1.6;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.suggestion {
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 11px;
}

.suggestion:hover {
  border-color: rgba(18, 183, 245, 0.5);
  background: var(--color-primary-softer);
  color: var(--color-primary-strong);
}

.ask-textarea {
  display: block;
  width: 100%;
  min-height: 112px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  resize: vertical;
  background: var(--color-panel);
  color: var(--color-text);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.65;
}

.ask-textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.ask-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.ask-actions--after {
  justify-content: flex-end;
  margin-top: 16px;
}

.ask-hint {
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.question-echo {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}

.question-echo span {
  flex: 0 0 auto;
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.7;
}

.question-echo p {
  margin: 0;
  color: var(--color-text);
  font-size: 12.5px;
  line-height: 1.7;
  word-break: break-word;
}

.answer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-label {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 10px;
  font-weight: var(--weight-semibold);
}

.ai-note {
  color: var(--color-text-muted);
  font-size: 10px;
}

.answer {
  margin-top: 4px;
}

.related {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-divider);
}

.related-title {
  margin: 0 0 8px;
  color: var(--color-text-muted);
  font-size: 10.5px;
  font-weight: var(--weight-bold);
  letter-spacing: 1px;
  text-transform: uppercase;
}

.related-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  text-align: left;
}

.related-item + .related-item {
  margin-top: 6px;
}

.related-item:hover {
  border-color: rgba(18, 183, 245, 0.5);
  background: var(--color-bg-subtle);
}

.related-emoji {
  flex: 0 0 auto;
  font-size: 18px;
}

.related-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.related-text strong {
  overflow: hidden;
  color: var(--color-text);
  font-size: 12px;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-text span {
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 10.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-arrow {
  margin-left: auto;
  color: var(--color-text-muted);
}

.feedback-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-divider);
  color: var(--color-text-muted);
  font-size: 11px;
}

.feedback-btn {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-panel);
  color: var(--color-text-secondary);
  font-size: 10.5px;
}

.feedback-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
}

.feedback-done {
  color: var(--color-success);
}

.ask-error {
  margin: 12px 0 0;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: 11.5px;
}

@media (max-width: 620px) {
  .ask-mask {
    padding: 12px;
  }

  .ask-card {
    max-height: 92vh;
  }

  .ask-head,
  .ask-body {
    padding-left: 14px;
    padding-right: 14px;
  }
}
</style>
