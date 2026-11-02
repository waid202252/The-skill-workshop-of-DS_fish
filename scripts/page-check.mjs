/**
 * 登录后访问指定页面，抽取关键 DOM 信息作为验证证据（不截图）。
 *
 * 为什么不用截图：详情页的 README 很长，CDP 回传大图时会解压失败。
 * 读 DOM 反而更精确——能直接看到"页面上到底有哪些信息"。
 *
 * 用法: node scripts/page-check.mjs <用户名> <密码> <路径>
 */

import { spawn } from 'node:child_process'

const BASE = process.env.IM_BASE || 'http://127.0.0.1:8080'
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const PORT = 9335
const [, , USERNAME = 'waid', PASSWORD = 'waid202252', TARGET = '/skills/2'] = process.argv

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function targets() {
  for (let i = 0; i < 40; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const page = list.find((t) => t.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {
      /* 端口未就绪 */
    }
    await sleep(250)
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

async function main() {
  const proc = spawn(EDGE, [
    '--headless=new',
    '--disable-gpu',
    `--remote-debugging-port=${PORT}`,
    '--user-data-dir=D:\\develop\\2025_6\\.tmp\\edge-page-check',
    '--window-size=1440,1000',
    'about:blank',
  ])

  try {
    const cdp = await connect(await targets())
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(1800)

    // 用接口登录拿 token，直接写 localStorage（比填表快，且表单链路已由 login-test 覆盖）
    const reg = await fetch(BASE + '/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Origin: BASE },
      body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
    }).then((r) => r.json())
    if (reg.code !== 0) throw new Error('登录失败: ' + reg.message)
    await cdp.evaluate(`localStorage.setItem('weliao.token', ${JSON.stringify(reg.data.token)})`)

    await cdp.send('Page.navigate', { url: BASE + TARGET })
    await sleep(4000)

    const info = await cdp.evaluate(`
      (() => {
        const txt = (sel) => document.querySelector(sel)?.innerText?.trim() ?? null;
        const all = (sel) => [...document.querySelectorAll(sel)].map(e => e.innerText.trim()).filter(Boolean);
        return {
          url: location.pathname,
          title: document.title,
          h1: txt('h1'),
          desc: txt('.hero-desc'),
          metrics: all('.metric'),
          topics: all('.topic'),
          sideLabels: all('.side-card dt'),
          sideValues: all('.side-card dd'),
          docHead: txt('.doc-head h2'),
          readmeChars: (document.querySelector('.doc-body')?.innerText || '').length,
          readmeHead: (document.querySelector('.doc-body')?.innerText || '').slice(0, 120),
          actions: all('.btn-line, .hero-actions button'),
          loadMore: txt('.btn-more'),
          rowCount: document.querySelectorAll('.row').length,
          rows: [...document.querySelectorAll('.row')].slice(0, 2).map(r => ({
            title: r.querySelector('.row-title')?.innerText.replace(/\\n/g, ' '),
            desc: (r.querySelector('.row-desc')?.innerText || '').slice(0, 56),
            tags: [...r.querySelectorAll('.row-tags span')].map(e => e.innerText),
            stats: [...r.querySelectorAll('.row-stats .stat')].map(e => e.innerText.replace(/\\n/g, ' ')),
            actions: [...r.querySelectorAll('.row-actions button')].map(e => e.innerText),
            avatar: (() => { const a = r.querySelector('.row-avatar'); return a ? Math.round(a.getBoundingClientRect().width) + 'x' + Math.round(a.getBoundingClientRect().height) : null })(),
          })),
          discoverCard: (() => {
            const c = document.querySelector('.skill-card');
            if (!c) return null;
            return {
              by: c.querySelector('.card-by')?.innerText,
              tags: [...c.querySelectorAll('.card-tags .tag')].map(e => e.innerText),
              foot: (c.querySelector('.card-foot')?.innerText || '').replace(/\\n/g, ' | ').slice(0, 80),
            };
          })(),
          pwdEntry: all('.topbar-right .btn-ghost'),
        };
      })()
    `)

    console.log(JSON.stringify(info, null, 2))

    // 显式限定截图区域：整页截图体积过大时 CDP 回传会解压失败
    if (process.env.SHOT_OUT) {
      await cdp.send('Emulation.setDeviceMetricsOverride', {
        width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
      })
      const shot = await cdp.send('Page.captureScreenshot', {
        format: 'jpeg',
        quality: 75,
        clip: { x: 0, y: 0, width: 1440, height: 900, scale: 1 },
      })
      const { writeFileSync } = await import('node:fs')
      writeFileSync(process.env.SHOT_OUT, Buffer.from(shot.data, 'base64'))
      console.log('截图: ' + process.env.SHOT_OUT)
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
