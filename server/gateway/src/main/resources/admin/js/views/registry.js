/* 注册表配置：参数池/页面池/元素池/业务事件 CRUD + 批量操作 + CSV 导入 */
import { $, $$, esc, toast } from '../util.js';
import { api, getSpace, uploadScreenshot } from '../api.js';
import {
  POOL_FIELD,
  renderPoolPicker,
  renderDomainPicker,
  renderOwnerPicker,
  bindEditorPickers,
  validatePoolRefs,
  dedupeListFields,
  stripInvalidPoolRefs,
  saveEditorDraft,
  peekEditorDraft,
  clearEditorDraft,
  readEditorFormState,
  refreshEditorPickers,
  updateRefHintsPanel,
  summarizeHintsToast,
} from '../registry-picker.js';
import {
  isSpaceAdmin, isEditor, canEditRegistry, canApprove,
} from '../session.js';

let registry = { params: [], pages: [], elements: [], events: [] };
let regMeta = {};
let pageShotByElement = new Map();
let registryLoaded = false;
let registryLoading = null;
let searchTimer = null;
let refHintsTimer = null;
let curKind = 'params';
const selected = new Set();

const KIND_META = {
  params: { id: 'key', name: '参数',
    cols: ['key', 'type', 'desc', 'rule', 'owner', 'since', 'issue_link', 'status'],
    labels: { key: '参数 key', type: '类型', desc: '说明', rule: '取值规则', owner: '负责人', since: '起始版本', issue_link: '需求单', status: '状态' } },
  pages: { id: 'pgid', name: '页面',
    cols: ['pgid', 'screenshot', 'desc', 'domain', 'params', 'elements', 'owner', 'since', 'issue_link', 'status'],
    labels: { pgid: '页面 pgid', screenshot: '预览图', desc: '页面说明', domain: '业务域', params: '页面参数', elements: '绑定元素（结构体）', owner: '负责人', since: '起始版本', issue_link: '需求单', status: '状态' } },
  elements: { id: 'eid', name: '元素',
    cols: ['eid', 'screenshot', 'desc', 'domain', 'params', 'children', 'owner', 'since', 'issue_link', 'status'],
    labels: { eid: '元素 eid', screenshot: '预览图', desc: '说明', domain: '业务域', params: '必携参数', children: '子元素', owner: '负责人', since: '起始版本', issue_link: '需求单', status: '状态' } },
  events: { id: 'code', name: '业务事件',
    cols: ['code', 'screenshot', 'desc', 'domain', 'source', 'params', 'owner', 'since', 'issue_link', 'status'],
    labels: { code: '事件 code', screenshot: '预览图', desc: '说明', domain: '业务域', source: '事实源', params: '事件参数', owner: '负责人', since: '起始版本', issue_link: '需求单', status: '状态' } },
};
const LIST_COLS = ['params', 'children', 'elements'];
const TEXTAREA_COLS = ['desc'];
const STATUS_LABEL = {
  draft: '登记中', dev: '开发中', testing: '测试中', pending: '待审批',
  live: '线上', deprecated: '已废弃',
};

const BULK_LABELS = {
  submit: '提交审批', approve: '批准', reject: '驳回',
  publish: '发布', deprecate: '废弃', delete: '删除',
};

const DEFAULT_COL_WIDTHS = {
  key: 132, pgid: 128, eid: 128, code: 128,
  type: 72, screenshot: 96, desc: 200, rule: 180,
  domain: 88, params: 140, elements: 140, children: 120,
  source: 96, owner: 100, since: 72, issue_link: 88, status: 80,
};
const ACTIONS_COL_WIDTH = 200;

function colKey() { return `reg-cols-${curKind}`; }
function colWidthsKey() { return `reg-col-widths-${curKind}`; }
function rowKey(it, meta) { return it[meta.id]; }

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

function defaultColWidth(c) {
  if (c === 'screenshot') {
    return (parseInt(getComputedStyle(document.documentElement).getPropertyValue('--reg-shot-w'), 10) || 72) + 24;
  }
  return DEFAULT_COL_WIDTHS[c] || 120;
}

