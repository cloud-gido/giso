/* 注册表配置：参数池/页面池/元素池/业务事件 CRUD + 表头列拖拽排序 */
import { $, $$, esc, toast } from '../util.js';
import { api } from '../api.js';
import { getMe, isAdmin, isEditor, canEditRegistry } from '../session.js';

let registry = { params: [], pages: [], elements: [], events: [] };
let curKind = 'params';

const KIND_META = {
  params: { id: 'key', name: '参数',
    cols: ['key', 'type', 'desc', 'rule', 'owner', 'since', 'status'],
    labels: { key: '参数 key', type: '类型', desc: '说明', rule: '取值规则', owner: '负责人', since: '起始版本', status: '状态' } },
  pages: { id: 'pgid', name: '页面',
    cols: ['pgid', 'screenshot', 'desc', 'domain', 'params', 'elements', 'owner', 'status'],
    labels: { pgid: '页面 pgid', screenshot: '页面截图', desc: '页面说明', domain: '业务域', params: '页面参数', elements: '绑定元素（结构体）', owner: '负责人', status: '状态' } },
  elements: { id: 'eid', name: '元素',
    cols: ['eid', 'desc', 'domain', 'params', 'children', 'owner', 'status'],
    labels: { eid: '元素 eid', desc: '说明', domain: '业务域', params: '必携参数', children: '子元素', owner: '负责人', status: '状态' } },
  events: { id: 'code', name: '业务事件',
    cols: ['code', 'desc', 'domain', 'source', 'params', 'owner', 'status'],
    labels: { code: '事件 code', desc: '说明', domain: '业务域', source: '事实源', params: '事件参数', owner: '负责人', status: '状态' } },
};
const LIST_COLS = ['params', 'children', 'elements'];
const TEXTAREA_COLS = ['desc'];
const STATUS_LABEL = {
  draft: '登记中', dev: '开发中', testing: '测试中', pending: '待审批',
  live: '线上', deprecated: '已废弃',
};

function colKey() { return `reg-cols-${curKind}`; }

function getCols(meta) {
  try {
    const saved = JSON.parse(localStorage.getItem(colKey()) || 'null');
    if (Array.isArray(saved) && saved.length && saved.every((c) => meta.cols.includes(c))) {
      const missing = meta.cols.filter((c) => !saved.includes(c));
      return [...saved, ...missing];
    }
  } catch { /* ignore */ }
  return [...meta.cols];
}

function saveCols(cols) { localStorage.setItem(colKey(), JSON.stringify(cols)); }

function cell(it, c, meta) {
  const v = it[c];
  if (c === 'screenshot') {
    if (!v) return '<span class="muted">—</span>';
    const src = esc(String(v));
    return `<a href="${src}" target="_blank" class="shot-link" title="查看截图">
      <img class="shot-thumb" src="${src}" alt="" loading="lazy"
           onerror="this.style.display='none';this.nextElementSibling.style.display='inline'">
      <span class="shot-fallback" style="display:none">${src}</span></a>`;
  }
  if (c === 'status') {
    const s = v || 'live';
    return `<span class="status-pill ${esc(s)}">${STATUS_LABEL[s] || esc(s)}</span>`;
  }
  if (c === 'source') {
    return `<span class="src-pill ${v === 'server' ? 'server' : 'client'}">${v === 'server' ? 'server 事实' : 'client 行为'}</span>`;
  }
  if (v == null) return '<span class="muted">—</span>';
  if (Array.isArray(v)) {
    return v.map((x) => `<span class="pill">${esc(x)}</span>`).join('') || '<span class="muted">—</span>';
  }
  if (c === meta.id) return `<span class="key">${esc(v)}</span>`;
  if (c === 'desc' && curKind === 'pages') {
    return `<span class="desc-cell" title="${esc(v)}">${esc(v)}</span>`;
  }
  return esc(v);
}

function bindColDrag(table, meta) {
  const ths = table.querySelectorAll('th[data-col]');
  let dragCol = null;
  ths.forEach((th) => {
    th.draggable = true;
    th.addEventListener('dragstart', (e) => {
      dragCol = th.dataset.col;
      th.classList.add('dragging');
      e.dataTransfer.effectAllowed = 'move';
    });
    th.addEventListener('dragend', () => th.classList.remove('dragging'));
    th.addEventListener('dragover', (e) => { e.preventDefault(); th.classList.add('drag-over'); });
    th.addEventListener('dragleave', () => th.classList.remove('drag-over'));
    th.addEventListener('drop', (e) => {
      e.preventDefault();
      th.classList.remove('drag-over');
      const target = th.dataset.col;
      if (!dragCol || dragCol === target) return;
      const cols = getCols(meta);
      const from = cols.indexOf(dragCol);
      const to = cols.indexOf(target);
      if (from < 0 || to < 0) return;
      cols.splice(from, 1);
      cols.splice(to, 0, dragCol);
      saveCols(cols);
      renderRegistry();
      toast('列顺序已保存');
    });
  });
}

