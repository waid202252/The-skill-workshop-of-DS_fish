/**
 * 前端新增入口对应的接口验证。
 * 覆盖：改资料、群成员邀请/移除/退群、权限校验、AI 状态。
 *
 * 运行前提：后端已启动（容器或本地均可）。
 * 运行方式：node scripts/ui-api-check.mjs
 */

const BASE = process.env.IM_BASE || 'http://127.0.0.1:8080';
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

async function register(name, nickname) {
  const r = await must('POST', '/api/auth/register', {
    body: { username: name, password: 'pass1234', nickname },
  });
  return { token: r.token, user: r.user };
}

async function main() {
  const tag = String(Date.now()).slice(-7);

  console.log('\n[1] 编辑资料（AppNavRail → ProfileEditModal）');
  const owner = await register(`own${tag}`, '群主');
  const updated = await must('PUT', '/api/users/me', {
    token: owner.token,
    body: { nickname: '改过的昵称', signature: '新签名', avatar: '' },
  });
  ok('昵称修改生效', updated.nickname === '改过的昵称', updated);
  ok('签名修改生效', updated.signature === '新签名');
  ok('空字符串头像被落成 null', updated.avatar === null);

  const reread = await must('GET', '/api/users/me', { token: owner.token });
  ok('重新读取仍是修改后的资料', reread.nickname === '改过的昵称');

  console.log('\n[2] 群成员管理（ChatPanel → GroupMembersModal）');
  const a1 = await register(`m1${tag}`, '成员一');
  const a2 = await register(`m2${tag}`, '成员二');
  const outsider = await register(`out${tag}`, '路人');

  const group = await must('POST', '/api/conversations/group', {
    token: owner.token,
    body: { name: '测试群', memberIds: [a1.user.id] },
  });
  ok('建群初始 2 人', group.memberCount === 2, group.memberCount);

  const added = await must('POST', `/api/conversations/${group.id}/members`, {
    token: owner.token,
    body: { userIds: [a2.user.id] },
  });
  ok('群主邀请后 3 人', added.memberCount === 3, added.memberCount);
  ok('新成员在成员列表中', added.members.some((m) => m.userId === a2.user.id));

  const a2Convs = await must('GET', '/api/conversations', { token: a2.token });
  ok('被邀请方能在会话列表看到群', a2Convs.some((c) => c.id === group.id));

  const removed = await must('DELETE', `/api/conversations/${group.id}/members/${a2.user.id}`, {
    token: owner.token,
  });
  void removed;
  const afterRemove = await must('GET', `/api/conversations/${group.id}`, { token: owner.token });
  ok('移除后回到 2 人', afterRemove.memberCount === 2, afterRemove.memberCount);
  ok('被移除者不再在成员里', !afterRemove.members.some((m) => m.userId === a2.user.id));

  const a2After = await must('GET', '/api/conversations', { token: a2.token });
  ok('被移除者的会话列表里没有该群', !a2After.some((c) => c.id === group.id));

  console.log('\n[3] 权限校验');
  const badInvite = await api('POST', `/api/conversations/${group.id}/members`, {
    token: a1.token,
    body: { userIds: [outsider.user.id] },
  });
  ok('普通成员不能邀请他人（403）', badInvite.status === 403 && badInvite.code === 40300, badInvite);

  const badRemove = await api('DELETE', `/api/conversations/${group.id}/members/${owner.user.id}`, {
    token: a1.token,
  });
  ok('普通成员不能移除他人（403）', badRemove.status === 403, badRemove);

  console.log('\n[4] 退出群聊');
  const left = await api('DELETE', `/api/conversations/${group.id}/members/${a1.user.id}`, {
    token: a1.token,
  });
  ok('成员可以自己退群', left.code === 0, left);
  const a1After = await must('GET', '/api/conversations', { token: a1.token });
  ok('退群后会话列表里没有该群', !a1After.some((c) => c.id === group.id));

  console.log('\n[5] AI 状态（模拟模式标记）');
  const ai = await must('GET', '/api/ai/status', { token: owner.token });
  ok('返回 provider 与 mock 标记', typeof ai.provider === 'string' && typeof ai.mock === 'boolean', ai);

  const meAsBot = await must('GET', '/api/users/search?keyword=ai_fish', { token: owner.token });
  ok('AI 账号的 type 为 BOT', meAsBot.some((u) => u.type === 'BOT'), meAsBot.map((u) => u.type));

  console.log('\n[6] 消息撤回');
  const single = await must('POST', '/api/conversations/single', {
    token: owner.token,
    body: { userId: a1.user.id },
  });
  const sentMsg = await must('POST', `/api/conversations/${single.id}/messages`, {
    token: owner.token,
    body: { type: 'TEXT', content: '这条要撤回' },
  });
  const recalled = await must('POST', `/api/conversations/${single.id}/messages/${sentMsg.id}/recall`, {
    token: owner.token,
  });
  ok('撤回成功且标记 recalled', recalled.recalled === true, recalled);
  ok('撤回后内容被清空', recalled.content === '', recalled.content);

  const peerView = await must('GET', `/api/conversations/${single.id}/messages?limit=10`, { token: a1.token });
  const peerMsg = peerView.messages.find((m) => m.id === sentMsg.id);
  ok('对方看到的也是已撤回且内容为空', !!peerMsg && peerMsg.recalled === true && peerMsg.content === '', peerMsg);

  const again = await must('POST', `/api/conversations/${single.id}/messages/${sentMsg.id}/recall`, {
    token: owner.token,
  });
  ok('重复撤回幂等，不报错', again.recalled === true);

  const othersRecall = await api('POST', `/api/conversations/${single.id}/messages/${sentMsg.id}/recall`, {
    token: a1.token,
  });
  ok('不能撤回别人的消息（403）', othersRecall.status === 403, othersRecall);

  console.log(`\n前端新增入口接口全部通过：${passed} 项断言 ✓`);
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('\n✗ ' + e.message);
    process.exit(1);
  });
