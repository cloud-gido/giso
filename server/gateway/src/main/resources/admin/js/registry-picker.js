/* 注册表编辑：从参数池 / 元素池 / 业务域搜索选取，避免手填重复与漏登 */
import { $, esc } from './util.js';

const KNOWN_DOMAINS = ['common', 'video', 'bet', 'pm', 'news', 'data'];
const DRAFT_TTL_MS = 24 * 60 * 60 * 1000;

export const POOL_FIELD = {
  params: {
    poolKind: 'params', idKey: 'key', descKey: 'desc', typeKey: 'type',
    poolLabel: '参数池', multi: true,
  },
  elements: {
    poolKind: 'elements', idKey: 'eid', descKey: 'desc',
    poolLabel: '元素池', multi: true,
  },
  children: {
    poolKind: 'elements', idKey: 'eid', descKey: 'desc',
    poolLabel: '元素池', multi: true,
  },
};

export function collectDomains(registry) {
  const set = new Set(KNOWN_DOMAINS);
  for (const k of ['pages', 'elements', 'events']) {
    for (const it of registry[k] || []) {
      if (it.domain) set.add(String(it.domain));
    }
  }
  return [...set].sort();
}

function normSelected(v) {
  if (Array.isArray(v)) return [...new Set(v.map((x) => String(x).trim()).filter(Boolean))];
  if (!v) return [];
  return [...new Set(String(v).split(',').map((s) => s.trim()).filter(Boolean))];
}

function poolOptions(registry, cfg) {
  return (registry[cfg.poolKind] || []).map((it) => ({
    id: it[cfg.idKey],
    desc: it[cfg.descKey] || '',
    type: cfg.typeKey ? it[cfg.typeKey] : '',
    domain: it.domain || '',
  })).filter((o) => o.id);
}

export function renderPoolPicker(name, selected, registry, fieldLabel) {
  const cfg = POOL_FIELD[name];
  if (!cfg) return '';
  const sel = normSelected(selected);
  const hidden = esc(sel.join(','));
  const poolN = (registry[cfg.poolKind] || []).length;
  return `<div class="pool-picker" data-field="${esc(name)}" data-pool="${esc(cfg.poolKind)}">
    <input type="hidden" name="${esc(name)}" value="${hidden}">
    <div class="pool-chips" data-selected="${esc(sel.join(','))}"></div>
    <div class="pool-search-row">
      <input type="search" class="pool-search" placeholder="搜索${esc(fieldLabel || cfg.poolLabel)}…" autocomplete="off" spellcheck="false">
      <button type="button" class="ghost pool-goto" data-goto="${esc(cfg.poolKind)}">去${esc(cfg.poolLabel)}添加</button>
    </div>
    <div class="pool-dropdown" hidden role="listbox"></div>
    <p class="pool-meta muted" data-pool-meta></p>
  </div>`;
}

export function renderDomainPicker(value, registry) {
  const v = esc(value || '');
  const domains = collectDomains(registry);
  return `<div class="pool-picker pool-picker--single" data-field="domain" data-pool="domain">
    <input type="hidden" name="domain" value="${v}">
    <div class="pool-search-row">
      <input type="search" class="pool-search domain-input" value="${v}" placeholder="选择或搜索业务域" autocomplete="off" list="domain-datalist">
      <datalist id="domain-datalist">${domains.map((d) => `<option value="${esc(d)}">`).join('')}</datalist>
    </div>
    <div class="pool-dropdown domain-dropdown" hidden></div>
    <p class="pool-meta muted" data-pool-meta></p>
  </div>`;
}

