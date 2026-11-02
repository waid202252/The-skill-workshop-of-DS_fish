<script setup lang="ts">
/**
 * 修改密码弹窗。
 *
 * 需求来自走查：登录后没有任何改密码入口，用户只能找我改库——
 * 上一轮我甚至用「临时账号借哈希」的土办法给他改过一次，这显然不能算产品功能。
 */
import { computed, ref } from 'vue'
import { authApi } from '@/services/api'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'

const emit = defineEmits<{ close: []; done: [] }>()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const serverError = ref('')
const showPlain = ref(false)

const oldError = computed(() => (oldPassword.value ? '' : '请输入原密码'))
const newError = computed(() => {
  const v = newPassword.value
  if (!v) return '请输入新密码'
  if (v.length < 6 || v.length > 32) return '新密码长度需为 6-32 位'
  if (v === oldPassword.value) return '新密码不能和原密码相同'
  return ''
})
const confirmError = computed(() => {
  if (!confirmPassword.value) return '请再输入一次新密码'
  if (confirmPassword.value !== newPassword.value) return '两次输入的新密码不一致'
  return ''
})

const canSubmit = computed(
  () =>
    !submitting.value &&
    !oldError.value &&
    !newError.value &&
    !confirmError.value,
)

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  serverError.value = ''
  submitting.value = true
  try {
    await authApi.changePassword(oldPassword.value, newPassword.value)
    showToast('密码已修改，下次登录请用新密码', 'success')
    emit('done')
    emit('close')
  } catch (e) {
    serverError.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="mask" @click.self="emit('close')">
      <div class="dialog" role="dialog" aria-modal="true">
      <header class="dialog-head">
        <h2>修改密码</h2>
        <button class="close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <form class="dialog-body" @submit.prevent="submit">
        <label class="field">
          <span class="label">原密码</span>
          <div class="wrap">
            <input
              v-model="oldPassword"
              class="input"
              :class="{ 'is-error': oldPassword && oldError }"
              :type="showPlain ? 'text' : 'password'"
              autocomplete="current-password"
              maxlength="32"
              placeholder="当前使用的密码"
            />
            <button class="toggle" type="button" @click="showPlain = !showPlain">
              {{ showPlain ? '隐藏' : '显示' }}
            </button>
          </div>
          <span v-if="oldPassword && oldError" class="err">{{ oldError }}</span>
        </label>

        <label class="field">
          <span class="label">新密码</span>
          <input
            v-model="newPassword"
            class="input"
            :class="{ 'is-error': newPassword && newError }"
            :type="showPlain ? 'text' : 'password'"
            autocomplete="new-password"
            maxlength="32"
            placeholder="6-32 位，别和原密码一样"
          />
          <span v-if="newPassword && newError" class="err">{{ newError }}</span>
        </label>

        <label class="field">
          <span class="label">确认新密码</span>
          <input
            v-model="confirmPassword"
            class="input"
            :class="{ 'is-error': confirmPassword && confirmError }"
            :type="showPlain ? 'text' : 'password'"
            autocomplete="new-password"
            maxlength="32"
            placeholder="再输一次"
          />
          <span v-if="confirmPassword && confirmError" class="err">{{ confirmError }}</span>
        </label>

        <p v-if="serverError" class="server-error">{{ serverError }}</p>

        <div class="dialog-foot">
          <button class="btn-ghost" type="button" @click="emit('close')">取消</button>
          <button class="btn-primary" type="submit" :disabled="!canSubmit">
            {{ submitting ? '提交中…' : '确认修改' }}
          </button>
        </div>
      </form>
    </div>
  </div>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(16, 30, 43, 0.42);
}

.dialog {
  width: 100%;
  max-width: 400px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(10, 40, 70, 0.24);
}

.dialog-head {
  display: flex;
  align-items: center;
  padding: 16px 18px;
  border-bottom: 1px solid var(--color-border, #eef3f8);
}

.dialog-head h2 {
  margin: 0;
  font-size: 15px;
  color: var(--color-text, #1b2733);
}

.close {
  margin-left: auto;
  border: none;
  background: none;
  font-size: 20px;
  line-height: 1;
  color: var(--color-text-muted, #94a1ae);
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  font-size: 12.5px;
  color: var(--color-text-secondary, #55636f);
}

.wrap {
  position: relative;
  display: flex;
}

.input {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--color-border, #e0e7ef);
  border-radius: 10px;
  font-size: 13px;
  outline: none;
  background: var(--color-bg-subtle);
}

.input:focus {
  border-color: var(--color-primary, #0a7cc4);
  background: #fff;
}

.input.is-error {
  border-color: #e05a5a;
}

.wrap .input {
  padding-right: 52px;
}

.toggle {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  padding: 3px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 11.5px;
  color: var(--color-text-muted, #94a1ae);
}

.err {
  font-size: 11.5px;
  color: #e05a5a;
}

.server-error {
  margin: 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(224, 90, 90, 0.1);
  font-size: 12px;
  color: #c94040;
}

.dialog-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.btn-primary,
.btn-ghost {
  height: 36px;
  padding: 0 18px;
  border-radius: 9px;
  font-size: 13px;
}

.btn-primary {
  border: none;
  background: var(--color-primary-strong);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.btn-primary:disabled {
  opacity: 0.55;
}

.btn-ghost {
  border: 1px solid var(--color-border, #e0e7ef);
  background: #fff;
  color: var(--color-text-secondary, #55636f);
}
</style>
