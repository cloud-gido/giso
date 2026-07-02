/* 账号管理（仅 admin） */
import { $, esc, toast } from '../util.js';
import { api } from '../api.js';
import { isAdmin } from '../session.js';

const ROLE_LABEL = { system_admin: '平台管理员', user: '平台用户', admin: '管理员' };

export async function renderUsers() {
  const wrap = $('#users-table');
  if (!isAdmin()) {
    wrap.innerHTML = '<p class="muted">仅管理员可管理账号。</p>';
    return;
  }
  const users = await api('/users');
  const list = Array.isArray(users) ? users : [];
  wrap.innerHTML = `<table>
    <thead><tr><th>用户名</th><th>角色</th><th>显示名</th><th>来源</th><th>操作</th></tr></thead>
    <tbody>${list.map((u) => `<tr>
      <td><span class="key">${esc(u.username)}</span></td>
      <td>${ROLE_LABEL[u.role] || esc(u.role)}</td>
      <td>${esc(u.display_name || '—')}</td>
      <td class="muted">${esc(u.source || '—')}</td>
      <td><button class="danger" data-user="${esc(u.username)}">禁用</button></td>
    </tr>`).join('') || '<tr><td colspan="5" class="muted">暂无账号</td></tr>'}
    </tbody></table>`;
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
  $('#user-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!isAdmin()) return;
    const fd = new FormData(e.target);
    const body = {
      username: (fd.get('username') || '').toString().trim(),
      password: (fd.get('password') || '').toString(),
      role: (fd.get('role') || 'editor').toString(),
      display_name: (fd.get('display_name') || '').toString().trim(),
    };
    const r = await api('/users', { method: 'POST', body: JSON.stringify(body) });
    if (r.error) toast(r.error, 'error');
    else {
      toast(`已创建 ${body.username}`);
      e.target.reset();
      renderUsers();
    }
  });
}