function renderChips(picker, registry, cfg) {
  const chips = picker.querySelector('.pool-chips');
  const hidden = picker.querySelector(`input[name="${picker.dataset.field}"]`);
  const sel = normSelected(hidden?.value);
  const pool = new Set(poolOptions(registry, cfg).map((o) => o.id));
  chips.innerHTML = sel.length
    ? sel.map((id) => {
      const ok = pool.has(id);
      return `<span class="pool-chip ${ok ? '' : 'pool-chip--orphan'}" data-id="${esc(id)}">
        <span class="pool-chip-id">${esc(id)}</span>
        ${ok ? '' : '<span class="pool-chip-warn" title="未在池中登记">!</span>'}
        <button type="button" class="pool-chip-remove" aria-label="移除">×</button>
      </span>`;
    }).join('')
    : '<span class="muted pool-empty">未选择，请搜索添加</span>';
  chips.dataset.selected = sel.join(',');
  updatePoolMeta(picker, registry, cfg, sel);
}

function updatePoolMeta(picker, registry, cfg, sel) {
  const meta = picker.querySelector('[data-pool-meta]');
  if (!meta) return;
  const pool = poolOptions(registry, cfg);
  const poolIds = new Set(pool.map((o) => o.id));
  const orphan = sel.filter((id) => !poolIds.has(id));
  const parts = [`${cfg.poolLabel}共 ${pool.length} 项`, `已选 ${sel.length} 项`];
  if (orphan.length) parts.push(`${orphan.length} 项未登记`);
  else if (sel.length) parts.push('均在池内');
  meta.textContent = parts.join(' · ');
  meta.classList.toggle('pool-meta--warn', orphan.length > 0);
}

function updateDomainMeta(picker, registry) {
  const meta = picker.querySelector('[data-pool-meta]');
  const hidden = picker.querySelector('input[name="domain"]');
  const v = hidden?.value?.trim() || '';
  const domains = collectDomains(registry);
  if (!meta) return;
  if (!v) {
    meta.textContent = `常用域：${domains.slice(0, 6).join('、')}…`;
    meta.classList.remove('pool-meta--warn');
    return;
  }
  const known = domains.includes(v);
  meta.textContent = known ? `业务域「${v}」已在登记中使用` : `「${v}」为新业务域，保存后将纳入可选列表`;
  meta.classList.toggle('pool-meta--warn', !known);
}

function showDropdown(picker, items, onPick) {
  const dd = picker.querySelector('.pool-dropdown');
  if (!items.length) {
    dd.innerHTML = '<div class="pool-dd-empty muted">无匹配项</div>';
  } else {
    dd.innerHTML = items.slice(0, 40).map((o) => `
      <button type="button" class="pool-dd-item" data-id="${esc(o.id)}">
        <span class="pool-dd-id">${esc(o.id)}</span>
        ${o.type ? `<span class="pool-dd-type">${esc(o.type)}</span>` : ''}
        ${o.desc ? `<span class="pool-dd-desc">${esc(o.desc)}</span>` : ''}
      </button>`).join('');
    dd.querySelectorAll('.pool-dd-item').forEach((btn) => {
      btn.onclick = () => { onPick(btn.dataset.id); dd.hidden = true; };
    });
  }
  dd.hidden = false;
}

function bindMultiPicker(picker, registry, cfg) {
  const hidden = picker.querySelector(`input[name="${picker.dataset.field}"]`);
  const search = picker.querySelector('.pool-search');
  const dd = picker.querySelector('.pool-dropdown');

  const getSel = () => normSelected(hidden.value);
  const setSel = (arr) => {
      hidden.value = arr.join(',');
      renderChips(picker, registry, cfg);
      picker.dispatchEvent(new CustomEvent('pool-changed', { bubbles: true }));
    };

  renderChips(picker, registry, cfg);

  picker.querySelector('.pool-goto')?.addEventListener('click', () => {
    picker.dispatchEvent(new CustomEvent('pool-need-register', {
      bubbles: true,
      detail: { poolKind: cfg.poolKind, poolLabel: cfg.poolLabel },
    }));
  });

  search?.addEventListener('input', () => {
    const q = search.value.trim().toLowerCase();
    const sel = new Set(getSel());
    const items = poolOptions(registry, cfg).filter((o) => {
      if (sel.has(o.id)) return false;
      if (!q) return true;
      return o.id.toLowerCase().includes(q)
        || o.desc.toLowerCase().includes(q);
    });
    showDropdown(picker, items, (id) => {
      setSel([...getSel(), id]);
      search.value = '';
    });
  });

  search?.addEventListener('focus', () => search.dispatchEvent(new Event('input')));

  picker.querySelector('.pool-chips')?.addEventListener('click', (e) => {
    const btn = e.target.closest('.pool-chip-remove');
    if (!btn) return;
    const chip = btn.closest('.pool-chip');
    setSel(getSel().filter((id) => id !== chip?.dataset.id));
  });

  document.addEventListener('click', (e) => {
    if (!picker.contains(e.target)) dd.hidden = true;
  });
}

