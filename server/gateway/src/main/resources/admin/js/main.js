/* GISO 玑源 · 管理控制台入口 */
import { $, $$ } from './util.js';
import { api, logout, getSpace, setSpace } from './api.js';
import { setMe } from './session.js';
import { initDebug, renderDebug } from './views/debug.js';
import { initAssert } from './views/assert.js';
import { initRegistry, renderRegistry, invalidateRegistryCache } from './views/registry.js';
import { initStats, loadStats } from './views/stats.js';
import { initApproval, renderApproval } from './views/approval.js';
import { initUsers, renderUsers } from './views/users.js';
import { initSpaceSwitcher, renderSpaces, refreshSpaceSwitcher } from './views/spaces.js';
import { initVisualPicker, renderVisualPicker } from './views/visual-picker.js';
import { initCopilot, renderCopilot } from './views/copilot.js';
import { initSettings, renderSettings } from './views/settings.js';
import { t, setLocale, getLocale, applyI18n } from './i18n.js';

const ROLE_LABEL = {
  system_admin: '平台管理员', admin: '管理员', user: '平台用户',
  space_admin: '空间管理员', editor: '编辑员', viewer: '只读',
};

const VIEWS = {
  debug: { titleKey: 'view.debug.title', descKey: 'view.debug.desc', onShow: renderDebug },
  assert: { titleKey: 'view.assert.title', descKey: 'view.assert.desc' },
  approval: { titleKey: 'view.approval.title', descKey: 'view.approval.desc', onShow: renderApproval },
  registry: { titleKey: 'view.registry.title', descKey: 'view.registry.desc', onShow: renderRegistry },
  visual: { titleKey: 'view.visual.title', descKey: 'view.visual.desc', onShow: renderVisualPicker },
  copilot: { titleKey: 'view.copilot.title', descKey: 'view.copilot.desc', onShow: renderCopilot },
  settings: { titleKey: 'view.settings.title', descKey: 'view.settings.desc', onShow: renderSettings },
  stats: { titleKey: 'view.stats.title', descKey: 'view.stats.desc', onShow: loadStats },
  spaces: { titleKey: 'view.spaces.title', descKey: 'view.spaces.desc', onShow: renderSpaces },
  users: { titleKey: 'view.users.title', descKey: 'view.users.desc', onShow: renderUsers },
};

export function show(view) {
  if (!VIEWS[view]) return;
  $$('[data-view]').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
  $$('.view').forEach((p) => p.classList.toggle('active', p.id === 'view-' + view));
  $('#page-title').textContent = t(VIEWS[view].titleKey);
  $('#page-desc').textContent = t(VIEWS[view].descKey);
  $$('.top-menu.open').forEach((m) => m.classList.remove('open'));
  VIEWS[view].onShow?.();
}

function initNavigation() {
  $$('[data-view]').forEach((btn) => {
    btn.onclick = (e) => {
      e.preventDefault();
      show(btn.dataset.view);
    };
  });
}

function initTopMenus() {
  $$('.top-menu').forEach((menu) => {
    menu.querySelector('.top-menu-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      const wasOpen = menu.classList.contains('open');
      $$('.top-menu.open, .space-picker.open, .user-menu.open').forEach((m) => m.classList.remove('open'));
      if (!wasOpen) menu.classList.add('open');
    });
  });
  document.addEventListener('click', () => $$('.top-menu.open').forEach((m) => m.classList.remove('open')));
}

function updatePendingBadge(count) {
  const badge = $('#nav-pending-badge');
  if (!badge) return;
  if (count > 0) {
    badge.hidden = false;
    badge.textContent = String(count);
  } else {
    badge.hidden = true;
  }
}

