/**
 * AI 伙伴链路验证。
 * 检查四件事：AI 账号被自动创建、注册即开通会话、欢迎语存在、用户发消息后 AI 会回复。
 *
 * 运行前提：后端已启动。
 * 运行方式：node scripts/ai-check.mjs
 */

const BASE = process.env.IM_BASE || 'http://127.0.0.1:8080';
let passed = 0;

function ok(name, cond, detail) {
  if (!cond) throw new Error(`断言失败: ${name}${detail !== undefined ? ' -> ' + JSON.stringify(detail) : ''}`);
  passed++;
  console.log(`  ✓ ${name}`);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(method, path, { token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, ...json };
}

async function must(method, path, opts) {
  const r = await api(method, path, opts);
  if (r.code !== 0) throw new Error(`${method} ${path} 失败: code=${r.code} message=${r.message}`);
  return r.data;
}

async function main() {
  const tag = String(Date.now()).slice(-7);

  console.log('\n[1] AI 链路状态');
  const u = `aiuser${tag}`;
  const reg = await must('POST', '/api/auth/register', {
    body: { username: u, password: 'pass1234', nickname: 'AI 测试员' },
  });
  const token = reg.token;

  const status = await must('GET', '/api/ai/status', { token });
  ok('AI 账号已初始化且有 id', typeof status.botId === 'number', status);
  ok('返回当前 provider 名称', typeof status.provider === 'string' && status.provider.length > 0, status);
  console.log(`     provider = ${status.provider}${status.mock ? '（本地模拟模式）' : '（已接入真实模型）'}`);

  console.log('\n[2] 注册即自动开通 AI 会话');
  const convs = await must('GET', '/api/conversations', { token });
  const aiConv = convs.find((c) => c.peer && c.peer.type === 'BOT');
  ok('会话列表里出现 AI 会话', !!aiConv, convs.map((c) => c.peer?.nickname));
  ok('AI 会话是单聊且带对方信息', aiConv.type === 'SINGLE' && aiConv.peer.nickname === '大肥鱼', aiConv.peer);
  ok('会话里有未读的欢迎语', aiConv.unread >= 1 && !!aiConv.lastMessage, {
    unread: aiConv.unread,
    last: aiConv.lastMessage?.content?.slice(0, 30),
  });

  const welcome = await must('GET', `/api/conversations/${aiConv.id}/messages?limit=10`, { token });
  ok('欢迎语已落库为一条真实消息', welcome.messages.length >= 1 && welcome.messages[0].senderId === status.botId, {
    count: welcome.messages.length,
  });

  console.log('\n[3] 用户发消息，AI 异步回复');
  const text = '你好，你能介绍一下自己吗';
  const sent = await must('POST', `/api/conversations/${aiConv.id}/messages`, {
    token,
    body: { type: 'TEXT', content: text },
  });
  ok('用户消息发送成功', sent.content === text);

  // AI 回复是异步的，轮询等待
  let reply = null;
  for (let i = 0; i < 30; i++) {
    await sleep(300);
    const page = await must('GET', `/api/conversations/${aiConv.id}/messages?limit=10`, { token });
    const found = page.messages.find((m) => m.senderId === status.botId && m.seq > sent.seq);
    if (found) {
      reply = found;
      break;
    }
  }
  ok('AI 在 9 秒内产生了回复', !!reply, reply);
  ok('回复来自 AI 账号且内容非空', reply.senderId === status.botId && reply.content.trim().length > 0, {
    from: reply.senderId,
    content: reply.content.slice(0, 60),
  });
  console.log(`     回复内容: ${reply.content.replace(/\n/g, ' / ').slice(0, 80)}`);

  console.log('\n[4] AI 不回应自己、幂等性');
  const before = (await must('GET', `/api/conversations/${aiConv.id}/messages?limit=50`, { token })).messages.length;
  await sleep(1200);
  const after = (await must('GET', `/api/conversations/${aiConv.id}/messages?limit=50`, { token })).messages.length;
  ok('AI 回复不会触发自身再回复（无无限循环）', after === before, { before, after });

  console.log('\n[5] 非 AI 会话不受影响');
  const p2 = await must('POST', '/api/auth/register', {
    body: { username: `peer${tag}`, password: 'pass1234', nickname: '真人好友' },
  });
  const humanConv = await must('POST', '/api/conversations/single', {
    token,
    body: { userId: p2.user.id },
  });
  ok('与真人的单聊正常创建', humanConv.type === 'SINGLE' && humanConv.peer.type === 'HUMAN', humanConv.peer);

  const humanMsg = await must('POST', `/api/conversations/${humanConv.id}/messages`, {
    token,
    body: { type: 'TEXT', content: '在吗' },
  });
  await sleep(1500);
  const humanHistory = await must('GET', `/api/conversations/${humanConv.id}/messages?limit=10`, { token });
  ok('真人会话不会冒出 AI 回复', humanHistory.messages.length === 1 && humanHistory.messages[0].id === humanMsg.id,
    humanHistory.messages.map((m) => m.senderId));

  console.log(`\nAI 链路全部通过：${passed} 项断言 ✓`);
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('\n✗ ' + e.message);
    process.exit(1);
  });