function getColWidths(meta, cols) {
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(colWidthsKey()) || '{}'); } catch { /* ignore */ }
  const out = {};
  cols.forEach((c) => { out[c] = Math.max(48, saved[c] || defaultColWidth(c)); });
  return out;
}

function saveColWidths(widths) {
  localStorage.setItem(colWidthsKey(), JSON.stringify(widths));
}

function colWidthStyle(w) {
  return `width:${w}px;min-width:${w}px;max-width:${w}px`;
}

function applyColWidth(table, col, w) {
  table.querySelectorAll(`[data-col="${col}"]`).forEach((el) => {
    el.style.width = `${w}px`;
    el.style.minWidth = `${w}px`;
    el.style.maxWidth = `${w}px`;
  });
}

function rebuildPageShotIndex() {
  pageShotByElement = new Map();
  for (const p of registry.pages || []) {
    const shot = p.screenshot;
    if (!shot) continue;
    const els = Array.isArray(p.elements) ? p.elements : [];
    for (const eid of els) {
      if (eid && !pageShotByElement.has(eid)) pageShotByElement.set(eid, shot);
    }
  }
}

function previewUrl(it, meta) {
  if (it.screenshot) return String(it.screenshot);
  if (curKind === 'elements') {
    const inherited = pageShotByElement.get(it[meta.id]);
    if (inherited) return inherited;
  }
  return null;
}

function renderPreviewCell(it, meta) {
  const url = previewUrl(it, meta);
  if (!url) return '<span class="muted">—</span>';
  const src = esc(url);
  const inherited = !it.screenshot && curKind === 'elements';
  const tip = inherited ? '继承自所属页面截图' : '点击查看大图';
  return `<a href="${src}" target="_blank" class="shot-link" title="${tip}">
    <img class="shot-thumb" src="${src}" alt="" loading="lazy"
         onerror="this.style.display='none';this.nextElementSibling.style.display='inline'">
    <span class="shot-fallback" style="display:none">图</span></a>`;
}

function filteredRows() {
  const meta = KIND_META[curKind];
  const kw = $('#reg-search').value.trim().toLowerCase();
  return (registry[curKind] || []).filter((it) => !kw || JSON.stringify(it).toLowerCase().includes(kw));
}

function clearSelection() {
  selected.clear();
  updateBulkBar();
}

function updateBulkBar() {
  const bar = $('#reg-bulk-bar');
  if (!bar) return;
  const n = selected.size;
  const showBulk = canEditRegistry() || canApprove();
  bar.hidden = !showBulk || n === 0;
  $('#reg-bulk-count').textContent = `已选 ${n} 条`;
  const rows = filteredRows();
  const allOnPage = rows.length > 0 && rows.every((it) => selected.has(rowKey(it, KIND_META[curKind])));
  const selAll = $('#reg-select-all');
  if (selAll) {
    selAll.checked = allOnPage;
    selAll.indeterminate = n > 0 && !allOnPage;
  }
  const editor = isEditor() && !isSpaceAdmin();
  $('#reg-bulk-submit')?.toggleAttribute('hidden', !canEditRegistry());
  $('#reg-bulk-delete')?.toggleAttribute('hidden', !canEditRegistry());
  $('#reg-bulk-approve')?.toggleAttribute('hidden', !canApprove());
  $('#reg-bulk-reject')?.toggleAttribute('hidden', !canApprove());
  $('#reg-bulk-publish')?.toggleAttribute('hidden', !canApprove() || editor);
  $('#reg-bulk-deprecate')?.toggleAttribute('hidden', !canApprove() || editor);
}

function cell(it, c, meta) {
  const v = it[c];
  if (c === 'screenshot') return renderPreviewCell(it, meta);
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
  if (c === 'desc' && (curKind === 'pages' || curKind === 'elements')) {
    return `<span class="desc-cell" title="${esc(v)}">${esc(v)}</span>`;
  }
  return esc(v);
}

