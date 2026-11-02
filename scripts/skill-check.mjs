/**
 * 技能市场接口验证。
 * 覆盖：GitHub 搜索、缓存命中、推荐列表、安装/卸载、本地降级不崩。
 *
 * 运行前提：后端已启动。
 * 运行方式：node scripts/skill-check.mjs
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

  console.log('\n[1] 登录与鉴权');
  const noAuth = await api('GET', '/api/skills/search?q=ai');
  ok('未登录访问技能搜索返回 401', noAuth.status === 401 && noAuth.code === 40100, noAuth);

  const reg = await must('POST', '/api/auth/register', {
    body: { username: `skill${tag}`, password: 'pass1234', nickname: '技能测试' },
  });
  const token = reg.token;
  ok('注册成功并拿到 token', !!token);

  console.log('\n[2] GitHub 搜索（首次走外网）');
  const first = await must('GET', '/api/skills/search?q=ai%20video&perPage=5', { token });
  ok('返回技能列表', Array.isArray(first.items) && first.items.length > 0, {
    count: first.items?.length,
    total: first.total,
  });
  if (first.degraded) {
    console.log(`     ⚠ GitHub 不可用，已降级：${first.notice}`);
  } else if (first.cached) {
    // 服务端缓存 TTL 是 300s，连着跑两次测试会命中缓存。
    // 这里不能断言 cached 必须为 false，否则测试会变成「看运气」的 flaky test。
    console.log('     服务端已有该查询的缓存（300s TTL 内），跳过「首次必须走外网」的断言');
  } else {
    ok('首次结果确实来自 GitHub（cached=false）', true);
  }
  ok('未发生降级（GitHub 链路正常）', first.degraded === false, { degraded: first.degraded });

  const card = first.items[0];
  ok('技能卡片字段完整', !!card.fullName && !!card.htmlUrl && typeof card.stars === 'number', card);
  ok('topics 被解析为数组', Array.isArray(card.topics), card.topics);
  ok('未安装状态为 false', card.installed === false);

  console.log('\n[3] 缓存命中（不消耗 GitHub 配额）');
  const second = await must('GET', '/api/skills/search?q=ai%20video&perPage=5', { token });
  ok('第二次相同查询命中本地缓存', second.cached === true, { cached: second.cached });
  ok('缓存结果条数一致', second.items.length === first.items.length);
  ok('缓存结果第一条 id 一致', second.items[0].id === first.items[0].id);

  console.log('\n[4] 推荐列表（本地 star 排序，不打 GitHub）');
  const trending = await must('GET', '/api/skills/trending', { token });
  ok('推荐列表非空', Array.isArray(trending) && trending.length > 0, { count: trending?.length });
  const stars = trending.map((s) => s.stars);
  ok('按 star 降序排列', stars.every((v, i) => i === 0 || stars[i - 1] >= v), stars.slice(0, 5));

  console.log('\n[5] 安装 / 卸载');
  const installed = await must('POST', `/api/skills/${card.id}/install`, { token });
  ok('安装后 installed=true', installed.installed === true, installed);
  ok('installCount 至少为 1', installed.installCount >= 1, installed.installCount);

  const myList = await must('GET', '/api/skills/installed', { token });
  ok('已安装列表包含该技能', myList.some((s) => s.id === card.id), myList.map((s) => s.id));

  const dup = await must('POST', `/api/skills/${card.id}/install`, { token });
  ok('重复安装幂等，计数不翻倍', dup.installCount === installed.installCount, {
    before: installed.installCount,
    after: dup.installCount,
  });

  const detail = await must('GET', `/api/skills/${card.id}`, { token });
  ok('详情接口返回 installed=true', detail.installed === true);

  await must('DELETE', `/api/skills/${card.id}/install`, { token });
  const afterUninstall = await must('GET', '/api/skills/installed', { token });
  ok('卸载后已安装列表不再包含它', !afterUninstall.some((s) => s.id === card.id));

  console.log('\n[6] 边界与容错');
  const missing = await api('GET', '/api/skills/99999999', { token });
  ok('不存在的技能返回 404', missing.status === 404, missing);

  const blank = await must('GET', '/api/skills/search', { token });
  ok('不带 q 时使用默认搜索词且不报错', Array.isArray(blank.items), {
    count: blank.items?.length,
    degraded: blank.degraded,
  });

  console.log('\n[7] 推荐分类与说明文档下载');
  const cats = await must('GET', '/api/skills/categories', { token });
  ok('返回预置推荐分类', Array.isArray(cats) && cats.length >= 6, { count: cats?.length });
  ok('每个分类都带查询语句', cats.every((c) => c.key && c.label && c.query), cats?.[0]);

  const readme = await must('GET', `/api/skills/${card.id}/readme`, { token });
  ok('能拉到说明文档', typeof readme.markdown === 'string' && readme.markdown.length > 0, {
    file: readme.fileName,
    length: readme.markdown?.length,
  });
  ok('返回原始地址便于跳转', !!readme.sourceUrl);

  const dl = await fetch(`${BASE}/api/skills/${card.id}/readme/download`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const disposition = dl.headers.get('content-disposition') || '';
  ok('下载接口返回附件响应头', dl.status === 200 && disposition.includes('attachment'), {
    status: dl.status,
    disposition,
  });
  const dlBody = await dl.text();
  ok('下载内容非空', dlBody.length > 0, { length: dlBody.length });

  console.log(`\n技能市场接口全部通过：${passed} 项断言 ✓`);
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('\n✗ ' + e.message);
    process.exit(1);
  });
