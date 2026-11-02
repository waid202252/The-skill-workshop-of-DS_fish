<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ source: string }>()

/**
 * 极简 Markdown 渲染：
 * - 只支持社区内容实际用到的标题、段落、列表、引用、代码块、粗体、行内代码、链接
 * - 先转义 HTML，再生成受控标签，避免 v-html 注入
 */
const html = computed(() => render(props.source ?? ''))

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function inline(value: string): string {
  const codes: string[] = []
  let text = escapeHtml(value).replace(/`([^`]+)`/g, (_, code: string) => {
    codes.push(code)
    return `\u0000${codes.length - 1}\u0000`
  })
  text = text
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(
      /\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>',
    )
  return text.replace(/\u0000(\d+)\u0000/g, (_, index: string) => `<code>${codes[Number(index)]}</code>`)
}

function render(source: string): string {
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  let html = ''
  let paragraph: string[] = []
  let listType: 'ul' | 'ol' | null = null
  let listItems: string[] = []
  let inCode = false
  let codeLines: string[] = []

  const flushParagraph = () => {
    if (!paragraph.length) return
    html += `<p>${inline(paragraph.join(' '))}</p>`
    paragraph = []
  }

  const flushList = () => {
    if (!listType || !listItems.length) {
      listType = null
      listItems = []
      return
    }
    html += `<${listType}>${listItems.map((item) => `<li>${inline(item)}</li>`).join('')}</${listType}>`
    listType = null
    listItems = []
  }

  for (const line of lines) {
    if (line.startsWith('```')) {
      if (inCode) {
        html += `<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`
        codeLines = []
        inCode = false
      } else {
        flushParagraph()
        flushList()
        inCode = true
      }
      continue
    }
    if (inCode) {
      codeLines.push(line)
      continue
    }

    if (!line.trim()) {
      flushParagraph()
      flushList()
      continue
    }

    const heading = line.match(/^(#{1,3})\s+(.*)$/)
    if (heading) {
      flushParagraph()
      flushList()
      const level = heading[1].length
      html += `<h${level}>${inline(heading[2])}</h${level}>`
      continue
    }

    const quote = line.match(/^>\s?(.*)$/)
    if (quote) {
      flushParagraph()
      flushList()
      html += `<blockquote>${inline(quote[1])}</blockquote>`
      continue
    }

    const unordered = line.match(/^[-*]\s+(.*)$/)
    if (unordered) {
      flushParagraph()
      if (listType !== 'ul') {
        flushList()
        listType = 'ul'
      }
      listItems.push(unordered[1])
      continue
    }

    const ordered = line.match(/^\d+\.\s+(.*)$/)
    if (ordered) {
      flushParagraph()
      if (listType !== 'ol') {
        flushList()
        listType = 'ol'
      }
      listItems.push(ordered[1])
      continue
    }

    flushList()
    paragraph.push(line)
  }

  if (inCode && codeLines.length) {
    html += `<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`
  }
  flushParagraph()
  flushList()
  return html
}
</script>

<template>
  <div class="markdown" v-html="html"></div>
</template>

<style scoped>
.markdown {
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
}

.markdown :deep(h1),
.markdown :deep(h2),
.markdown :deep(h3) {
  margin: 1.4em 0 0.55em;
  color: var(--color-text);
  font-weight: var(--weight-bold);
  line-height: 1.35;
}

.markdown :deep(h1) {
  font-size: 22px;
}

.markdown :deep(h2) {
  font-size: 18px;
}

.markdown :deep(h3) {
  font-size: 15.5px;
}

.markdown :deep(p) {
  margin: 0.65em 0;
}

.markdown :deep(ul),
.markdown :deep(ol) {
  margin: 0.65em 0;
  padding-left: 22px;
}

.markdown :deep(li) {
  margin: 0.35em 0;
}

.markdown :deep(strong) {
  color: var(--color-text);
  font-weight: var(--weight-semibold);
}

.markdown :deep(a) {
  color: var(--color-primary-strong);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.markdown :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: #eef2f6;
  color: #334155;
  font-family: var(--font-mono);
  font-size: 0.9em;
}

.markdown :deep(pre) {
  margin: 1em 0;
  padding: 14px 16px;
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #f8fafc;
}

.markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: #334155;
  font-size: 12.5px;
  line-height: 1.7;
}

.markdown :deep(blockquote) {
  margin: 1em 0;
  padding: 8px 14px;
  border-left: 3px solid var(--color-primary);
  border-radius: 0 8px 8px 0;
  background: var(--color-primary-softer);
  color: var(--color-text-secondary);
}
</style>
