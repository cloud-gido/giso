/* 当前登录用户与空间上下文 */
let me = { role: 'admin', space_role: 'admin', username: '', current_space: 'default' };

export function setMe(user) { me = user || me; }
export function getMe() { return me; }

export function isSystemAdmin() {
  return me.role === 'system_admin' || me.role === 'admin';
}

export function isSpaceAdmin() {
  return isSystemAdmin() || me.space_role === 'space_admin';
}

export function isAdmin() { return isSystemAdmin(); }

export function isEditor() { return me.space_role === 'editor'; }

export function isViewer() { return me.space_role === 'viewer'; }

export function hasSpaceAccess() {
  return isSystemAdmin() || (Array.isArray(me.spaces) && me.spaces.length > 0);
}

export function canEditRegistry() {
  return isSpaceAdmin() || me.space_role === 'editor';
}

export function canApprove() { return isSpaceAdmin(); }
