/**
 * 用真实浏览器走一遍「填表 → 点登录」，验证用户实际操作的路径。
 *
 * 和 e2e 脚本的区别：e2e 直接打 HTTP 接口，跳过了前端。
 * 这里是真的开一个 Edge，往输入框里填字、点按钮，能暴露
 * 前端表单、路由守卫、localStorage、CORS 这些只在浏览器里才有的问题。
 *
 * 用法: node scripts/login-test.mjs [用户名] [密码]
 */

import { spawn } from 'node:child_process'
import { writeFileSync, mkdirSync } from 'node:fs'

const BASE = process.env.IM_BASE || 'http://127.0.0.1:8080'
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const PORT = 9334
const USERNAME = process.argv[2] || 'waid'
const PASSWORD = process.argv[3] || 'waid1234'
const SHOT = process.env.SHOT_OUT || '.tmp/shots/login-test.png'
const AFTER = process.argv[4] || ''

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function cdpTargets() {
  for (let i = 0; i < 40; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/list`)
      const list = await r.json()
      const page = list.find((t) => t.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {
      /* 端口还没起来 */
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
    '--hide-scrollbars',
    `--remote-debugging-port=${PORT}`,
    '--user-data-dir=D:\\develop\\2025_6\\.tmp\\edge-login-test',
    '--window-size=1440,1000',
    'about:blank',
  ])

  try {
    const wsUrl = await cdpTargets()
    const cdp = await connect(wsUrl)

    // 先清掉本地登录态，确保停在登录页
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2000)
    await cdp.evaluate('localStorage.clear()')
    await cdp.send('Page.navigate', { url: BASE + '/login?redirect=/discover' })
    await sleep(2000)

    const before = await cdp.evaluate('location.pathname')
    console.log('开始前页面:', before)

    // 填表：必须派发 input 事件，否则 Vue 的 v-model 收不到
    const filled = await cdp.evaluate(`
      (() => {
        const inputs = document.querySelectorAll('.login-form input');
        if (inputs.length < 2) return '找不到输入框，实际数量=' + inputs.length;
        const setVal = (el, v) => {
          el.value = v;
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        };
        setVal(inputs[0], ${JSON.stringify(USERNAME)});
        setVal(inputs[1], ${JSON.stringify(PASSWORD)});
        return '已填入 ' + inputs.length + ' 个输入框';
      })()
    `)
    console.log('填表:', filled)

    const clicked = await cdp.evaluate(`
      (() => {
        const btn = document.querySelector('.login-form .submit-button');
        if (!btn) return '找不到登录按钮';
        if (btn.disabled) return '登录按钮是禁用状态';
        btn.click();
        return '已点击登录';
      })()
    `)
    console.log('提交:', clicked)

    // 等登录结果：成功会跳到 /discover，失败会出现 .error-banner
    let outcome = null
    for (let i = 0; i < 24; i++) {
      await sleep(250)
      outcome = await cdp.evaluate(`
        (() => {
          const err = document.querySelector('.error-banner');
          const token = localStorage.getItem('weliao.token');
          return {
            path: location.pathname,
            error: err ? err.innerText.trim() : null,
            hasToken: !!token,
          };
        })()
      `)
      if (outcome.error || outcome.path !== '/login') break
    }

    // 登录成功后可以继续跳到指定页面再截图
    if (AFTER) {
      await cdp.send('Page.navigate', { url: BASE + AFTER })
      await sleep(3500)
    }

    console.log('\n结果:')
    console.log('  当前路径 :', outcome.path)
    console.log('  有 token :', outcome.hasToken)
    console.log('  错误提示 :', outcome.error ?? '（无）')

    const { data } = await cdp.send('Page.captureScreenshot', { format: 'jpeg', quality: 80 })
    mkdirSync('.tmp/shots', { recursive: true })
    writeFileSync(SHOT, Buffer.from(data, 'base64'))
    console.log('  截图     :', SHOT)

    const ok = outcome.path !== '/login' && !outcome.error && outcome.hasToken
    console.log('\n' + (ok ? '✓ 浏览器登录成功' : '✗ 浏览器登录失败'))
    cdp.close()
    if (!ok) process.exitCode = 1
  } finally {
    proc.kill()
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
