/* 账号管理（仅 platform admin） */
import { $, esc, toast } from '../util.js';
import { api } from '../api.js';
import { isAdmin } from '../session.js';

const ROLE_LABEL = { system_admin: '平台管理员', user: '平台用户', admin: '管理员' };
const SPACE_ROLE_LABEL = { space_admin: '空间管理员', editor: '编辑员', viewer: '只读' };

function formatSpaces(spaces) {
  if (!Array.isArray(spaces) || spaces.length === 0) {
    return '<span class="tag tag-warn">未授权空间</span>';
  }
  return spaces.map((s) => {
    const label = SPACE_ROLE_LABEL[s.role] || s.role;
    return `<span class="tag">${esc(s.space_key)} · ${esc(label)}</span>`;
  }).join(' ');
}

function toggleSpaceFields() {
  const platformRole = $('#user-platform-role')?.value || 'user';
  const show = platformRole === 'user';
  $('#user-space-role')?.toggleAttribute('hidden', !show);
  $('#user-space-key')?.toggleAttribute('hidden', !show);
  $('#user-form-hint')?.toggleAttribute('hidden', !show);
}

export async function renderUsers() {
  const wrap = $('#users-table');
  if (!isAdmin()) {
    wrap.innerHTML = '<p class="muted">仅平台管理员可管理账号。</p>';
    return;
  }
  const users = await api('/users');
  const list = Array.isArray(users) ? users : [];
  wrap.innerHTML = `<table>
    <thead><tr><th>用户名</th><th>平台角色</th><th>空间授权</th><th>显示名</th><th>操作</th></tr></thead>
    <tbody>${list.map((u) => `<tr>
      <td><span class="key">${esc(u.username)}</span></td>
      <td>${ROLE_LABEL[u.role] || esc(u.role)}</td>
      <td class="space-tags">${formatSpaces(u.spaces)}</td>
      <td>${esc(u.display_name || '—')}</td>
      <td class="actions-cell">
        ${u.locked ? '<span class="tag tag-warn">已锁定</span> ' : ''}
        ${u.role === 'user' ? `<button type="button" class="ghost" data-role="${esc(u.username)}">改角色</button>` : ''}
        <button type="button" class="ghost" data-reset="${esc(u.username)}">重置密码</button>
        ${u.locked ? `<button type="button" class="ghost" data-unlock="${esc(u.username)}">解锁</button>` : ''}
        <button class="danger" data-user="${esc(u.username)}">禁用</button>
      </td>
    </tr>`).join('') || '<tr><td colspan="5" class="muted">暂无账号</td></tr>'}
    </tbody></table>`;
  wrap.querySelectorAll('button[data-role]').forEach((btn) => {
    btn.onclick = async () => {
      const name = btn.dataset.role;
      const next = prompt(
        `修改 ${name} 的平台角色：\n输入 system_admin 或 user`,
        'user',
      );
      if (next == null) return;
      const role = next.trim();
      if (!['system_admin', 'user'].includes(role)) return toast('仅支持 system_admin / user', 'error');
      const r = await api('/users/' + encodeURIComponent(name), {
        method: 'PUT',
        body: JSON.stringify({ role }),
      });
      if (r.error) toast(r.error, 'error');
      else { toast(`已更新 ${name} 的平台角色`); renderUsers(); }
    };
  });
  wrap.querySelectorAll('button[data-reset]').forEach((btn) => {
    btn.onclick = async () => {
      const name = btn.dataset.reset;
      const next = prompt(`为账号 ${name} 设置新密码（至少 6 位）`);
      if (next == null) return;
      if (next.length < 6) return toast('密码至少 6 位', 'error');
      const r = await api('/users/' + encodeURIComponent(name), {
        method: 'PUT',
        body: JSON.stringify({ password: next }),
      });
      if (r.error) toast(r.error, 'error');
      else toast(`已重置 ${name} 的密码`);
    };
  });
  wrap.querySelectorAll('button[data-unlock]').forEach((btn) => {
    btn.onclick = async () => {
      const name = btn.dataset.unlock;
      if (!confirm(`解锁账号 ${name}？`)) return;
      const r = await api('/users/' + encodeURIComponent(name) + '/unlock', { method: 'POST' });
      if (r.error) toast(r.error, 'error');
      else { toast(r.message || `已解锁 ${name}`); renderUsers(); }
    };
  });
  wrap.querySelectorAll('button[data-user]').forEach((btn) => {
    btn.onclick = async () => {
      const name = btn.dataset.user;
      if (!confirm(`禁用账号 ${name}？`)) return;
      const r = await api('/users/' + encodeURIComponent(name), { method: 'DELETE' });
      if (r.error) toast(r.error, 'error');
      else { toast(`已禁用 ${name}`); renderUsers(); }
    };
  });
}

export function initUsers() {
  toggleSpaceFields();
  $('#user-platform-role')?.addEventListener('change', toggleSpaceFields);
  $('#user-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!isAdmin()) return;
    const fd = new FormData(e.target);
    const platformRole = (fd.get('role') || 'user').toString();
    const spaceKey = (fd.get('space_key') || 'default').toString();
    const body = {
      username: (fd.get('username') || '').toString().trim(),
      password: (fd.get('password') || '').toString(),
      role: platformRole,
      display_name: (fd.get('display_name') || '').toString().trim(),
    };
    if (platformRole === 'user') {
      body.space_role = (fd.get('space_role') || 'viewer').toString();
      body.space_key = spaceKey;
      body.all_spaces = spaceKey === '__all__';
    }
    const r = await api('/users', { method: 'POST', body: JSON.stringify(body) });
    if (r.error) toast(r.error, 'error');
    else {
      toast(`已创建 ${body.username}，空间授权已同步`);
      e.target.reset();
      toggleSpaceFields();
      renderUsers();
    }
  });
}