function bindColDrag(table, meta) {
  const ths = table.querySelectorAll('th[data-col]');
  let dragCol = null;
  ths.forEach((th) => {
    const grip = th.querySelector('.th-grip');
    if (!grip) return;
    grip.draggable = true;
    grip.addEventListener('dragstart', (e) => {
      dragCol = th.dataset.col;
      th.classList.add('dragging');
      e.dataTransfer.effectAllowed = 'move';
      e.stopPropagation();
    });
    grip.addEventListener('dragend', () => th.classList.remove('dragging'));
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
      renderTable();
      toast('列顺序已保存');
    });
  });
}

function bindColResize(table, cols, widths) {
  table.querySelectorAll('th[data-col]').forEach((th) => {
    const col = th.dataset.col;
    const handle = document.createElement('span');
    handle.className = 'col-resize-handle';
    handle.title = '拖拽调整列宽';
    th.appendChild(handle);
    handle.addEventListener('mousedown', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const startX = e.clientX;
      const startW = th.offsetWidth;
      const onMove = (ev) => {
        const w = Math.max(48, Math.min(640, startW + ev.clientX - startX));
        widths[col] = w;
        applyColWidth(table, col, w);
      };
      const onUp = () => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.classList.remove('col-resizing');
        saveColWidths(widths);
      };
      document.body.classList.add('col-resizing');
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  });
}

function canModifyRow(it) {
  if (!canEditRegistry()) return false;
  if (isSpaceAdmin()) return true;
  const s = it.status || 'live';
  return s === 'pending' || s === 'draft' || !s;
}

async function fetchRegistry(force = false) {
  if (!force && registryLoaded) return;
  if (registryLoading) return registryLoading;
  registryLoading = Promise.all([
    api('/registry/meta').catch(() => ({})),
    api('/registry'),
  ]).then(([meta, data]) => {
    regMeta = meta || {};
    registry = data || { params: [], pages: [], elements: [], events: [] };
    rebuildPageShotIndex();
    registryLoaded = true;
    registryLoading = null;
  }).catch((e) => {
    registryLoading = null;
    throw e;
  });
  return registryLoading;
}

