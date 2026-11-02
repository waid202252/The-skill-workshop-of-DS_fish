/**
 * 带登录态给页面截图（用 Edge 的 DevTools 协议）。
 *
 * 为什么需要它：/discover、/skills 都要登录，边 --screenshot 只能拍一个 URL，
 * 拍不到登录后的页面。这里先用接口注册拿 token，再通过 CDP 把它写进 localStorage，
 * 然后导航到目标页并截图。
 *
 * 用法: node scripts/shot.mjs <路径> <输出文件>
 *   例: node scripts/shot.mjs /discover docs/screenshots/discover.png
 */

import { spawn } from 'node:child_process'
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'

const BASE = process.env.IM_BASE || 'http://127.0.0.1:8080'
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const PORT = 9333

const targetPath = process.argv[2] || '/discover'
const outFile = process.argv[3] || '.tmp/shots/page.png'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function register() {
  const username = 'shot' + String(Date.now()).slice(-7)
  const res = await fetch(BASE + '/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Origin: BASE },
    body: JSON.stringify({ username, password: 'pass1234', nickname: '截图账号' }),
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error('注册失败: ' + json.message)
  return json.data.token
}

async function cdpTargets() {
  // 等浏览器把调试端口拉起来
  for (let i = 0; i < 40; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/list`)
      const list = await r.json()
      const page = list.find((t) => t.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {
      /* 还没起来 */
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
  const token = await register()
  console.log('token 就绪')

  const proc = spawn(EDGE, [
    '--headless=new',
    '--disable-gpu',
    '--hide-scrollbars',
    `--remote-debugging-port=${PORT}`,
    '--user-data-dir=D:\\develop\\2025_6\\.tmp\\edge-cdp',
    '--window-size=1440,1000',
    'about:blank',
  ])

  try {
    const wsUrl = await cdpTargets()
    const cdp = await connect(wsUrl)

    // 先访问一次同源页面，才能写它的 localStorage
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(1800)

    await cdp.send('Runtime.evaluate', {
      expression: `localStorage.setItem('weliao.token', ${JSON.stringify(token)})`,
    })

    await cdp.send('Page.navigate', { url: BASE + targetPath })
    await sleep(3200)

    // 用 jpeg：详情页的 README 很长，整页 png 体积过大，CDP 回传时会解压失败
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'jpeg', quality: 78 })
    mkdirSync(dirname(outFile), { recursive: true })
    writeFileSync(outFile, Buffer.from(data, 'base64'))
    console.log('已保存: ' + outFile)

    cdp.close()
  } finally {
    proc.kill()
  }
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error(e)
    process.exit(1)
  })
