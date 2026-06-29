/* 实时联调：SSE 事件流 + 过滤 + 状态计数 */
import { $, fmtTime, esc, toast } from '../util.js';
import { api, connectSSE } from '../api.js';

const counts = { ok: 0, missing: 0, error: 0 };
const MAX_ROWS = 300;
const STATUS_TEXT = { ok: '正常', missing: '缺失', error: '错误' };

function targetOf(d) {
  switch (d.event) {
    case 'element_exposure':
    case 'element_click': return d.element?.eid || '?';
    case 'biz_event': return d.biz?.code || '?';
    case 'page_enter':
    case 'page_exit': return d.page?.pgid || '?';
    default: return '';
  }
}

function passFilter(w) {
  const did = $('#f-did').value.trim();
  const event = $('#f-event').value;
  const status = $('#f-status').value;
  const d = w.data || {};
  if (did && !(d.common?.did || '').includes(did)) return false;
  if (event && d.event !== event) return false;
  if (status && w.status !== status) return false;
  return true;
}

function renderEvent(w, prepend = true) {
  if (!passFilter(w)) return;
  const d = w.data || {};
  const div = document.createElement('div');
  div.className = 'ev ' + w.status;
  const issues = (w.issues || []).map((i) =>
    `<div class="issue ${i.level}">[${i.level === 'missing' ? '缺失' : '错误'}] ${esc(i.field)} — ${esc(i.msg)}</div>`).join('');
  div.innerHTML = `
    <div class="ev-head">
      <span class="badge ${w.status}">${STATUS_TEXT[w.status] || w.status}</span>
      <span class="ev-name">${esc(d.event || '?')}</span>
      <span class="ev-target">${esc(targetOf(d))}</span>
      <span class="ev-meta">
        <span class="meta-pill">${esc(d.common?.platform || '?')}</span>
        <span class="meta-pill mono">did:${esc((d.common?.did || '').slice(0, 10))}</span>
        <span class="meta-pill mono">pg:${esc(d.page?.pgid || '-')}</span>
        <span class="meta-time">${fmtTime(w.stime)}</span>
      </span>
    </div>
    <div class="ev-body">
      ${issues ? `<div class="issues">${issues}</div>` : ''}
      <pre>${esc(JSON.stringify(d, null, 2))}</pre>
    </div>`;
  div.querySelector('.ev-head').onclick = () => div.classList.toggle('open');
  const list = $('#event-list');
  prepend ? list.prepend(div) : list.append(div);
  while (list.children.length > MAX_ROWS) list.lastChild.remove();
  $('#debug-empty').hidden = list.children.length > 0;
}

function bumpCounts(status) {
  counts[status] = (counts[status] || 0) + 1;
  renderCounts();
}

function renderCounts() {
  $('#cnt-ok').textContent = counts.ok;
  $('#cnt-missing').textContent = counts.missing;
  $('#cnt-error').textContent = counts.error;
  const total = counts.ok + counts.missing + counts.error;
  $('#cnt-rate').textContent = total
    ? ((counts.missing + counts.error) / total * 100).toFixed(1) + '%' : '—';
}

async function refilter() {
  $('#event-list').innerHTML = '';
  const q = new URLSearchParams({
    limit: 200, did: $('#f-did').value.trim(),
    event: $('#f-event').value, status: $('#f-status').value,
  });
  const list = await api('/events?' + q);
  list.forEach((w) => renderEvent(w, false));
  $('#debug-empty').hidden = $('#event-list').children.length > 0;
}

export async function initDebug() {
  ['#f-did', '#f-event', '#f-status'].forEach((sel) =>
    $(sel).addEventListener('input', refilter));

  $('#btn-clear').onclick = async () => {
    const r = await api('/clear', { method: 'POST' });
    if (r.error) { toast(r.error, 'error'); return; }
    $('#event-list').innerHTML = '';
    counts.ok = counts.missing = counts.error = 0;
    renderCounts();
    $('#debug-empty').hidden = false;
    toast('已清空联调缓冲');
  };

  const recent = await api('/events?limit=100');
  recent.reverse().forEach((w) => { bumpCounts(w.status); renderEvent(w); });
  $('#debug-empty').hidden = $('#event-list').children.length > 0;
  renderCounts();

  connectSSE(
    (w) => { bumpCounts(w.status); if (!$('#f-pause').checked) renderEvent(w); },
    (up) => {
      const conn = $('#conn');
      conn.classList.toggle('off', !up);
      conn.innerHTML = `<span class="conn-dot"></span>${up ? '实时连接' : '已断开 · 重连中'}`;
    });
}
