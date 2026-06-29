/* 质量统计：KPI 卡片 + 事件/参数/版本三张维度表 */
import { $, esc } from '../util.js';
import { api } from '../api.js';

const rate = (c) => c.total ? ((c.missing + c.error) / c.total * 100).toFixed(1) + '%' : '—';

function rateCell(c) {
  if (!(c.missing + c.error)) return '<span class="rate-ok">0%</span>';
  return `<span class="num ${c.error ? 'bad' : 'warn'}">${rate(c)}</span>`;
}

export async function loadStats() {
  const [s, cov] = await Promise.all([api('/stats'), api('/coverage')]);
  const events = s.events || [];

  const total = events.reduce((a, c) => a + c.total, 0);
  const missing = events.reduce((a, c) => a + c.missing, 0);
  const error = events.reduce((a, c) => a + c.error, 0);
  $('#kpi-total').textContent = total.toLocaleString();
  $('#kpi-missing').textContent = missing.toLocaleString();
  $('#kpi-error').textContent = error.toLocaleString();
  $('#kpi-rate').textContent = total ? ((missing + error) / total * 100).toFixed(2) + '%' : '—';

  $('#stats-events').innerHTML = `<table>
    <thead><tr><th>事件</th><th class="num">上报量</th><th class="num">缺失量</th><th class="num">错误量</th><th class="num">异常率</th></tr></thead>
    <tbody>${events.sort((a, b) => b.total - a.total).map((c) => `<tr>
      <td><span class="key">${esc(c.key)}</span></td>
      <td class="num">${c.total}</td>
      <td class="num ${c.missing ? 'warn' : ''}">${c.missing}</td>
      <td class="num ${c.error ? 'bad' : ''}">${c.error}</td>
      <td class="num">${rateCell(c)}</td>
    </tr>`).join('') || '<tr><td colspan="5" class="muted empty-cell">暂无数据</td></tr>'}</tbody></table>`;

  $('#stats-params').innerHTML = `<table>
    <thead><tr><th>参数/字段</th><th class="num">缺失量</th><th class="num">错误量</th></tr></thead>
    <tbody>${(s.params || []).sort((a, b) => (b.missing + b.error) - (a.missing + a.error)).map((c) => `<tr>
      <td><span class="key">${esc(c.key)}</span></td>
      <td class="num ${c.missing ? 'warn' : ''}">${c.missing}</td>
      <td class="num ${c.error ? 'bad' : ''}">${c.error}</td>
    </tr>`).join('') || '<tr><td colspan="3" class="muted empty-cell">暂无异常</td></tr>'}</tbody></table>`;

  $('#stats-versions').innerHTML = `<table>
    <thead><tr><th>平台 · 版本</th><th class="num">上报量</th><th class="num">缺失量</th><th class="num">错误量</th><th class="num">异常率</th></tr></thead>
    <tbody>${(s.versions || []).sort((a, b) => b.total - a.total).map((c) => `<tr>
      <td><span class="key">${esc(c.key)}</span></td>
      <td class="num">${c.total}</td>
      <td class="num ${c.missing ? 'warn' : ''}">${c.missing}</td>
      <td class="num ${c.error ? 'bad' : ''}">${c.error}</td>
      <td class="num">${rateCell(c)}</td>
    </tr>`).join('') || '<tr><td colspan="5" class="muted empty-cell">暂无数据</td></tr>'}</tbody></table>`;

  const summary = cov.summary || {};
  $('#cov-pages-missing').textContent = `${summary.pages_missing ?? '—'} / ${summary.pages_live ?? '—'}`;
  $('#cov-elements-missing').textContent = `${summary.elements_missing ?? '—'} / ${summary.elements_live ?? '—'}`;
  $('#cov-events-missing').textContent = `${summary.events_missing ?? '—'} / ${summary.events_live ?? '—'}`;

  const missingRows = [
    ...(cov.pages_missing || []).map((r) => ({ kind: '页面', id: r.pgid, desc: r.desc })),
    ...(cov.elements_missing || []).map((r) => ({ kind: '元素', id: r.eid, desc: r.desc })),
    ...(cov.events_missing || []).map((r) => ({ kind: '业务事件', id: r.code, desc: r.desc })),
  ];
  $('#stats-coverage').innerHTML = `<table>
    <thead><tr><th>类型</th><th>ID</th><th>说明</th></tr></thead>
    <tbody>${missingRows.map((r) => `<tr>
      <td>${esc(r.kind)}</td>
      <td><span class="key">${esc(r.id)}</span></td>
      <td class="muted">${esc(r.desc || '—')}</td>
    </tr>`).join('') || '<tr><td colspan="3" class="muted empty-cell">全部 live 条目均已见过上报 🎉</td></tr>'}</tbody></table>`;
}

export function initStats() {
  $('#btn-stats-refresh').onclick = loadStats;
}
