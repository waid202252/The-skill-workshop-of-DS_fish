/**
 * 参考站样式取证工具：抓任意 URL 的真实 DOM 结构与关键计算样式。
 *
 * 为什么要用无头浏览器而不是 web_fetch：
 *   Modrinth 这类站点靠客户端渲染，直接抓 HTML 只能拿到骨架，
 *   拿不到"卡片长什么样、栅格几列、间距多少、hover 有什么变化"。
 *
 * 用法: node scripts/reference-dump.mjs <url> [输出截图路径]
 */

import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const PORT = 9336
const [, , URL_ARG, SHOT] = process.argv
if (!URL_ARG) {
  console.error('用法: node scripts/reference-dump.mjs <url> [shot.jpg]')
  process.exit(1)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function targets() {
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const page = list.find((t) => t.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {
      /* 未就绪 */
    }
    await sleep(300)
  }
  throw new Error('拿不到 CDP 目标')
}

function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url)
    let id = 0
    const pending = new Map()
    ws.onopen = () =>
      resolve({
        send(method, params = {}) {
          const msgId = ++id
          ws.send(JSON.stringify({ id: msgId, method, params }))
          return new Promise((res, rej) => pending.set(msgId, { res, rej }))
        },
        async evaluate(expression) {
          const r = await this.send('Runtime.evaluate', {
            expression,
            awaitPromise: true,
            returnByValue: true,
          })
          if (r.exceptionDetails) throw new Error(r.exceptionDetails.text)
          return r.result?.value
        },
        close: () => ws.close(),
      })
    ws.onerror = (e) => reject(e)
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && pending.has(msg.id)) {
        const { res, rej } = pending.get(msg.id)
        pending.delete(msg.id)
        msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result)
      }
    }
  })
}