function renderTable() {
  $$('.seg-btn').forEach((b) => b.classList.toggle('active', b.dataset.kind === curKind));
  if (regMeta.revision != null) {
    $('#reg-meta').textContent = `revision ${regMeta.revision} · ${regMeta.entries ?? '—'} 条 · ${regMeta.backend || '—'}`;
  }
  const meta = KIND_META[curKind];
  const cols = getCols(meta);
  const widths = getColWidths(meta, cols);
  const rows = filteredRows();
  const showCheck = canEditRegistry() || canApprove();
  const w = (c) => colWidthStyle(widths[c]);
  $('#reg-count').textContent = `${rows.length} 条`;
  $('#reg-table').innerHTML = `<table>
    <thead><tr>
      ${showCheck ? `<th class="reg-th-check" style="${colWidthStyle(36)}"><input type="checkbox" id="reg-head-check" title="全选本页"></th>` : ''}
      ${cols.map((c) =>
      `<th data-col="${c}" style="${w(c)}" title="拖拽 ⠿ 调整顺序，拖拽右边缘调整列宽"><span class="th-grip" draggable="true">⠿</span><span class="th-label">${meta.labels[c]}</span></th>`).join('')}
      <th class="th-fixed" style="${colWidthStyle(ACTIONS_COL_WIDTH)}">操作</th></tr></thead>
    <tbody>${rows.map((it) => {
      const key = rowKey(it, meta);
      const checked = selected.has(key);
      return `<tr data-key="${esc(key)}" class="${checked ? 'row-selected' : ''}">
      ${showCheck ? `<td class="reg-td-check" style="${colWidthStyle(36)}"><input type="checkbox" class="reg-row-check" data-key="${esc(key)}" ${checked ? 'checked' : ''}></td>` : ''}
      ${cols.map((c) => `<td data-col="${c}" style="${w(c)}">${cell(it, c, meta)}</td>`).join('')}
      <td class="row-actions" style="${colWidthStyle(ACTIONS_COL_WIDTH)}">
        ${canModifyRow(it) ? `<button data-act="edit" data-key="${esc(key)}">编辑</button>` : ''}
        ${actionBtns(it, meta)}
        <button data-act="audit" data-key="${esc(key)}">审计</button>
        ${canModifyRow(it) ? `<button data-act="del" data-key="${esc(key)}" class="danger">删除</button>` : ''}
      </td></tr>`;
    }).join('') || `<tr><td colspan="${cols.length + (showCheck ? 2 : 1)}" class="muted empty-cell">没有匹配的条目</td></tr>`}
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

  $('#reg-table').querySelectorAll('.reg-row-check').forEach((cb) => {
    cb.onchange = () => {
      const key = cb.dataset.key;
      if (cb.checked) selected.add(key);
      else selected.delete(key);
      updateBulkBar();
      cb.closest('tr')?.classList.toggle('row-selected', cb.checked);
    };
  });

  const headCheck = $('#reg-head-check');
  headCheck?.addEventListener('change', () => {
    rows.forEach((it) => {
      const key = rowKey(it, meta);
      if (headCheck.checked) selected.add(key);
      else selected.delete(key);
    });
    renderTable();
  });

  const table = $('#reg-table').querySelector('table');
  bindColDrag(table, meta);
  bindColResize(table, cols, widths);
  updateBulkBar();
}

export async function renderRegistry(force = false) {
  await fetchRegistry(force);
  renderTable();
}

export function invalidateRegistryCache() {
  registryLoaded = false;
}

function actionBtns(it, meta) {
  const s = it.status || 'live';
  let html = '';
  if (isSpaceAdmin()) {
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

async function runBatch(action) {
  const keys = [...selected];
  if (!keys.length) return;
  const label = BULK_LABELS[action] || action;
  if (!confirm(`确认对 ${keys.length} 条执行「${label}」？`)) return;
  const r = await api('/registry/batch', {
    method: 'POST',
    body: JSON.stringify({ kind: curKind, action, keys }),
  });
  if (r.failed > 0) {
    const detail = (r.errors || []).slice(0, 5).map((e) => `${e.key}: ${e.error}`).join('\n');
    toast(`${label}：成功 ${r.ok}，失败 ${r.failed}${detail ? '\n' + detail : ''}`, r.ok ? 'warn' : 'error');
  } else {
    toast(`${label}完成：${r.ok} 条`);
  }
  clearSelection();
  renderRegistry(true);
  if (action === 'submit' || action === 'approve') {
    document.dispatchEvent(new CustomEvent('giso:pending-changed'));
  }
}

async function doApprove(key) {
  if (!confirm(`批准 ${key} 上线？`)) return;
  const r = await api(`/registry/${curKind}/approve?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已批准 ${key}`); renderRegistry(true); document.dispatchEvent(new CustomEvent('giso:pending-changed')); }
}

async function doReject(key) {
  if (!confirm(`驳回 ${key}？`)) return;
  const r = await api(`/registry/${curKind}/reject?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已驳回 ${key}`); renderRegistry(true); }
}

async function doPublish(key) {
  if (!confirm(`发布 ${key} 为 live？发布后将参与线上校验。`)) return;
  const r = await api(`/registry/${curKind}/publish?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已发布 ${key}`); renderRegistry(true); }
}

async function doDeprecate(key) {
  if (!confirm(`废弃 ${key}？`)) return;
  const r = await api(`/registry/${curKind}/deprecate?key=${encodeURIComponent(key)}`, { method: 'POST' });
  if (r.error) toast(r.error, 'error');
  else { toast(`已废弃 ${key}`); renderRegistry(true); }
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
  else { toast(`已删除 ${key}`); renderRegistry(true); }
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
    const inherited = key && curKind === 'elements' && !val && pageShotByElement.get(item[meta.id]);
    const hint = inherited
      ? `<span class="hint-inline">未填时列表展示所属页面截图（${esc(inherited)}）</span>`
      : '';
    return field(meta.labels[c], `
      <div class="shot-upload-row">
        <input name="screenshot" value="${esc(val)}" placeholder="截图 URL，或点击下方本地上传">
        <label class="btn ghost shot-pick">本地上传<input type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden class="shot-file"></label>
      </div>
      <div class="shot-preview" id="shot-preview">${val ? `<img src="${esc(val)}" alt="">` : '<span class="muted">填写 URL 或上传后预览</span>'}</div>${hint}`);
  }
  if (TEXTAREA_COLS.includes(c) && (curKind === 'pages' || curKind === 'elements')) {
    return field(meta.labels[c], `<textarea name="${c}" rows="3" placeholder="页面用途、入口、关键交互说明…">${esc(val)}</textarea>`);
  }
  if (c === 'domain' && (curKind === 'pages' || curKind === 'elements' || curKind === 'events')) {
    return field(meta.labels[c], renderDomainPicker(val, registry));
  }
  if (POOL_FIELD[c]) {
    return field(meta.labels[c], renderPoolPicker(c, item[c], registry, meta.labels[c]));
  }
  if (c === 'owner') {
    return field(meta.labels[c], renderOwnerPicker(val, registry));
  }
  const ph = LIST_COLS.includes(c) ? '逗号分隔，如: vid, pos' : '';
  const ro = c === meta.id && key ? 'readonly' : '';
  return field(meta.labels[c], `<input name="${c}" value="${esc(val)}" placeholder="${ph}" ${ro}>`);
}

function scheduleRefHints(meta) {
  clearTimeout(refHintsTimer);
  refHintsTimer = setTimeout(() => loadRefHints(meta), 280);
}

async function loadRefHints(meta) {
  try {
    const item = readEditorFormState(meta, LIST_COLS);
    const hints = await api('/registry/ref-hints', {
      method: 'POST',
      body: JSON.stringify({ kind: curKind, item }),
    });
    if (item[meta.id]) hints._pgid = item[meta.id];
    updateRefHintsPanel(hints, curKind, registry);
  } catch {
    document.getElementById('editor-ref-hints')?.setAttribute('hidden', '');
  }
}

function persistEditorDraft(kind, entryKey) {
  const meta = KIND_META[kind];
  const draft = readEditorFormState(meta, LIST_COLS);
  const key = entryKey || draft[meta.id] || null;
  saveEditorDraft(getSpace(), kind, key, draft);
  return key;
}

/** 跳转去其他池补登记前，先把当前条目以 draft 写入注册表（去掉未登记引用） */
async function saveRegistryDraft(out, kind, sessionDraft) {
  const meta = KIND_META[kind];
  const key = out[meta.id];
  if (!key) {
    toast('请先填写主键 ID', 'error');
    return false;
  }
  const payload = stripInvalidPoolRefs({ ...out }, registry);
  dedupeListFields(payload);
  if (!payload.status) payload.status = isEditor() ? 'pending' : 'draft';
  const r = await api('/registry/' + kind, { method: 'POST', body: JSON.stringify(payload) });
  if (r.error) {
    toast(r.error, 'error');
    return false;
  }
  clearEditorDraft(getSpace(), kind, null);
  saveEditorDraft(getSpace(), kind, key, sessionDraft || out);
  await renderRegistry(true);
  if (isEditor()) document.dispatchEvent(new CustomEvent('giso:pending-changed'));
  return true;
}

async function saveEntryDraftBeforePoolJump() {
  const meta = KIND_META[curKind];
  const draft = readEditorFormState(meta, LIST_COLS);
  return saveRegistryDraft(draft, curKind, draft);
}

function openEditor(key, draftOverride) {
  const meta = KIND_META[curKind];
  const storedDraft = draftOverride || peekEditorDraft(getSpace(), curKind, key);
  const serverItem = key ? registry[curKind].find((it) => it[meta.id] === key) : {};
  const item = storedDraft ? { ...serverItem, ...storedDraft } : serverItem;
  const editingKey = key || storedDraft?.[meta.id] || null;
  $('#editor-title').textContent = `${editingKey ? '编辑' : '新增'} · ${meta.name}`;
  if (storedDraft && !draftOverride) {
    toast('已恢复未保存的草稿', 'warn');
  }
  $('#editor-fields').innerHTML = meta.cols.map((c) => editorField(c, meta, item, editingKey)).join('');
  bindEditorPickers($('#editor-fields'), registry);
  scheduleRefHints(meta);
  const shotInput = $('#editor-fields').querySelector('input[name="screenshot"]');
  if (shotInput) {
    const preview = () => {
      const url = shotInput.value.trim();
      $('#shot-preview').innerHTML = url
        ? `<img src="${esc(url)}" alt="" onerror="this.outerHTML='<span class=muted>无法加载预览</span>'">`
        : '<span class="muted">填写 URL 或上传后预览</span>';
    };
    shotInput.addEventListener('input', preview);
    const fileInput = $('#editor-fields').querySelector('.shot-file');
    fileInput?.addEventListener('change', async () => {
      const file = fileInput.files?.[0];
      fileInput.value = '';
      if (!file) return;
      try {
        toast('上传中…');
        const url = await uploadScreenshot(file);
        shotInput.value = url;
        preview();
        toast('上传成功');
      } catch (e) {
        toast(e.message || '上传失败', 'error');
      }
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
    dedupeListFields(out);
    const { errors, warn } = validatePoolRefs(out, registry);
    if (errors.length) {
      if (out[meta.id]) {
        await saveRegistryDraft(out, curKind, out);
      } else {
        persistEditorDraft(curKind, editingKey);
      }
      toast(errors.join('；') + '。条目已保存为草稿，请切换到对应池添加后重新打开继续编辑。', 'error');
      e.preventDefault();
      return;
    }
    if (warn.length) toast(warn.join('；'), 'warn');
    const r = await api('/registry/' + curKind, { method: 'POST', body: JSON.stringify(out) });
    if (r.error) { toast(r.error, 'error'); e.preventDefault(); }
    else {
      clearEditorDraft(getSpace(), curKind, out[meta.id] || editingKey);
      const hintMsg = summarizeHintsToast(r.hints);
      toast(hintMsg
        ? `${isEditor() ? '已提交待审批' : '已保存'}。${hintMsg}`
        : (isEditor() ? '已提交待审批' : '已保存'));
      if (r.hints) updateRefHintsPanel(r.hints, curKind, registry);
      renderRegistry(true);
      if (isEditor()) document.dispatchEvent(new CustomEvent('giso:pending-changed'));
    }
  };
}

let bundlePayload = null;

async function exportRegistryBundle() {
  const r = await fetch('/admin/api/registry/export?format=json', {
    credentials: 'same-origin',
    headers: { 'X-GISO-Space': getSpace() },
  });
  if (r.status === 401) {
    window.location.href = '/admin/login.html';
    return;
  }
  if (!r.ok) {
    let msg = '导出失败';
    try { msg = (await r.json()).error || msg; } catch { /* ignore */ }
    toast(msg, 'error');
    return;
  }
  const blob = await r.blob();
  const cd = r.headers.get('Content-Disposition') || '';
  const m = cd.match(/filename="([^"]+)"/);
  const filename = m?.[1] || `giso-registry-${getSpace()}-live.json`;
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
  toast('已导出 live 条目及引用依赖（JSON 空间包）');
}

function openBundleImportDialog() {
  bundlePayload = null;
  $('#reg-bundle-file').value = '';
  $('#reg-bundle-preview').textContent = '';
  $('#reg-bundle-import-run').disabled = true;
  $('#reg-bundle-import-dialog').showModal();
}

async function readBundleFile() {
  const file = $('#reg-bundle-file').files?.[0];
  if (!file) return null;
  const text = await file.text();
  try {
    return JSON.parse(text);
  } catch {
    toast('JSON 解析失败', 'error');
    return null;
  }
}

function formatBundlePreview(r, bundle) {
  const counts = bundle?.counts || {};
  const dep = bundle?.dependency_total;
  const live = bundle?.live_total;
  const countLine = dep != null && live != null
    ? `包内：live ${live} + 依赖 ${dep} · params ${counts.params ?? '?'} · pages ${counts.pages ?? '?'} · elements ${counts.elements ?? '?'} · events ${counts.events ?? '?'}`
    : `包内：params ${counts.params ?? '?'} · pages ${counts.pages ?? '?'} · elements ${counts.elements ?? '?'} · events ${counts.events ?? '?'}`;
  const lines = [
    r.warning ? `⚠ ${r.warning}` : null,
    `校验：成功 ${r.ok}，失败 ${r.failed}（新建 ${r.created}，更新 ${r.updated}）`,
    countLine,
  ].filter(Boolean);
  if (r.failed > 0) {
    lines.push('');
    lines.push('失败条目：');
    (r.errors || []).slice(0, 8).forEach((e) => {
      lines.push(`· ${e.kind || ''}/${e.key}: ${e.error}`);
    });
    if ((r.errors || []).length > 8) lines.push(`… 另有 ${r.errors.length - 8} 条`);
  }
  return lines.join('\n');
}

async function runBundleImport(dryRun) {
  const bundle = bundlePayload || await readBundleFile();
  if (!bundle) { toast('请选择 JSON 空间包文件', 'error'); return; }
  bundlePayload = bundle;
  const r = await api('/registry/import-bundle', {
    method: 'POST',
    body: JSON.stringify({ dry_run: dryRun, bundle }),
  });
  $('#reg-bundle-preview').textContent = formatBundlePreview(r, bundle);
  if (r.failed > 0) {
    toast(dryRun ? `预览：${r.ok} 条通过，${r.failed} 条失败` : `导入：成功 ${r.ok}，失败 ${r.failed}`,
      r.ok ? 'warn' : 'error');
    $('#reg-bundle-import-run').disabled = true;
    return;
  }
  if (dryRun) {
    toast(`预览通过：将新建 ${r.created}、更新 ${r.updated} 条`);
    $('#reg-bundle-import-run').disabled = false;
    return;
  }
  toast(`导入完成：${r.ok} 条（新建 ${r.created}，更新 ${r.updated}）`);
  $('#reg-bundle-import-dialog').close();
  renderRegistry(true);
  document.dispatchEvent(new CustomEvent('giso:pending-changed'));
}

async function downloadTemplate() {
  const r = await fetch(`/admin/api/registry/import-template?kind=${encodeURIComponent(curKind)}`, {
    credentials: 'same-origin',
  });
  if (!r.ok) { toast('下载模板失败', 'error'); return; }
  const blob = await r.blob();
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${curKind}_import_template.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
}

function countCsvRows(text) {
  return text.split('\n').filter((l) => {
    const t = l.trim();
    return t && !t.startsWith('#') && !t.startsWith('key,') && !t.startsWith('pgid,')
      && !t.startsWith('eid,') && !t.startsWith('code,');
  }).length;
}

function openImportDialog() {
  $('#reg-import-kind-label').textContent = KIND_META[curKind].name;
  $('#reg-import-file').value = '';
  $('#reg-import-text').value = '';
  $('#reg-import-preview').textContent = '';
  $('#reg-import-dialog').showModal();
}

async function runImport() {
  let csv = $('#reg-import-text').value.trim();
  const file = $('#reg-import-file').files?.[0];
  if (file) csv = await file.text();
  if (!csv) { toast('请上传或粘贴 CSV', 'error'); return; }
  const n = countCsvRows(csv);
  if (!n) { toast('未解析到数据行', 'error'); return; }
  if (!confirm(`将导入约 ${n} 条到「${KIND_META[curKind].name}」，继续？`)) return;
  const r = await api('/registry/import', {
    method: 'POST',
    body: JSON.stringify({ kind: curKind, csv }),
  });
  if (r.failed > 0) {
    const detail = (r.errors || []).slice(0, 5).map((e) => `${e.key}: ${e.error}`).join('\n');
    toast(`导入：成功 ${r.ok}，失败 ${r.failed}\n${detail}`, r.ok ? 'warn' : 'error');
  } else {
    toast(`导入完成：${r.ok} 条`);
    $('#reg-import-dialog').close();
  }
  renderRegistry(true);
  if (isEditor()) document.dispatchEvent(new CustomEvent('giso:pending-changed'));
}

export function initRegistry() {
  $('#editor-fields')?.addEventListener('pool-need-register', async (e) => {
    const poolLabel = e.detail?.poolLabel || '目标池';
    const meta = KIND_META[curKind];
    const draft = readEditorFormState(meta, LIST_COLS);
    const key = draft[meta.id];
    if (!key) {
      toast('请先填写主键 ID 再添加引用', 'error');
      return;
    }
    if (!(await saveEntryDraftBeforePoolJump())) return;
    toast(`「${key}」已保存为草稿；请切换到${poolLabel}添加缺失项，完成后重新打开继续编辑`);
    $('#editor').close();
  });
  $('#editor-fields')?.addEventListener('pool-changed', () => {
    scheduleRefHints(KIND_META[curKind]);
  });
  $('#editor-fields')?.addEventListener('input', (e) => {
    if (e.target.matches('[name="domain"], .owner-input, .domain-input')) {
      scheduleRefHints(KIND_META[curKind]);
    }
  });
  $$('.seg-btn').forEach((btn) => {
    btn.onclick = () => {
      curKind = btn.dataset.kind;
      clearSelection();
      renderTable();
    };
  });
  $('#reg-search').addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { clearSelection(); renderTable(); }, 120);
  });
  $('#btn-add').onclick = () => openEditor(null);
  $('#btn-reg-export')?.addEventListener('click', exportRegistryBundle);
  $('#btn-reg-bundle-import')?.addEventListener('click', openBundleImportDialog);
  $('#btn-reg-template')?.addEventListener('click', downloadTemplate);
  $('#btn-reg-import')?.addEventListener('click', openImportDialog);
  $('#reg-bundle-dry-run')?.addEventListener('click', () => runBundleImport(true));
  $('#reg-bundle-import-run')?.addEventListener('click', () => {
    if (!confirm('确认将包内 live 条目写入当前空间？此操作不可自动撤销。')) return;
    runBundleImport(false);
  });
  $('#reg-bundle-file')?.addEventListener('change', () => {
    bundlePayload = null;
    $('#reg-bundle-preview').textContent = '';
    $('#reg-bundle-import-run').disabled = true;
    const f = $('#reg-bundle-file').files?.[0];
    if (f) $('#reg-bundle-preview').textContent = `已选：${f.name}`;
  });
  $('#reg-import-dl-template')?.addEventListener('click', (e) => { e.preventDefault(); downloadTemplate(); });
  $('#reg-import-run')?.addEventListener('click', runImport);
  $('#reg-import-file')?.addEventListener('change', async () => {
    const f = $('#reg-import-file').files?.[0];
    if (f) $('#reg-import-preview').textContent = `已选文件：${f.name}（约 ${countCsvRows(await f.text())} 条）`;
  });
  $('#reg-import-text')?.addEventListener('input', () => {
    const n = countCsvRows($('#reg-import-text').value);
    $('#reg-import-preview').textContent = n ? `约 ${n} 条待导入` : '';
  });
  $('#reg-bulk-clear')?.addEventListener('click', () => { clearSelection(); renderTable(); });
  $('#reg-select-all')?.addEventListener('change', () => {
    const rows = filteredRows();
    const meta = KIND_META[curKind];
    if ($('#reg-select-all').checked) rows.forEach((it) => selected.add(rowKey(it, meta)));
    else selected.clear();
    renderTable();
  });
  $('#reg-bulk-submit')?.addEventListener('click', () => runBatch('submit'));
  $('#reg-bulk-approve')?.addEventListener('click', () => runBatch('approve'));
  $('#reg-bulk-reject')?.addEventListener('click', () => runBatch('reject'));
  $('#reg-bulk-publish')?.addEventListener('click', () => runBatch('publish'));
  $('#reg-bulk-deprecate')?.addEventListener('click', () => runBatch('deprecate'));
  $('#reg-bulk-delete')?.addEventListener('click', () => runBatch('delete'));
  $('#btn-visual-picker')?.addEventListener('click', () => {
    document.dispatchEvent(new CustomEvent('giso:navigate', { detail: { view: 'visual' } }));
  });
  $('#btn-reset-cols')?.addEventListener('click', () => {
    localStorage.removeItem(colKey());
    localStorage.removeItem(colWidthsKey());
    renderTable();
    toast('已恢复默认列顺序与列宽');
  });
}