function initUserMenu() {
  const menu = $('#user-menu');
  const btn = $('#user-menu-btn');
  btn?.addEventListener('click', (e) => {
    e.stopPropagation();
    const open = !menu.classList.contains('open');
    $$('.top-menu.open, .space-picker.open').forEach((el) => el.classList.remove('open'));
    menu.classList.toggle('open', open);
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  });
  menu?.addEventListener('click', (e) => e.stopPropagation());
  document.addEventListener('click', () => {
    menu?.classList.remove('open');
    btn?.setAttribute('aria-expanded', 'false');
  });
  $('#btn-logout')?.addEventListener('click', () => logout());
}

function applyRole(me) {
  const userMenu = $('#user-menu');
  const menuName = $('#user-menu-name');
  const menuRole = $('#user-menu-role');
  const avatar = $('#user-avatar');
  const logoutBtn = $('#btn-logout');
  const meta = $('#user-menu-meta');
  if (!me?.username && me?.auth_enabled) return;

  if (userMenu) userMenu.hidden = false;

  if (me?.auth_enabled) {
    const global = ROLE_LABEL[me.role] || me.role;
    const space = ROLE_LABEL[me.space_role] || me.space_role || '';
    const name = me.username || 'user';
    if (menuName) menuName.textContent = name;
    if (menuRole) menuRole.textContent = space || global;
    if (avatar) avatar.textContent = name.charAt(0).toUpperCase();
    if (meta) meta.textContent = `全局 · ${global}  ·  本空间 · ${space || '—'}`;
    if (logoutBtn) logoutBtn.hidden = false;
  } else {
    if (menuName) menuName.textContent = '本地开发';
    if (menuRole) menuRole.textContent = '免登录';
    if (avatar) avatar.textContent = 'G';
    if (meta) meta.textContent = 'auth_enabled=false';
    if (logoutBtn) logoutBtn.hidden = true;
  }

  if (me.space_role === 'viewer') {
    $('#btn-clear')?.setAttribute('hidden', '');
    $('#btn-add')?.setAttribute('hidden', '');
  }
  if (!['system_admin', 'admin', 'space_admin'].includes(me.space_role)
      && !['system_admin', 'admin'].includes(me.role)) {
    $$('.nav-admin-only').forEach((el) => el.setAttribute('hidden', ''));
  }
  if (!['system_admin', 'admin'].includes(me.role)) {
    $$('.nav-platform-only').forEach((el) => el.setAttribute('hidden', ''));
  }
  updatePendingBadge(me.pending_count || 0);
}

initNavigation();
initTopMenus();
initUserMenu();
initDebug();
initAssert();
initApproval();
initRegistry();
initStats();
initUsers();
initVisualPicker();
initCopilot();
initSettings();
applyI18n();
document.documentElement.lang = getLocale() === 'en' ? 'en' : 'zh-CN';
$('#btn-locale')?.addEventListener('click', () => {
  setLocale(getLocale() === 'en' ? 'zh' : 'en');
  applyI18n();
  const active = $$('[data-view].active')[0]?.dataset?.view;
  if (active) show(active);
});

async function boot() {
  try {
    let me = await api('/me');
    if (me?.error) throw new Error(me.error);
    if (me.current_space && me.current_space !== getSpace()) {
      setSpace(me.current_space);
      me = await api('/me');
    }
    setMe(me);
    function onSpaceChange(fresh) {
      setMe(fresh);
      applyRole(fresh);
      invalidateRegistryCache();
      refreshSpaceSwitcher(fresh, onSpaceChange);
      const active = $$('[data-view].active')[0]?.dataset?.view;
      if (active) VIEWS[active]?.onShow?.();
    }
    initSpaceSwitcher(onSpaceChange);
    applyRole(me);
    show('debug');
  } catch {
    location.href = '/admin/login.html';
  }
}

boot();

document.addEventListener('giso:pending-changed', () => {
  api('/me').then((me) => {
    setMe(me);
    updatePendingBadge(me.pending_count || 0);
  });
});

document.addEventListener('giso:navigate', (e) => show(e.detail?.view));
