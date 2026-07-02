/* 空间管理 + 成员 + App Key 绑定 + 顶栏空间切换器 */
import { $, $$, esc, toast } from '../util.js';
import { api, getSpace, setSpace, disconnectSSE } from '../api.js';
import { getMe, isSystemAdmin, isSpaceAdmin } from '../session.js';
import { t } from '../i18n.js';

const SPACE_ROLE_LABEL = {
  space_admin: '空间管理员', editor: '编辑员', viewer: '只读',
};

function spaceAccentClass(key) {
  if (!key) return 'space-accent--default';
  if (key === 'longvideo' || key.startsWith('video') || key.startsWith('long')) return 'space-accent--video';
  if (key.startsWith('sport')) return 'space-accent--sports';
  return 'space-accent--default';
}

function spaceLabel(me, key) {
  const spaces = me?.spaces || [];
  const hit = spaces.find((s) => s.space_key === key);
  return hit?.display_name || key;
}

function closeSpacePicker() {
  const picker = $('#space-picker');
  const btn = $('#space-picker-btn');
  const panel = $('#space-picker-panel');
  picker?.classList.remove('open');
  btn?.setAttribute('aria-expanded', 'false');
  if (panel) panel.hidden = true;
}

function playSpaceTransition(displayName) {
  const el = $('#space-transition');
  const nameEl = $('#space-transition-name');
  if (!el || !nameEl) return Promise.resolve();
  nameEl.textContent = displayName;
  el.hidden = false;
  el.setAttribute('aria-hidden', 'false');
  requestAnimationFrame(() => el.classList.add('active'));
  return new Promise((resolve) => {
    setTimeout(() => {
      el.classList.remove('active');
      setTimeout(() => {
        el.hidden = true;
        el.setAttribute('aria-hidden', 'true');
        resolve();
      }, 280);
    }, 560);
  });
}

export function updateSpacePickerUI(me) {
  const btn = $('#space-picker-btn');
  const nameEl = $('#space-picker-name');
  if (!btn || !nameEl) return;
  const key = getSpace();
  nameEl.textContent = spaceLabel(me, key);
  btn.className = `space-picker-btn ${spaceAccentClass(key)}`;
}

function renderSpacePickerList(me, onChange) {
  const list = $('#space-picker-list');
  if (!list) return;
  const spaces = me.spaces || [];
  const cur = getSpace();
  list.innerHTML = spaces.map((s) => {
    const accent = spaceAccentClass(s.space_key);
    const active = s.space_key === cur ? ' active' : '';
    const label = esc(s.display_name || s.space_key);
    return `<button type="button" class="space-option ${accent}${active}" role="option"
      data-key="${esc(s.space_key)}" aria-selected="${s.space_key === cur}">
      <span class="space-option-glow"></span>
      <span class="space-option-ico"><svg><use href="#ico-space"/></svg></span>
      <span class="space-option-body">
        <strong>${label}</strong>
        <span class="muted mono">${esc(s.space_key)}</span>
      </span>
      <span class="space-option-check">✓</span>
    </button>`;
  }).join('');

  list.querySelectorAll('.space-option').forEach((opt) => {
    opt.onclick = async () => {
      const key = opt.dataset.key;
      if (!key || key === getSpace()) {
        closeSpacePicker();
        return;
      }
      const label = opt.querySelector('strong')?.textContent || key;
      closeSpacePicker();
      await playSpaceTransition(label);
      setSpace(key);
      disconnectSSE();
      const fresh = await api('/me');
      updateSpacePickerUI(fresh);
      renderSpacePickerList(fresh, onChange);
      onChange(fresh);
      toast(t('space.switched').replace('{name}', label));
    };
  });
}

export function refreshSpaceSwitcher(me, onChange) {
  updateSpacePickerUI(me);
  renderSpacePickerList(me, onChange);
}

export function initSpaceSwitcher(onChange) {
  const picker = $('#space-picker');
  const btn = $('#space-picker-btn');
  if (!picker || !btn) return;

  const me = getMe();
  refreshSpaceSwitcher(me, onChange);

  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    const open = !picker.classList.contains('open');
    $$('.top-menu.open, .user-menu.open').forEach((el) => el.classList.remove('open'));
    picker.classList.toggle('open', open);
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    const panel = $('#space-picker-panel');
    if (panel) panel.hidden = !open;
  });

  picker.addEventListener('click', (e) => e.stopPropagation());
  document.addEventListener('click', closeSpacePicker);
}

