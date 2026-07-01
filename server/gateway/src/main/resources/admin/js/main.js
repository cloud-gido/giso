/* GISO 玑源 · 管理控制台入口 */
import { $, $$ } from './util.js';
import { api } from './api.js';
import { setMe } from './session.js';
import { initDebug } from './views/debug.js';
import { initAssert } from './views/assert.js';
import { initRegistry, renderRegistry } from './views/registry.js';
import { initStats, loadStats } from './views/stats.js';
import { initApproval, renderApproval } from './views/approval.js';
import { initUsers, renderUsers } from './views/users.js';

const ROLE_LABEL = { admin: '管理员', editor: '编辑员', viewer: '只读' };

const VIEWS = {
  debug: { title: '实时联调', desc: '设备开 debug 模式后实时核对上报，红错误 / 黄缺失 / 绿正常' },
  assert: { title: '用例断言', desc: '声明链路期望事件序列，与设备实报做有序比对，一键回归' },
  approval: { title: '待审批', desc: '编辑员提交的登记变更；管理员批准后参与线上校验', onShow: renderApproval },
  registry: { title: '注册表配置', desc: '生产写 PostgreSQL；编辑员提交待审批，管理员批准后生效', onShow: renderRegistry },
  stats: { title: '质量统计', desc: '事件 / 参数 / 版本三个维度的上报质量，配合 /metrics 告警使用', onShow: loadStats },
  users: { title: '账号管理', desc: '管理台 admin / editor / viewer 账号（PostgreSQL 持久化）', onShow: renderUsers },
};

function show(view) {
  $$('.nav-item[data-view]').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
  $$('.view').forEach((p) => p.classList.toggle('active', p.id === 'view-' + view));
  $('#page-title').textContent = VIEWS[view].title;
  $('#page-desc').textContent = VIEWS[view].desc;
  VIEWS[view].onShow?.();
}

$$('.nav-item[data-view]').forEach((btn) => { btn.onclick = () => show(btn.dataset.view); });

function updatePendingBadge(count) {
  const badge = $('#nav-pending-badge');
  if (!badge) return;
  if (count > 0) {
    badge.hidden = false;
    badge.textContent = String(count);
  } else {
    badge.hidden = true;
  }
}

function applyRole(me) {
  const pill = $('#user-pill');
  if (!me?.username) return;
  pill.hidden = false;
  const roleLabel = ROLE_LABEL[me.role] || me.role;
  pill.textContent = `${me.username} · ${roleLabel}`;
  pill.title = roleLabel;

  if (me.role === 'viewer') {
    $('#btn-clear')?.setAttribute('hidden', '');
    $('#btn-add')?.setAttribute('hidden', '');
  }
  if (me.role !== 'admin') {
    $$('.nav-admin-only').forEach((el) => el.setAttribute('hidden', ''));
  }
  updatePendingBadge(me.pending_count || 0);
}

initDebug();
initAssert();
initApproval();
initRegistry();
initStats();
initUsers();
show('debug');

api('/me').then((me) => {
  setMe(me);
  applyRole(me);
}).catch(() => {});

document.addEventListener('giso:pending-changed', () => {
  api('/me').then((me) => {
    setMe(me);
    updatePendingBadge(me.pending_count || 0);
  });
});
