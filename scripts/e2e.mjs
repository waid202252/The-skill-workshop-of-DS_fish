/**
 * 微聊 IM 端到端冒烟测试（REST + WebSocket 全链路）。
 *
 * 运行前提：后端已在 127.0.0.1:8080 启动。
 * 运行方式：node scripts/e2e.mjs
 *
 * 每次运行都会注册新的随机用户，不污染已有数据，可反复执行。
 */

const BASE = process.env.IM_BASE || 'http://127.0.0.1:8080';
const WS_BASE = BASE.replace(/^http/, 'ws');

let passed = 0;
const log = (msg) => console.log(msg);

function ok(name, cond, detail) {
  if (!cond) {
    throw new Error(`断言失败: ${name}${detail !== undefined ? ' -> ' + JSON.stringify(detail) : ''}`);
  }
  passed++;
  log(`  ✓ ${name}`);
}

async function api(method, path, { token, body } = {}) {
  const headers = {
    // 必须模拟浏览器的 Origin 头：真实浏览器给 POST 请求会带上它，
    // CORS 白名单一旦漏配就会返回 403「Invalid CORS request」，
    // 而 curl / Node fetch 默认不带这个头，测不出这类问题（本项目就踩过：
    // 部署到 8080 后所有 POST 全 403，测试却全绿）。
    Origin: BASE,
  };
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
  if (r.code !== 0) {
    throw new Error(`${method} ${path} 失败: code=${r.code} message=${r.message} http=${r.status}`);
  }
  return r.data;
}

// ---------------------------------------------------------------- WebSocket

async function wsConnect(token) {
  const ws = new WebSocket(`${WS_BASE}/ws?token=${encodeURIComponent(token)}`);
  const client = { ws, frames: [], waiters: [] };
  ws.onmessage = (ev) => {
    let frame;
    try {
      frame = JSON.parse(ev.data);
    } catch {
      return;
    }
    client.frames.push(frame);
    client.waiters = client.waiters.filter((w) => {
      if (w.type === frame.type && w.predicate(frame)) {
        w.resolve(frame);
        return false;
      }
      return true;
    });
  };
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket 连接超时')), 5000);
    ws.onopen = () => {
      clearTimeout(timer);
      resolve();
    };
    ws.onerror = () => {
      clearTimeout(timer);
      reject(new Error('WebSocket 连接失败'));
    };
  });
  return client;
}

function waitFrame(client, type, predicate = () => true, timeoutMs = 5000) {
  const hit = client.frames.find((f) => f.type === type && predicate(f));
  if (hit) return Promise.resolve(hit);
  return new Promise((resolve, reject) => {
    const waiter = { type, predicate, resolve: null };
    const timer = setTimeout(() => {
      client.waiters = client.waiters.filter((w) => w !== waiter);
      reject(new Error(`等待 WebSocket 帧超时: ${type}`));
    }, timeoutMs);
    waiter.resolve = (f) => {
      clearTimeout(timer);
      resolve(f);
    };
    client.waiters.push(waiter);
  });
}