export async function renderSpaces() {
  const wrap = $('#spaces-panel');
  if (!wrap) return;
  const me = getMe();
  const spaces = me.spaces || [];
  if (!spaces.length) {
    wrap.innerHTML = '<p class="muted">暂无可用空间。</p>';
    return;
  }

  let membersHtml = '';
  if (isSpaceAdmin()) {
    const members = await api(`/spaces/${encodeURIComponent(getSpace())}/members`);
    const list = Array.isArray(members) ? members : [];
    membersHtml = `<h3>本空间成员</h3>
      <table><thead><tr><th>用户名</th><th>角色</th><th>操作</th></tr></thead>
      <tbody>${list.map((m) => `<tr>
        <td>${esc(m.username)}</td>
        <td>${SPACE_ROLE_LABEL[m.role] || esc(m.role)}</td>
        <td><button class="danger" data-rm="${esc(m.username)}">移除</button></td>
      </tr>`).join('') || '<tr><td colspan="3" class="muted">暂无成员</td></tr>'}
      </tbody></table>
      <form id="member-form" class="inline-form">
        <input name="username" placeholder="用户名" required>
        <select name="role">
          <option value="space_admin">空间管理员</option>
          <option value="editor" selected>编辑员</option>
          <option value="viewer">只读</option>
        </select>
        <button type="submit">添加成员</button>
      </form>`;
  }

  let appKeysHtml = '';
  if (isSpaceAdmin()) {
    const keys = await api(`/spaces/${encodeURIComponent(getSpace())}/app-keys`);
    const list = Array.isArray(keys) ? keys : [];
    appKeysHtml = `<h3>App Key 绑定</h3>
      <p class="muted">上报时 X-App-Key 解析到本空间；未绑定则按前缀规则（video-*→长视频，sports-*→体育）。</p>
      <ul>${list.map((k) => `<li><code>${esc(k.app_key)}</code></li>`).join('') || '<li class="muted">暂无</li>'}</ul>
      <form id="appkey-form" class="inline-form">
        <input name="app_key" placeholder="App Key" required>
        <button type="submit">绑定</button>
      </form>`;
  }

  let createHtml = '';
  if (isSystemAdmin()) {
    createHtml = `<h3>创建空间（平台管理员）</h3>
      <form id="space-form" class="inline-form">
        <input name="space_key" placeholder="space_key（snake_case）" required>
        <input name="display_name" placeholder="显示名称">
        <button type="submit">创建</button>
      </form>`;
  }

  wrap.innerHTML = `${createHtml}
    ${membersHtml}
    ${appKeysHtml}`;

  wrap.querySelector('#member-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const body = {
      username: fd.get('username').toString().trim(),
      role: fd.get('role').toString(),
    };
    const r = await api(`/spaces/${encodeURIComponent(getSpace())}/members`, {
      method: 'POST', body: JSON.stringify(body),
    });
    if (r.error) toast(r.error, 'error');
    else { toast('已添加成员'); renderSpaces(); }
  });

  wrap.querySelectorAll('button[data-rm]').forEach((btn) => {
    btn.onclick = async () => {
      const u = btn.dataset.rm;
      if (!confirm(`移除成员 ${u}？`)) return;
      const r = await api(
        `/spaces/${encodeURIComponent(getSpace())}/members?username=${encodeURIComponent(u)}`,
        { method: 'DELETE' });
      if (r.error) toast(r.error, 'error');
      else { toast('已移除'); renderSpaces(); }
    };
  });

  wrap.querySelector('#appkey-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const r = await api(`/spaces/${encodeURIComponent(getSpace())}/app-keys`, {
      method: 'POST',
      body: JSON.stringify({ app_key: fd.get('app_key').toString().trim() }),
    });
    if (r.error) toast(r.error, 'error');
    else { toast('已绑定'); e.target.reset(); renderSpaces(); }
  });

  wrap.querySelector('#space-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const r = await api('/spaces', {
      method: 'POST',
      body: JSON.stringify({
        space_key: fd.get('space_key').toString().trim(),
        display_name: fd.get('display_name').toString().trim(),
      }),
    });
    if (r.error) toast(r.error, 'error');
    else { toast('空间已创建'); e.target.reset(); location.reload(); }
  });
}
