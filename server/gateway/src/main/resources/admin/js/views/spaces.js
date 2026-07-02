/* 空间管理 + 成员 + App Key 绑定 */
import { $, esc, toast } from '../util.js';
import { api, getSpace, setSpace, disconnectSSE } from '../api.js';
import { getMe, isSystemAdmin, isSpaceAdmin } from '../session.js';

const SPACE_ROLE_LABEL = {
  space_admin: '空间管理员', editor: '编辑员', viewer: '只读',
};

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

export function initSpaceSwitcher(onChange) {
  const sel = $('#space-switcher');
  if (!sel) return;
  const me = getMe();
  const spaces = me.spaces || [];
  sel.innerHTML = spaces.map((s) =>
    `<option value="${esc(s.space_key)}" ${s.space_key === getSpace() ? 'selected' : ''}>
      ${esc(s.display_name || s.space_key)}
    </option>`).join('');
  sel.onchange = async () => {
    setSpace(sel.value);
    disconnectSSE();
    const fresh = await api('/me');
    onChange(fresh);
  };
}