function bindDomainPicker(picker, registry) {
  const hidden = picker.querySelector('input[name="domain"]');
  const search = picker.querySelector('.pool-search');
  const dd = picker.querySelector('.pool-dropdown');

  const apply = (v) => {
    hidden.value = v;
    if (search) search.value = v;
    updateDomainMeta(picker, registry);
    dd.hidden = true;
  };

  updateDomainMeta(picker, registry);

  search?.addEventListener('input', () => {
    const q = search.value.trim().toLowerCase();
    const items = collectDomains(registry)
      .filter((d) => !q || d.toLowerCase().includes(q))
      .map((id) => ({ id, desc: '', type: '' }));
    showDropdown(picker, items, apply);
  });

  search?.addEventListener('change', () => apply(search.value.trim()));
  search?.addEventListener('blur', () => apply(search.value.trim()));
}

export function collectOwners(registry) {
  const set = new Set();
  for (const k of ['params', 'pages', 'elements', 'events']) {
    for (const it of registry[k] || []) {
      if (it.owner) set.add(String(it.owner).trim());
    }
  }
  return [...set].sort();
}

export function renderOwnerPicker(value, registry) {
  const v = esc(value || '');
  const owners = collectOwners(registry);
  return `<div class="pool-picker pool-picker--single" data-field="owner">
    <input type="hidden" name="owner" value="${v}">
    <div class="pool-search-row">
      <input type="search" class="pool-search owner-input" value="${v}" placeholder="选择或输入负责人/团队" autocomplete="off" list="owner-datalist">
      <datalist id="owner-datalist">${owners.map((o) => `<option value="${esc(o)}">`).join('')}</datalist>
    </div>
    <p class="pool-meta muted">从历史负责人中选择，保持团队命名一致</p>
  </div>`;
}

function bindOwnerPicker(picker) {
  const hidden = picker.querySelector('input[name="owner"]');
  const search = picker.querySelector('.pool-search');
  const apply = (v) => {
    hidden.value = v;
    if (search) search.value = v;
  };
  search?.addEventListener('change', () => apply(search.value.trim()));
  search?.addEventListener('blur', () => apply(search.value.trim()));
}

export function bindEditorPickers(container, registry) {
  container.querySelectorAll('.pool-picker').forEach((picker) => {
    const field = picker.dataset.field;
    if (field === 'domain') {
      bindDomainPicker(picker, registry);
      return;
    }
    if (field === 'owner') {
      bindOwnerPicker(picker);
      return;
    }
    const cfg = POOL_FIELD[field];
    if (cfg?.multi) bindMultiPicker(picker, registry, cfg);
  });
}

function addChipToPicker(field, id, registry) {
  const picker = document.querySelector(`#editor-fields .pool-picker[data-field="${field}"]`);
  if (!picker) return;
  const hidden = picker.querySelector(`input[name="${field}"]`);
  const cfg = POOL_FIELD[field];
  if (!hidden || !cfg) return;
  const sel = normSelected(hidden.value);
  if (!sel.includes(id)) sel.push(id);
  hidden.value = sel.join(',');
  renderChips(picker, registry, cfg);
  picker.dispatchEvent(new CustomEvent('pool-changed', { bubbles: true }));
}