const PROBE = `
(() => {
  const cs = (el, props) => {
    const s = getComputedStyle(el);
    const out = {};
    for (const p of props) out[p] = s.getPropertyValue(p);
    return out;
  };
  const STYLE_PROPS = ['display','grid-template-columns','gap','padding','margin','background-color',
    'border','border-radius','box-shadow','font-size','font-weight','color','line-height','width','max-width'];

  // 1) 页面骨架：找含大量重复子元素的容器（通常是卡片栅格）
  const containers = [...document.querySelectorAll('main div, main ul, div[class*="grid"], div[class*="list"]')]
    .map(el => ({ el, n: el.children.length }))
    .filter(x => x.n >= 4)
    .sort((a, b) => b.n - a.n)
    .slice(0, 3);

  const grid = containers.map(({ el, n }) => ({
    tag: el.tagName.toLowerCase(),
    cls: el.className?.toString().slice(0, 120),
    children: n,
    style: cs(el, ['display','grid-template-columns','gap','padding','max-width']),
  }));

  // 2) 单张卡片结构：取第一个"看起来像卡片"的子元素
  const cardHost = containers[0]?.el;
  const card = cardHost?.children[0];
  const cardInfo = card ? {
    tag: card.tagName.toLowerCase(),
    cls: card.className?.toString().slice(0, 160),
    style: cs(card, STYLE_PROPS),
    text: card.innerText.replace(/\\s+/g, ' ').slice(0, 220),
    html: card.outerHTML.replace(/\\s+/g, ' ').slice(0, 1400),
  } : null;

  // 3) 侧栏 / 筛选区：找 aside 或含 checkbox/选择器的块
  const aside = document.querySelector('aside');
  const filters = aside ? {
    cls: aside.className?.toString().slice(0, 120),
    text: aside.innerText.replace(/\\s+/g, ' ').slice(0, 400),
    checkboxCount: aside.querySelectorAll('input[type=checkbox], button, a').length,
  } : null;

  // 4) 徽标/标签：找小而圆的元素
  const badges = [...document.querySelectorAll('span, div')]
    .filter(e => {
      const t = e.innerText?.trim();
      if (!t || t.length > 18) return false;
      const s = getComputedStyle(e);
      const r = parseFloat(s.borderRadius);
      return r >= 4 && (s.backgroundColor !== 'rgba(0, 0, 0, 0)' || s.border !== 'none');
    })
    .slice(0, 12)
    .map(e => ({
      text: e.innerText.trim().slice(0, 24),
      bg: getComputedStyle(e).backgroundColor,
      color: getComputedStyle(e).color,
      radius: getComputedStyle(e).borderRadius,
      font: getComputedStyle(e).fontSize,
    }));

  // 5) 页面配色基线
  const body = getComputedStyle(document.body);


  // 6) 结果行：以 /mod/ 链接为锚点往上找卡片容器
  const link = document.querySelector('a[href*="/mod/"]');
  let row = link;
  for (let i = 0; i < 4 && row && row.parentElement; i++) {
    const r = parseFloat(getComputedStyle(row).borderRadius);
    if (r >= 8) break;
    row = row.parentElement;
  }
  const rows = row ? {
    cls: row.className?.toString().slice(0, 160),
    style: cs(row, STYLE_PROPS),
    text: row.innerText.replace(/\s+/g, ' ').slice(0, 260),
    children: [...row.children].map(c => ({
      tag: c.tagName.toLowerCase(),
      cls: c.className?.toString().slice(0, 90),
      text: c.innerText.replace(/\s+/g, ' ').slice(0, 90),
      style: cs(c, ['width','flex','padding','gap','display','font-size','font-weight','color']),
    })),
    pills: [...row.querySelectorAll('div,span')]
      .filter(e => parseFloat(getComputedStyle(e).borderRadius) >= 999)
      .slice(0, 8)
      .map(e => ({ text: e.innerText.trim().slice(0, 20),
                   bg: getComputedStyle(e).backgroundColor,
                   color: getComputedStyle(e).color,
                   pad: getComputedStyle(e).padding,
                   font: getComputedStyle(e).fontSize })),
  } : null;

  // 7) 栅格骨架：侧栏宽度 + 内容宽度
  const aside2 = document.querySelector('aside')?.getBoundingClientRect();
  const mainRect = document.querySelector('main')?.getBoundingClientRect();

  return {
    row: rows,
    layout: aside2 && mainRect ? {
      asideWidth: Math.round(aside2.width),
      mainWidth: Math.round(mainRect.width),
      gap: Math.round(mainRect.left - aside2.right),
    } : null,
    pageBg: getComputedStyle(document.body).backgroundColor,
    title: document.title,
    url: location.href,
    bodyBg: body.backgroundColor,
    bodyColor: body.color,
    fontFamily: body.fontFamily.slice(0, 120),
    grid,
    card: cardInfo,
    aside: filters,
    badges,
    headings: [...document.querySelectorAll('h1,h2,h3')].slice(0, 6)
      .map(h => ({ tag: h.tagName, text: h.innerText.trim().slice(0, 40),
                   size: getComputedStyle(h).fontSize, weight: getComputedStyle(h).fontWeight })),
  };
})()
`

async function main() {
  const proc = spawn(EDGE, [
    '--headless=new',
    '--disable-gpu',
    `--remote-debugging-port=${PORT}`,
    '--user-data-dir=D:\\develop\\2025_6\\.tmp\\edge-ref',
    '--window-size=1440,1000',
    'about:blank',
  ])

  try {
    const cdp = await connect(await targets())
    await cdp.send('Page.navigate', { url: URL_ARG })
    await sleep(6000) // 等客户端渲染

    const info = await cdp.evaluate(PROBE)
    console.log(JSON.stringify(info, null, 2))

    if (SHOT) {
      await cdp.send('Emulation.setDeviceMetricsOverride', {
        width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false,
      })
      const shot = await cdp.send('Page.captureScreenshot', {
        format: 'jpeg', quality: 78,
        clip: { x: 0, y: 0, width: 1440, height: 1000, scale: 1 },
      })
      writeFileSync(SHOT, Buffer.from(shot.data, 'base64'))
      console.error('截图: ' + SHOT)
    }
    cdp.close()
  } finally {
    proc.kill()
  }
}

main().catch((e) => {
  console.error('✗ ' + e.message)
  process.exit(1)
})
