/* 当前登录用户（各视图只读） */
let me = { role: 'admin', username: '' };

export function setMe(user) { me = user || me; }
export function getMe() { return me; }
export function isAdmin() { return me.role === 'admin'; }
export function isEditor() { return me.role === 'editor'; }
export function canEditRegistry() { return me.role === 'admin' || me.role === 'editor'; }
