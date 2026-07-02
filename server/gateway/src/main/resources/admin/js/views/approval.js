/* 待审批收件箱（管理员批准 / 驳回） */
import { $, esc, toast } from '../util.js';
import { api } from '../api.js';
import { canApprove, getMe } from '../session.js';

const KIND_LABEL = { params: '参数', pages: '页面', elements: '元素', events: '业务事件' };
const ID_FIELD = { params: 'key', pages: 'pgid', elements: 'eid', events: 'code' };

export async function renderApproval() {
  const box = $('#approval-list');
  if (!canApprove()) {
    box.innerHTML = '<p class="muted">仅管理员可审批。编辑员提交的条目会出现在此。</p>';
    return;
  }
  const pending = await api('/registry/pending');
  const rows = [];
  for (const [kind, items] of Object.entries(pending || {})) {
    for (const it of items || []) {
      const id = it[ID_FIELD[kind]];
      rows.push({ kind, id, it });
    }
  }
  rows.sort((a, b) => String(a.it.desc || a.id).localeCompare(String(b.it.desc || b.id)));
  $('#approval-count').textContent = `${rows.length} 条待审`;
  if (!rows.length) {
    box.innerHTML = '<p class="empty">暂无待审批条目</p>';
    return;
  }
  box.innerHTML = `<table>
    <thead><tr><th>类型</th><th>标识</th><th>说明</th><th>负责人</th><th>操作</th></tr></thead>
    <tbody>${rows.map(({ kind, id, it }) => `<tr>
      <td>${KIND_LABEL[kind] || kind}</td>
      <td><span class="key">${esc(id)}</span></td>
      <td>${esc(it.desc || '—')}</td>
      <td>${esc(it.owner || '—')}</td>
      <td class="row-actions">
        <button class="primary" data-act="approve" data-kind="${kind}" data-key="${esc(id)}">批准上线</button>
        <button data-act="reject" data-kind="${kind}" data-key="${esc(id)}">驳回</button>
      </td></tr>`).join('')}
    </tbody></table>`;
  box.querySelectorAll('button[data-act]').forEach((btn) => {
    btn.onclick = async () => {
      const { kind, key } = btn.dataset;
      const act = btn.dataset.act;
      const msg = act === 'approve'
        ? `批准 ${key} 上线？批准后立即参与 /v1/track 校验。`
        : `驳回 ${key}？将退回「登记中」状态。`;
      if (!confirm(msg)) return;
      const r = await api(`/registry/${kind}/${act}?key=${encodeURIComponent(key)}`, { method: 'POST' });
      if (r.error) toast(r.error, 'error');
      else {
        toast(act === 'approve' ? `已批准 ${key}` : `已驳回 ${key}`);
        renderApproval();
        const m = getMe();
        if (m.pending_count != null) m.pending_count = Math.max(0, m.pending_count - 1);
        document.dispatchEvent(new CustomEvent('giso:pending-changed'));
      }
    };
  });
}

export function initApproval() {
  $('#btn-approval-refresh')?.addEventListener('click', renderApproval);
}