function canModifyRow(it) {
  if (!canEditRegistry()) return false;
  if (isAdmin()) return true;
  const s = it.status || 'live';
  return s === 'pending' || s === 'draft' || !s;
}

export async function renderRegistry() {
  const regMeta = await api('/registry/meta').catch(() => ({}));
  if (regMeta.revision != null) {
    $('#reg-meta').textContent = `revision ${regMeta.revision} · ${regMeta.entries ?? '—'} 条 · ${regMeta.backend || '—'}`;
  }
  registry = await api('/registry');
  const meta = KIND_META[curKind];
  const cols = getCols(meta);
  const kw = $('#reg-search').value.trim().toLowerCase();
  const rows = registry[curKind].filter((it) => !kw || JSON.stringify(it).toLowerCase().includes(kw));
  $('#reg-count').textContent = `${rows.length} 条`;
  $('#reg-table').innerHTML = `<div class="col-hint">拖拽表头可调整列顺序 · <button type="button" class="link-btn" id="btn-reset-cols">恢复默认</button></div>
    <table>
    <thead><tr>${cols.map((c) =>
      `<th data-col="${c}" title="拖拽调整列顺序"><span class="th-grip">⠿</span>${meta.labels[c]}</th>`).join('')}<th class="th-fixed">操作</th></tr></thead>
    <tbody>${rows.map((it) => `<tr>
      ${cols.map((c) => `<td>${cell(it, c, meta)}</td>`).join('')}
      <td class="row-actions">
        ${canModifyRow(it) ? `<button data-act="edit" data-key="${esc(it[meta.id])}">编辑</button>` : ''}
        ${actionBtns(it, meta)}
        <button data-act="audit" data-key="${esc(it[meta.id])}">审计</button>
        ${canModifyRow(it) ? `<button data-act="del" data-key="${esc(it[meta.id])}" class="danger">删除</button>` : ''}
      </td></tr>`).join('') || `<tr><td colspan="${cols.length + 1}" class="muted empty-cell">没有匹配的条目</td></tr>`}
    </tbody></table>`;
  $('#reg-table').querySelectorAll('button[data-act]').forEach((b) => {
    const key = b.dataset.key;
    const act = b.dataset.act;
    b.onclick = () => {
      if (act === 'edit') openEditor(key);
      else if (act === 'del') doDelete(key);
      else if (act === 'audit') openAudit(key);
      else if (act === 'pub') doPublish(key);
      else if (act === 'dep') doDeprecate(key);
      else if (act === 'approve') doApprove(key);
      else if (act === 'reject') doReject(key);
    };
  });
  $('#btn-reset-cols')?.addEventListener('click', () => {
    localStorage.removeItem(colKey());
    renderRegistry();
    toast('已恢复默认列顺序');
  });
  bindColDrag($('#reg-table').querySelector('table'), meta);
}

function actionBtns(it, meta) {
  const s = it.status || 'live';
  const me = getMe();
  let html = '';
  if (isAdmin()) {
    if (s === 'pending') {
      html += `<button data-act="approve" data-key="${esc(it[meta.id])}" class="primary">批准</button>`;
      html += `<button data-act="reject" data-key="${esc(it[meta.id])}">驳回</button>`;
    } else if (s !== 'live' && s !== 'deprecated') {
      html += `<button data-act="pub" data-key="${esc(it[meta.id])}" class="primary">发布</button>`;
    }
    if (s === 'live' || s === 'testing') {
      html += `<button data-act="dep" data-key="${esc(it[meta.id])}">废弃</button>`;
    }
  }
  if (!canEditRegistry()) return html;
  if (isEditor() && s !== 'pending' && s !== 'draft' && s) return html;
  return html;
}

async function doApprove(key) {
  if (!confirm(`批准 ${key} 上线？`)) return;
  const r = await api(`/registry/${curKind}/approve?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已批准 ${key}`); renderRegistry(); document.dispatchEvent(new CustomEvent('giso:pending-changed')); }
}

async function doReject(key) {
  if (!confirm(`驳回 ${key}？`)) return;
  const r = await api(`/registry/${curKind}/reject?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已驳回 ${key}`); renderRegistry(); }
}

async function doPublish(key) {
  if (!confirm(`发布 ${key} 为 live？发布后将参与线上校验。`)) return;
  const r = await api(`/registry/${curKind}/publish?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已发布 ${key}`); renderRegistry(); }
}

async function doDeprecate(key) {
  if (!confirm(`废弃 ${key}？`)) return;
  const r = await api(`/registry/${curKind}/deprecate?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已废弃 ${key}`); renderRegistry(); }
}

async function openAudit(key) {
  const rows = await api(`/registry/audit?kind=${curKind}&key=${encodeURIComponent(key)}&limit=30`);
  const body = (Array.isArray(rows) ? rows : []).map((r) =>
    `<tr><td>${esc(r.created_at || '')}</td><td>${esc(r.action)}</td><td>${esc(r.operator)}</td></tr>`
  ).join('') || '<tr><td colspan="3" class="muted">无记录</td></tr>';
  $('#audit-title').textContent = `审计 · ${key}`;
  $('#audit-body').innerHTML = `<table><thead><tr><th>时间</th><th>动作</th><th>操作者</th></tr></thead><tbody>${body}</tbody></table>`;
  $('#audit-dialog').showModal();
}

