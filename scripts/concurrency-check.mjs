/**
 * 并发与冲突处理验证（对应存储审查里第 6 节的坑）。
 *
 *   [1] 同一对用户并发打开单聊 → 只能产生 1 个会话
 *   [2] 同一用户名并发注册       → 1 个成功，其余 409（不是 500）
 *
 * 运行前提：后端已启动。
 * 运行方式：node scripts/concurrency-check.mjs
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

async function main() {
  const tag = String(Date.now()).slice(-7);

  const a = await must('POST', '/api/auth/register', {
    body: { username: `ca${tag}`, password: 'pass1234', nickname: '并发A' },
  });
  const b = await must('POST', '/api/auth/register', {
    body: { username: `cb${tag}`, password: 'pass1234', nickname: '并发B' },
  });
  console.log(`\n准备完成: A=${a.user.id} B=${b.user.id}`);

  // ---------------------------------------------------------------- 1
  console.log('\n[1] 同一对用户并发打开单聊（10 个并发请求）');
  const N = 10;
  const results = await Promise.all(
    Array.from({ length: N }, () =>
      api('POST', '/api/conversations/single', { token: a.token, body: { userId: b.user.id } }),
    ),
  );
  const failed = results.filter((r) => r.code !== 0);
  ok('全部请求成功（没有 500）', failed.length === 0, failed.slice(0, 2));

  const ids = [...new Set(results.map((r) => r.data?.id))];
  ok(`10 个并发请求只产生 1 个会话（实际 ${ids.length} 个）`, ids.length === 1, ids);

  // 再从 B 的角度核对，避免"看起来同一个 id 但库里真有两条"
  const bConvs = await must('GET', '/api/conversations', { token: b.token });
  const withA = bConvs.filter((c) => c.type === 'SINGLE' && c.peer && c.peer.id === a.user.id);
  ok('B 的会话列表里与 A 只有一个单聊', withA.length === 1, withA.map((c) => c.id));

  // ---------------------------------------------------------------- 2
  console.log('\n[2] 同一用户名并发注册（5 个并发请求）');
  const dupName = `dup${tag}`;
  const regs = await Promise.all(
    Array.from({ length: 5 }, (_, i) =>
      api('POST', '/api/auth/register', {
        body: { username: dupName, password: 'pass1234', nickname: `重复${i}` },
      }),
    ),
  );
  const success = regs.filter((r) => r.code === 0);
  const conflicts = regs.filter((r) => r.status === 409);
  const serverErrors = regs.filter((r) => r.status >= 500);

  ok('只有 1 个注册成功', success.length === 1, { success: success.length });
  ok('其余 4 个返回 409 而不是 500', conflicts.length === 4, {
    conflicts: conflicts.length,
    serverErrors: serverErrors.length,
    statuses: regs.map((r) => r.status),
  });
  ok('没有任何 5xx', serverErrors.length === 0, regs.map((r) => r.status));

  console.log(`\n并发验证全部通过：${passed} 项断言 ✓`);
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('\n✗ ' + e.message);
    process.exit(1);
  });