function wsSend(client, type, data) {
  client.ws.send(JSON.stringify(data === undefined ? { type } : { type, data }));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------------------------------------------------------------- 主流程

const opened = [];

async function main() {
  const tag = String(Date.now()).slice(-7);
  const A = `alice${tag}`;
  const B = `bob${tag}`;
  const C = `carol${tag}`;

  log('\n[1] 注册与登录');
  const alice = await must('POST', '/api/auth/register', { body: { username: A, password: 'pass1234', nickname: '爱丽丝' } });
  const bob = await must('POST', '/api/auth/register', { body: { username: B, password: 'pass1234', nickname: '鲍勃' } });
  const carol = await must('POST', '/api/auth/register', { body: { username: C, password: 'pass1234', nickname: '卡罗尔' } });
  ok('三个用户注册成功并拿到 token', !!alice.token && !!bob.token && !!carol.token);
  ok('注册返回的用户信息不含密码', !('password' in alice.user));

  const relogin = await must('POST', '/api/auth/login', { body: { username: A, password: 'pass1234' } });
  ok('登录成功', relogin.user.username === A && !!relogin.token);

  const badLogin = await api('POST', '/api/auth/login', { body: { username: A, password: 'wrong' } });
  ok('错误密码返回 400 业务错误码', badLogin.code !== 0 && badLogin.status === 400, badLogin);

  const dupName = await api('POST', '/api/auth/register', { body: { username: A, password: 'pass1234', nickname: 'x' } });
  ok('用户名重复返回 409', dupName.status === 409 && dupName.code === 40900);

  const badRegister = await api('POST', '/api/auth/register', { body: { username: 'ab', password: '1', nickname: '' } });
  ok('参数校验失败返回 400', badRegister.status === 400 && badRegister.code === 40000, badRegister);

  log('\n[2] 鉴权');
  const noToken = await api('GET', '/api/users/me');
  ok('未带 token 返回 401 且是统一信封', noToken.status === 401 && noToken.code === 40100, noToken);
  const badToken = await api('GET', '/api/users/me', { token: 'not-a-real-token' });
  ok('非法 token 返回 401', badToken.status === 401, badToken);

  const me = await must('GET', '/api/users/me', { token: alice.token });
  ok('取当前用户正确', me.username === A && me.nickname === '爱丽丝');

  const updated = await must('PUT', '/api/users/me', { token: alice.token, body: { signature: '今天也要开心' } });
  ok('更新签名生效', updated.signature === '今天也要开心');

  const found = await must('GET', '/api/users/search?keyword=' + encodeURIComponent(tag), { token: alice.token });
  ok('搜索到其他用户且不含自己', found.length === 2 && !found.some((u) => u.username === A), found.map((u) => u.username));

  log('\n[3] 好友申请');
  const bobWs = await wsConnect(bob.token);
  opened.push(bobWs);
  const bobReady = await waitFrame(bobWs, 'ready');
  ok('WebSocket 建连后收到 ready', bobReady.data.userId === bob.user.id, bobReady);

  const req = await must('POST', '/api/friends/requests', {
    token: alice.token,
    body: { toUserId: bob.user.id, message: '我是爱丽丝' },
  });
  ok('好友申请创建成功', req.status === 'PENDING' && req.fromUserId === alice.user.id);

  const pushFr = await waitFrame(bobWs, 'friend_request', (f) => f.data.fromUserId === alice.user.id);
  ok('对方实时收到好友申请推送', pushFr.data.message === '我是爱丽丝');

  const dupReq = await api('POST', '/api/friends/requests', { token: alice.token, body: { toUserId: bob.user.id } });
  ok('重复申请被拒（409）', dupReq.status === 409 && dupReq.code === 40900, dupReq);

  const selfReq = await api('POST', '/api/friends/requests', { token: alice.token, body: { toUserId: alice.user.id } });
  ok('不能加自己为好友', selfReq.status === 400, selfReq);

  const lists = await must('GET', '/api/friends/requests', { token: bob.token });
  ok('待处理申请出现在对方列表', lists.incoming.some((r) => r.id === req.id));

  const accepted = await must('POST', `/api/friends/requests/${req.id}/accept`, { token: bob.token });
  ok('通过好友申请', accepted.userId === alice.user.id);

  const bobFriends = await must('GET', '/api/friends', { token: bob.token });
  ok('好友列表已建立', bobFriends.some((f) => f.userId === alice.user.id));

  log('\n[4] 单聊会话 + WebSocket 实时收发');
  const conv = await must('POST', '/api/conversations/single', { token: alice.token, body: { userId: bob.user.id } });
  ok('单聊会话创建成功', conv.type === 'SINGLE' && conv.peer.id === bob.user.id, conv);
  const conv2 = await must('POST', '/api/conversations/single', { token: bob.token, body: { userId: alice.user.id } });
  ok('重复打开单聊是幂等的', conv2.id === conv.id, { a: conv.id, b: conv2.id });

  const aliceWs = await wsConnect(alice.token);
  opened.push(aliceWs);
  await waitFrame(aliceWs, 'ready');

  const clientMsgId = 'c-' + Date.now();
  wsSend(aliceWs, 'send', { conversationId: conv.id, clientMsgId, type: 'TEXT', content: '你好，鲍勃！' });

  const ack = await waitFrame(aliceWs, 'ack', (f) => f.data.clientMsgId === clientMsgId);
  ok('发送方收到 ack 且带回 clientMsgId', ack.data.message.content === '你好，鲍勃！' && ack.data.message.seq > 0, ack.data);

  const bobMsg = await waitFrame(bobWs, 'message', (f) => f.data.message.content === '你好，鲍勃！');
  ok('接收方实时收到消息', bobMsg.data.message.senderId === alice.user.id && bobMsg.data.message.type === 'TEXT');

  const pingBefore = Date.now();
  wsSend(aliceWs, 'ping');
  const pong = await waitFrame(aliceWs, 'pong');
  ok('心跳 ping 得到 pong', !!pong.data.serverTime);

  log('\n[5] 历史消息、未读数、已读回执');
  const history = await must('GET', `/api/conversations/${conv.id}/messages?limit=20`, { token: bob.token });
  ok('能拉取到刚才那条消息', history.messages.length === 1 && history.messages[0].content === '你好，鲍勃！');
  ok('没有更早消息时 hasMore=false', history.hasMore === false);

  const bobConvs = await must('GET', '/api/conversations', { token: bob.token });
  const bobConv = bobConvs.find((c) => c.id === conv.id);
  ok('会话列表未读数为 1', bobConv && bobConv.unread === 1, bobConv);
  ok('会话列表带最后一条消息摘要', bobConv.lastMessage && bobConv.lastMessage.content === '你好，鲍勃！');
  ok('单聊会话名自动填成对方昵称', bobConv.name === '爱丽丝');

  const aliceConvs = await must('GET', '/api/conversations', { token: alice.token });
  const aliceConv = aliceConvs.find((c) => c.id === conv.id);
  ok('自己发的消息不计入自己的未读', aliceConv.unread === 0);

  wsSend(bobWs, 'read', { conversationId: conv.id, seq: bobMsg.data.message.seq });
  const readPush = await waitFrame(aliceWs, 'read', (f) => f.data.conversationId === conv.id && f.data.userId === bob.user.id);
  ok('已读回执实时推送给对方', readPush.data.seq === bobMsg.data.message.seq);

  const bobConvs2 = await must('GET', '/api/conversations', { token: bob.token });
  ok('标记已读后未读数归零', bobConvs2.find((c) => c.id === conv.id).unread === 0);

  log('\n[6] 游标分页');
  for (let i = 1; i <= 5; i++) {
    await must('POST', `/api/conversations/${conv.id}/messages`, {
      token: alice.token,
      body: { clientMsgId: `p-${i}`, type: 'TEXT', content: `分页消息 ${i}` },
    });
  }
  const page1 = await must('GET', `/api/conversations/${conv.id}/messages?limit=2`, { token: alice.token });
  ok('第一页返回 2 条且 hasMore=true', page1.messages.length === 2 && page1.hasMore === true, page1);
  ok('消息按 seq 升序排列', page1.messages[0].seq < page1.messages[1].seq);
  const page2 = await must('GET', `/api/conversations/${conv.id}/messages?limit=2&beforeSeq=${page1.messages[0].seq}`, { token: alice.token });
  ok('第二页接着更早的消息往后翻', page2.messages.every((m) => m.seq < page1.messages[0].seq), page2.messages.map((m) => m.seq));

  log('\n[6.1] afterSeq 增量补齐（断线重连场景）');
  const catchUp = await must('GET', `/api/conversations/${conv.id}/messages?afterSeq=0&limit=2`, { token: alice.token });
  ok('afterSeq 从最早一条往后取', catchUp.messages.length === 2 && catchUp.messages[0].seq === 1, catchUp.messages.map((m) => m.seq));
  ok('还有更新消息时 hasMore=true', catchUp.hasMore === true);

  let cursor = 0;
  const collected = [];
  for (let i = 0; i < 20; i++) {
    const p = await must('GET', `/api/conversations/${conv.id}/messages?afterSeq=${cursor}&limit=2`, { token: alice.token });
    collected.push(...p.messages.map((m) => m.seq));
    if (p.messages.length === 0 || !p.hasMore) break;
    cursor = p.messages[p.messages.length - 1].seq;
  }
  const strictlyAscending = collected.every((s, i) => i === 0 || s > collected[i - 1]);
  ok('循环 afterSeq 能完整补齐全部消息', collected.length === 6, collected);
  ok('补齐结果无重复且严格递增', new Set(collected).size === 6 && strictlyAscending, collected);

  log('\n[7] 越权校验');
  const forbidden = await api('GET', `/api/conversations/${conv.id}/messages`, { token: carol.token });
  ok('非成员读消息返回 403', forbidden.status === 403 && forbidden.code === 40300, forbidden);

  log('\n[8] 群聊');
  const group = await must('POST', '/api/conversations/group', {
    token: alice.token,
    body: { name: '周末爬山', memberIds: [bob.user.id, carol.user.id] },
  });
  ok('群聊创建成功', group.type === 'GROUP' && group.memberCount === 3, { type: group.type, count: group.memberCount });
  ok('群成员含创建者且角色为 OWNER', group.members.some((m) => m.userId === alice.user.id && m.role === 'OWNER'));

  const groupHistory = await must('GET', `/api/conversations/${group.id}/messages?limit=10`, { token: carol.token });
  ok('建群产生系统消息', groupHistory.messages.some((m) => m.type === 'SYSTEM' && m.content.includes('创建了群聊')),
    groupHistory.messages.map((m) => m.type));

  const carolConvs = await must('GET', '/api/conversations', { token: carol.token });
  ok('群成员能在会话列表看到群', carolConvs.some((c) => c.id === group.id));

  await must('POST', `/api/conversations/${group.id}/messages`, {
    token: carol.token,
    body: { type: 'TEXT', content: '我也想参加' },
  });
  const bobGroupPush = await waitFrame(bobWs, 'message',
    (f) => f.data.message.conversationId === group.id && f.data.message.content === '我也想参加');
  ok('群消息实时推送给其他成员', bobGroupPush.data.message.content === '我也想参加');

  log('\n[9] 在线状态');
  const carolWs = await wsConnect(carol.token);
  opened.push(carolWs);
  await waitFrame(carolWs, 'ready');
  const presence = await waitFrame(bobWs, 'presence', (f) => f.data.userId === carol.user.id);
  ok('有共同会话的联系人收到上线通知', presence.data.online === true);

  const bobFriendsOnline = await must('GET', '/api/friends', { token: bob.token });
  const aliceInList = bobFriendsOnline.find((f) => f.userId === alice.user.id);
  ok('好友列表里在线用户的 online 标记为 true', !!aliceInList && aliceInList.online === true, aliceInList);

  log('\n[10] 文件上传');
  const form = new FormData();
  form.append('file', new Blob(['hello weliao upload'], { type: 'text/plain' }), '测试文件.txt');
  const uploadRes = await fetch(BASE + '/api/files/upload', {
    method: 'POST',
    headers: { Authorization: `Bearer ${alice.token}` },
    body: form,
  });
  const uploadJson = await uploadRes.json();
  ok('上传成功返回统一信封', uploadJson.code === 0, uploadJson);
  ok('返回可直接访问的相对 URL', uploadJson.data.url.startsWith('/uploads/'), uploadJson.data);

  const fileRes = await fetch(BASE + uploadJson.data.url);
  const fileText = await fileRes.text();
  ok('上传的文件可以访问且内容一致', fileRes.status === 200 && fileText === 'hello weliao upload');

  const evilForm = new FormData();
  evilForm.append('file', new Blob(['x'], { type: 'application/x-msdownload' }), 'virus.exe');
  const evilRes = await fetch(BASE + '/api/files/upload', {
    method: 'POST',
    headers: { Authorization: `Bearer ${alice.token}` },
    body: evilForm,
  });
  const evilJson = await evilRes.json();
  ok('不允许的扩展名被拒绝', evilJson.code !== 0, evilJson);

  log('\n[11] WebSocket 错误处理');
  wsSend(aliceWs, 'send', { conversationId: 999999, clientMsgId: 'bad-1', type: 'TEXT', content: 'x' });
  const errFrame = await waitFrame(aliceWs, 'error', (f) => f.data.clientMsgId === 'bad-1');
  ok('向不存在的会话发消息返回 error 帧', typeof errFrame.data.message === 'string', errFrame.data);

  await sleep(100);
  log(`\n全部通过：${passed} 项断言 ✓`);
  return { bobWs, aliceWs, carolWs };
}

main()
  .then(() => {
    opened.forEach((c) => {
      try {
        c.ws.close();
      } catch {
        /* 忽略 */
      }
    });
    process.exit(0);
  })
  .catch((e) => {
    console.error('\n✗ ' + e.message);
    opened.forEach((c) => {
      try {
        c.ws.close();
      } catch {
        /* 忽略 */
      }
    });
    process.exit(1);
  });