export function updateRefHintsPanel(hints, kind, registry) {
  const panel = document.getElementById('editor-ref-hints');
  const list = document.getElementById('editor-ref-hints-list');
  if (!panel || !list || !hints) {
    panel?.setAttribute('hidden', '');
    return;
  }
  const items = [];
  if (hints.warn) items.push({ t: hints.warn, warn: true });

  if (hints.orphan_params_count > 0) {
    items.push({
      t: `参数池有 ${hints.orphan_params_count} 个参数未被任何页面/元素/事件引用`,
      sub: (hints.orphan_params || []).join(', '),
    });
  }
  if (hints.orphan_elements_count > 0) {
    items.push({
      t: `元素池有 ${hints.orphan_elements_count} 个元素未绑定到任何页面`,
      sub: (hints.orphan_elements || []).join(', '),
    });
  }

  if (kind === 'params' && hints.referenced_by?.length) {
    const refs = hints.referenced_by.map((r) => `${r.kind}/${r.id}`).join('、');
    items.push({ t: `已被引用：${refs}` });
  }

  if (kind === 'pages' && hints.element_links?.length) {
    for (const link of hints.element_links) {
      const pages = (link.pages || []).filter((p) => p !== hints._pgid);
      if (pages.length) {
        items.push({ t: `元素 ${link.eid} 同时绑定于：${pages.join('、')}` });
      }
    }
  }

  if (kind === 'elements' && hints.parent_pages?.length) {
    items.push({ t: `已绑定页面：${hints.parent_pages.join('、')}` });
  }

  if (hints.suggested_elements?.length) {
    items.push({
      t: '同域可绑定元素（点击添加）',
      picks: hints.suggested_elements.map((id) => ({ field: 'elements', id })),
    });
  }
  if (hints.suggested_pages?.length) {
    items.push({
      t: '同域可绑定到页面（供参考）',
      sub: hints.suggested_pages.join('、'),
    });
  }

  if (!items.length) {
    panel.hidden = true;
    return;
  }

  list.innerHTML = items.map((it, i) => `
    <li class="ref-hint-item ${it.warn ? 'ref-hint-warn' : ''}">
      <span>${esc(it.t)}</span>
      ${it.sub ? `<span class="ref-hint-sub muted">${esc(it.sub)}</span>` : ''}
      ${it.picks ? `<span class="ref-hint-picks">${it.picks.map((p) =>
        `<button type="button" class="link-btn ref-hint-pick" data-field="${esc(p.field)}" data-id="${esc(p.id)}">+ ${esc(p.id)}</button>`
      ).join('')}</span>` : ''}
    </li>`).join('');

  list.querySelectorAll('.ref-hint-pick').forEach((btn) => {
    btn.onclick = () => {
      addChipToPicker(btn.dataset.field, btn.dataset.id, registry);
      document.getElementById('editor-fields')?.dispatchEvent(new CustomEvent('pool-changed', { bubbles: true }));
    };
  });
  panel.hidden = false;
}

export function summarizeHintsToast(hints) {
  if (!hints) return '';
  const parts = [];
  if (hints.warn) parts.push(hints.warn);
  if (hints.orphan_params_count > 0) parts.push(`${hints.orphan_params_count} 个参数未被引用`);
  if (hints.orphan_elements_count > 0) parts.push(`${hints.orphan_elements_count} 个元素未绑定页面`);
  return parts.join('；');
}

