/* 空间管理 + 成员 + App Key 绑定 + 顶栏空间切换器 */
import { $, $$, esc, toast } from '../util.js';
import { api, getSpace, setSpace, disconnectSSE } from '../api.js';
import { getMe, isSystemAdmin, isSpaceAdmin } from '../session.js';
import { t } from '../i18n.js';

const SPACE_ROLE_LABEL = {
  space_admin: '空间管理员', editor: '编辑员', viewer: '只读',
};
const GLOBAL_ROLE_LABEL = {
  system_admin: '平台管理员', admin: '平台管理员', user: '平台用户',
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

function memberSelectHtml(candidates) {
  if (!candidates.length) {
    return `<select name="username" disabled>
      <option value="">暂无可添加账号</option>
    </select>`;
  }
  const opts = candidates.map((u) => {
    const label = u.display_name
      ? `${u.username}（${u.display_name}）`
      : u.username;
    const global = GLOBAL_ROLE_LABEL[u.global_role] || u.global_role || '';
    const suffix = global ? ` · ${global}` : '';
    return `<option value="${esc(u.username)}">${esc(label)}${esc(suffix)}</option>`;
  }).join('');
  return `<select name="username" required aria-label="选择成员">
    <option value="" disabled selected>选择账号…</option>
    ${opts}
  </select>`;
}

function memberHintHtml(candidates) {
  if (!candidates.length) {
    return `<p class="member-form-hint warn">所有平台账号已在本空间，或尚无账号。请平台管理员在「账号管理」中先创建用户。</p>`;
  }
  return `<p class="member-form-hint">可选 ${candidates.length} 个平台账号（不含已在本空间的成员）。</p>`;
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
    const spaceKey = getSpace();
    const [members, candidates] = await Promise.all([
      api(`/spaces/${encodeURIComponent(spaceKey)}/members`),
      api(`/spaces/${encodeURIComponent(spaceKey)}/member-candidates`),
    ]);
    const list = Array.isArray(members) ? members : [];
    const pickList = Array.isArray(candidates) ? candidates : [];
    membersHtml = `<h3>本空间成员</h3>
      <table><thead><tr><th>用户名</th><th>角色</th><th>操作</th></tr></thead>
      <tbody>${list.map((m) => `<tr>
        <td>${esc(m.username)}${m.display_name ? ` <span class="muted">(${esc(m.display_name)})</span>` : ''}</td>
        <td>${SPACE_ROLE_LABEL[m.role] || esc(m.role)}</td>
        <td><button class="danger" data-rm="${esc(m.username)}">移除</button></td>
      </tr>`).join('') || '<tr><td colspan="3" class="muted">暂无成员</td></tr>'}
      </tbody></table>
      <form id="member-form" class="inline-form">
        <label class="muted" style="font-size:12px;font-weight:600">添加成员</label>
        ${memberSelectHtml(pickList)}
        <select name="role" aria-label="空间角色">
          <option value="space_admin">空间管理员</option>
          <option value="editor" selected>编辑员</option>
          <option value="viewer">只读</option>
        </select>
        <button type="submit" ${pickList.length ? '' : 'disabled'}>添加成员</button>
        <div id="member-form-feedback" hidden></div>
        ${memberHintHtml(pickList)}
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
      </form>
      <p class="member-form-hint">创建后可在下方「本空间成员」从平台账号列表中添加成员。</p>`;
  }

  wrap.innerHTML = `${createHtml}
    ${membersHtml}
    ${appKeysHtml}`;

  const showMemberFeedback = (text, kind) => {
    const el = $('#member-form-feedback');
    if (!el) return;
    el.hidden = false;
    el.className = `form-feedback ${kind}`;
    el.textContent = text;
  };

  wrap.querySelector('#member-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const username = fd.get('username')?.toString().trim();
    const role = fd.get('role')?.toString();
    if (!username) {
      showMemberFeedback('请先选择要添加的平台账号', 'error');
      toast('请选择成员账号', 'error');
      return;
    }
    const r = await api(`/spaces/${encodeURIComponent(getSpace())}/members`, {
      method: 'POST', body: JSON.stringify({ username, role }),
    });
    if (r.error) {
      showMemberFeedback(r.error, 'error');
      toast(r.error, 'error');
      return;
    }
    const msg = r.message || `已添加成员「${username}」`;
    showMemberFeedback(msg, 'ok');
    toast(msg);
    renderSpaces();
  });

  wrap.querySelectorAll('button[data-rm]').forEach((btn) => {
    btn.onclick = async () => {
      const u = btn.dataset.rm;
      if (!confirm(`移除成员 ${u}？`)) return;
      const r = await api(
        `/spaces/${encodeURIComponent(getSpace())}/members?username=${encodeURIComponent(u)}`,
        { method: 'DELETE' });
      if (r.error) toast(r.error, 'error');
      else { toast(`已移除成员「${u}」`); renderSpaces(); }
    };
  });

  wrap.querySelector('#appkey-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const appKey = fd.get('app_key').toString().trim();
    const r = await api(`/spaces/${encodeURIComponent(getSpace())}/app-keys`, {
      method: 'POST',
      body: JSON.stringify({ app_key: appKey }),
    });
    if (r.error) toast(r.error, 'error');
    else { toast(`已绑定 App Key「${appKey}」`); e.target.reset(); renderSpaces(); }
  });

  wrap.querySelector('#space-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const spaceKey = fd.get('space_key').toString().trim();
    const displayName = fd.get('display_name').toString().trim();
    const r = await api('/spaces', {
      method: 'POST',
      body: JSON.stringify({ space_key: spaceKey, display_name: displayName }),
    });
    if (r.error) toast(r.error, 'error');
    else {
      toast(`空间「${displayName || spaceKey}」已创建，可在成员列表中添加账号`);
      e.target.reset();
      location.reload();
    }
  });
}
