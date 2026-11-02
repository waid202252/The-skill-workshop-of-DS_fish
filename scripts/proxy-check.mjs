/**
 * Vite 代理链路验证：确认前端 5173 端口的 /api、/ws、/uploads 都能正确转发到后端 8080。
 * 这一步验证的是「浏览器里真实会走的那条路径」，而不是直连后端。
 *
 * 运行前提：后端 8080 已启动 + 前端 `pnpm dev` 已启动。
 * 运行方式：node scripts/proxy-check.mjs
 */

const ORIGIN = process.env.IM_ORIGIN || 'http://localhost:5173';
const WS_ORIGIN = ORIGIN.replace(/^http/, 'ws');

let passed = 0;
function ok(name, cond, detail) {
  if (!cond) throw new Error(`断言失败: ${name}${detail !== undefined ? ' -> ' + JSON.stringify(detail) : ''}`);
  passed++;
  console.log(`  ✓ ${name}`);
}

async function api(method, path, { token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(ORIGIN + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, ...json };
}

async function main() {
  const tag = String(Date.now()).slice(-7);

  console.log('\n[1] 页面与静态资源');
  const page = await fetch(ORIGIN + '/');
  const html = await page.text();
  ok('开发服务器返回页面', page.status === 200 && html.includes('<div id="app"'), page.status);
  // 注意：Vite 在 HMR 失效后会给入口加 ?t= 时间戳，所以不能匹配结尾引号
  ok('页面挂载了入口脚本', /src="\/src\/main\.ts/.test(html), html.slice(0, 300));

  console.log('\n[2] /api 代理');
  const reg = await api('POST', '/api/auth/register', {
    body: { username: `web${tag}`, password: 'pass1234', nickname: '网页用户' },
  });
  ok('/api 代理转发注册请求成功', reg.code === 0 && !!reg.data?.token, reg);

  const token = reg.data.token;
  const me = await api('GET', '/api/users/me', { token });
  ok('/api 代理转发鉴权请求成功', me.code === 0 && me.data.nickname === '网页用户', me);

  const unauth = await api('GET', '/api/users/me');
  ok('未登录经代理仍返回 401 信封', unauth.status === 401 && unauth.code === 40100, unauth);

  console.log('\n[3] /ws 代理');
  const wsUrl = `${WS_ORIGIN}/ws?token=${encodeURIComponent(token)}`;
  const ws = new WebSocket(wsUrl);
  const frames = [];
  const waiters = [];
  ws.onmessage = (ev) => {
    const f = JSON.parse(ev.data);
    frames.push(f);
    for (let i = waiters.length - 1; i >= 0; i--) {
      if (waiters[i].type === f.type) {
        const [w] = waiters.splice(i, 1);
        w.resolve(f);
      }
    }
  };
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('WebSocket 经代理建连超时')), 8000);
    ws.onopen = () => {
      clearTimeout(t);
      resolve();
    };
    ws.onerror = () => {
      clearTimeout(t);
      reject(new Error('WebSocket 经代理建连失败'));
    };
  });
  ok('WebSocket 经 Vite 代理建连成功', true);

  const ready = await new Promise((resolve, reject) => {
    const hit = frames.find((f) => f.type === 'ready');
    if (hit) return resolve(hit);
    const t = setTimeout(() => reject(new Error('未收到 ready 帧')), 5000);
    waiters.push({ type: 'ready', resolve: (f) => { clearTimeout(t); resolve(f); } });
  });
  ok('经代理收到 ready 帧', !!ready.data.userId);

  const partner = await api('POST', '/api/auth/register', {
    body: { username: `peer${tag}`, password: 'pass1234', nickname: '对端用户' },
  });
  ok('再注册一个对端用户用于双人会话', partner.code === 0 && !!partner.data?.token, partner);

  const conv = await api('POST', '/api/conversations/single', {
    token,
    body: { userId: partner.data.user.id },
  });
  ok('经代理创建单聊会话成功', conv.code === 0 && conv.data.type === 'SINGLE', conv);

  const clientMsgId = 'proxy-' + Date.now();
  ws.send(JSON.stringify({
    type: 'send',
    data: { conversationId: conv.data.id, clientMsgId, type: 'TEXT', content: '经代理发送' },
  }));
  const ack = await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('未收到 ack 帧')), 5000);
    waiters.push({
      type: 'ack',
      resolve: (f) => {
        clearTimeout(t);
        resolve(f);
      },
    });
  });
  ok('经代理的 WebSocket 能完成消息往返（ack）', ack.data.message.content === '经代理发送', ack.data);

  console.log('\n[4] /uploads 代理');
  const form = new FormData();
  form.append('file', new Blob(['proxy upload body'], { type: 'text/plain' }), 'p.txt');
  const upRes = await fetch(ORIGIN + '/api/files/upload', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  const upJson = await upRes.json();
  ok('经代理上传文件成功', upJson.code === 0, upJson);

  const fileRes = await fetch(ORIGIN + upJson.data.url);
  const text = await fileRes.text();
  ok('经代理能读取上传后的文件', fileRes.status === 200 && text === 'proxy upload body', {
    status: fileRes.status,
    text,
  });

  ws.close();
  console.log(`\n代理链路全部通过：${passed} 项 ✓`);
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('\n✗ ' + e.message);
    process.exit(1);
  });
