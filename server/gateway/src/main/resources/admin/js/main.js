/* GISO 玑源 · 管理控制台入口 */
import { $, $$ } from './util.js';
import { initDebug } from './views/debug.js';
import { initAssert } from './views/assert.js';
import { initRegistry, renderRegistry } from './views/registry.js';
import { initStats, loadStats } from './views/stats.js';

const VIEWS = {
  debug: { title: '实时联调', desc: '设备开 debug 模式后实时核对上报，红错误 / 黄缺失 / 绿正常' },
  assert: { title: '用例断言', desc: '声明链路期望事件序列，与设备实报做有序比对，一键回归' },
  registry: { title: '注册表配置', desc: '改动直接写回 schema/*.yaml，Git 提交仍是最终评审与审计记录', onShow: renderRegistry },
  stats: { title: '质量统计', desc: '事件 / 参数 / 版本三个维度的上报质量，配合 /metrics 告警使用', onShow: loadStats },
};

function show(view) {
  $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
  $$('.view').forEach((p) => p.classList.toggle('active', p.id === 'view-' + view));
  $('#page-title').textContent = VIEWS[view].title;
  $('#page-desc').textContent = VIEWS[view].desc;
  VIEWS[view].onShow?.();
}

$$('.nav-item').forEach((btn) => { btn.onclick = () => show(btn.dataset.view); });

initDebug();
initAssert();
initRegistry();
initStats();
show('debug');