async function doDelete(key) {
  if (!confirm(`确认删除 ${key}？已上报数据中的历史引用不受影响，但校验将把它判为未登记。`)) return;
  const r = await api(`/registry/${curKind}?key=${encodeURIComponent(key)}`, { method: 'DELETE' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已删除 ${key}`); renderRegistry(); }
}

const field = (label, inner) => `<div class="field"><label>${label}</label>${inner}</div>`;

function editorField(c, meta, item, key) {
  const v = item[c] ?? '';
  const val = Array.isArray(v) ? v.join(', ') : v;
  if (c === 'type') {
    return field(meta.labels[c], `<select name="type">${['string', 'int', 'float', 'bool', 'object']
      .map((t) => `<option ${t === v ? 'selected' : ''}>${t}</option>`).join('')}</select>`);
  }
  if (c === 'source') {
    return field(meta.labels[c], `<select name="source">${['client', 'server']
      .map((t) => `<option ${t === v ? 'selected' : ''}>${t}</option>`).join('')}</select>`);
  }
  if (c === 'status') {
    if (isEditor()) {
      return field(meta.labels[c], `<input name="status" value="pending" readonly title="编辑员提交后进入待审批">`
        + `<span class="hint-inline">保存后进入「待审批」，管理员批准后生效</span>`);
    }
    return field(meta.labels[c], `<select name="status">${['', 'draft', 'dev', 'testing', 'pending', 'live', 'deprecated']
      .map((t) => `<option value="${t}" ${t === (v || '') ? 'selected' : ''}>${t ? `${t} · ${STATUS_LABEL[t]}` : '（缺省 = live 线上）'}</option>`).join('')}</select>`);
  }
  if (c === 'screenshot') {
    return field(meta.labels[c], `
      <input name="screenshot" value="${esc(val)}" placeholder="截图 URL，如 /admin/screenshots/home.png 或 CDN 地址">
      <div class="shot-preview" id="shot-preview">${val ? `<img src="${esc(val)}" alt="">` : '<span class="muted">填写 URL 后预览</span>'}</div>`);
  }
  if (TEXTAREA_COLS.includes(c) && curKind === 'pages') {
    return field(meta.labels[c], `<textarea name="${c}" rows="3" placeholder="页面用途、入口、关键交互说明…">${esc(val)}</textarea>`);
  }
  const ph = LIST_COLS.includes(c) ? '逗号分隔，如: vid, pos' : '';
  const ro = c === meta.id && key ? 'readonly' : '';
  return field(meta.labels[c], `<input name="${c}" value="${esc(val)}" placeholder="${ph}" ${ro}>`);
}

function openEditor(key) {
  const meta = KIND_META[curKind];
  const item = key ? registry[curKind].find((it) => it[meta.id] === key) : {};
  $('#editor-title').textContent = `${key ? '编辑' : '新增'} · ${meta.name}`;
  $('#editor-fields').innerHTML = meta.cols.map((c) => editorField(c, meta, item, key)).join('');
  const shotInput = $('#editor-fields').querySelector('input[name="screenshot"]');
  if (shotInput) {
    shotInput.addEventListener('input', () => {
      const url = shotInput.value.trim();
      $('#shot-preview').innerHTML = url
        ? `<img src="${esc(url)}" alt="" onerror="this.outerHTML='<span class=muted>无法加载预览</span>'">`
        : '<span class="muted">填写 URL 后预览</span>';
    });
  }
  $('#editor').showModal();
  $('#editor-form').onsubmit = async (e) => {
    if (e.submitter && e.submitter.value === 'cancel') return;
    const fd = new FormData($('#editor-form'));
    const out = {};
    meta.cols.forEach((c) => {
      const v = (fd.get(c) || '').toString().trim();
      if (LIST_COLS.includes(c)) {
        const arr = v ? v.split(',').map((s) => s.trim()).filter(Boolean) : [];
        if (arr.length || c === 'params') out[c] = arr;
      } else if (v) out[c] = v;
    });
    const r = await api('/registry/' + curKind, { method: 'POST', body: JSON.stringify(out) });
    if (r.error) { toast(r.error, 'error'); e.preventDefault(); }
    else {
      toast(isEditor() ? '已提交待审批' : '已保存');
      renderRegistry();
      if (isEditor()) document.dispatchEvent(new CustomEvent('giso:pending-changed'));
    }
  };
}

export function initRegistry() {
  $$('.seg-btn').forEach((btn) => {
    btn.onclick = () => {
      $$('.seg-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      curKind = btn.dataset.kind;
      renderRegistry();
    };
  });
  $('#reg-search').addEventListener('input', renderRegistry);
  $('#btn-add').onclick = () => openEditor(null);
  if (!canEditRegistry()) $('#btn-add')?.setAttribute('hidden', '');
}
