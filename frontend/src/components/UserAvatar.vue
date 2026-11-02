<script setup lang="ts">
import { computed } from 'vue'
import { avatarColor, avatarLetter } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name: string
    size?: number
    /** 是否展示在线状态小圆点；null / undefined 表示不展示 */
    online?: boolean | null
    /** 圆角形状，群头像用方形 */
    square?: boolean
  }>(),
  {
    src: null,
    size: 40,
    online: null,
    square: false,
  },
)

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: props.src ? '#dfe4ea' : avatarColor(props.name),
  fontSize: `${Math.round(props.size * 0.42)}px`,
  borderRadius: props.square ? `${Math.round(props.size * 0.22)}px` : '50%',
}))

const dotStyle = computed(() => ({
  width: `${Math.max(8, Math.round(props.size * 0.26))}px`,
  height: `${Math.max(8, Math.round(props.size * 0.26))}px`,
}))
</script>

<template>
  <span class="avatar" :style="style">
    <img v-if="src" :src="src" :alt="name" class="avatar-img" />
    <span v-else class="avatar-text">{{ avatarLetter(name) }}</span>
    <span
      v-if="online !== null && online !== undefined"
      class="avatar-dot"
      :class="{ 'avatar-dot--online': online }"
      :style="dotStyle"
    ></span>
  </span>
</template>

<style scoped>
.avatar {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: #fff;
  font-weight: 600;
  user-select: none;
  overflow: visible;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
  display: block;
}

.avatar-text {
  line-height: 1;
}

.avatar-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #c3c9d2;
}

.avatar-dot--online {
  background: var(--color-success);
}
</style>