export function validatePoolRefs(out, registry) {
  const errors = [];
  const warn = [];

  if (out.params?.length) {
    const keys = new Set((registry.params || []).map((p) => p.key));
    const bad = out.params.filter((k) => !keys.has(k));
    if (bad.length) errors.push(`参数未在参数池登记：${bad.join(', ')}`);
    const dup = out.params.length - new Set(out.params).size;
    if (dup > 0) warn.push('参数列表存在重复项（已自动去重保存）');
  }
  if (out.elements?.length) {
    const ids = new Set((registry.elements || []).map((e) => e.eid));
    const bad = out.elements.filter((k) => !ids.has(k));
    if (bad.length) errors.push(`元素未在元素池登记：${bad.join(', ')}`);
  }
  if (out.children?.length) {
    const ids = new Set((registry.elements || []).map((e) => e.eid));
    const bad = out.children.filter((k) => !ids.has(k));
    if (bad.length) errors.push(`子元素未在元素池登记：${bad.join(', ')}`);
  }
  if (out.domain) {
    const domains = collectDomains(registry);
    if (!domains.includes(out.domain)) {
      warn.push(`业务域「${out.domain}」为新增域，请确认拼写正确`);
    }
  }
  return { errors, warn };
}

export function dedupeListFields(out) {
  for (const k of ['params', 'elements', 'children']) {
    if (Array.isArray(out[k])) out[k] = [...new Set(out[k])];
  }
  return out;
}

/** 保存到服务端前去掉尚未登记的池引用，避免 draft 无法落库 */
export function stripInvalidPoolRefs(out, registry) {
  const cleaned = { ...out };
  if (Array.isArray(cleaned.params)) {
    const keys = new Set((registry.params || []).map((p) => p.key));
    cleaned.params = cleaned.params.filter((k) => keys.has(k));
  }
  if (Array.isArray(cleaned.elements)) {
    const ids = new Set((registry.elements || []).map((e) => e.eid));
    cleaned.elements = cleaned.elements.filter((k) => ids.has(k));
  }
  if (Array.isArray(cleaned.children)) {
    const ids = new Set((registry.elements || []).map((e) => e.eid));
    cleaned.children = cleaned.children.filter((k) => ids.has(k));
  }
  return cleaned;
}

function draftStorageKey(space, kind, entryKey) {
  return `giso_reg_draft:${space}:${kind}:${entryKey || '__new__'}`;
}

export function saveEditorDraft(space, kind, entryKey, draft) {
  if (!draft || !Object.keys(draft).length) return;
  sessionStorage.setItem(draftStorageKey(space, kind, entryKey), JSON.stringify({
    draft,
    ts: Date.now(),
  }));
}

export function peekEditorDraft(space, kind, entryKey) {
  try {
    const raw = sessionStorage.getItem(draftStorageKey(space, kind, entryKey));
    if (!raw) return null;
    const data = JSON.parse(raw);
    if (!data?.draft || (data.ts && Date.now() - data.ts > DRAFT_TTL_MS)) {
      sessionStorage.removeItem(draftStorageKey(space, kind, entryKey));
      return null;
    }
    return data.draft;
  } catch {
    return null;
  }
}

export function clearEditorDraft(space, kind, entryKey) {
  sessionStorage.removeItem(draftStorageKey(space, kind, entryKey));
}

export function readEditorFormState(meta, listCols) {
  const out = {};
  for (const c of meta.cols) {
    const el = document.querySelector(`#editor-fields [name="${c}"]`);
    if (!el) continue;
    const v = (el.value || '').toString().trim();
    if (listCols.includes(c)) {
      out[c] = v ? v.split(',').map((s) => s.trim()).filter(Boolean) : [];
    } else if (v) out[c] = v;
  }
  return out;
}

export function refreshEditorPickers(container, registry) {
  container.querySelectorAll('.pool-picker[data-field]').forEach((picker) => {
    const field = picker.dataset.field;
    if (field === 'domain') {
      updateDomainMeta(picker, registry);
      return;
    }
    const cfg = POOL_FIELD[field];
    if (cfg) renderChips(picker, registry, cfg);
  });
}
