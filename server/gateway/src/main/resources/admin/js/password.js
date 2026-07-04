/* 头像菜单 · 修改自己的密码 */
import { $, toast } from './util.js';
import { api, logout } from './api.js';
import { t } from './i18n.js';

export function initPasswordChange() {
  const dialog = $('#password-dialog');
  const form = $('#password-form');
  const err = $('#pwd-err');
  const btn = $('#btn-change-password');

  btn?.addEventListener('click', () => {
    err.textContent = '';
    form?.reset();
    dialog?.showModal();
  });

  $('#pwd-cancel')?.addEventListener('click', () => dialog?.close());

  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    const current = $('#pwd-current')?.value ?? '';
    const next = $('#pwd-new')?.value ?? '';
    const confirm = $('#pwd-confirm')?.value ?? '';
    if (next.length < 6) {
      err.textContent = t('password.tooShort');
      return;
    }
    if (next !== confirm) {
      err.textContent = t('password.mismatch');
      return;
    }
    const submit = $('#pwd-submit');
    if (submit) submit.disabled = true;
    try {
      const r = await api('/me/password', {
        method: 'POST',
        body: JSON.stringify({ current_password: current, new_password: next }),
      });
      if (r.error) {
        err.textContent = r.error;
        return;
      }
      dialog?.close();
      toast(r.message || t('password.saved'));
      setTimeout(() => logout(), 800);
    } catch (ex) {
      err.textContent = ex.message || t('password.failed');
    } finally {
      if (submit) submit.disabled = false;
    }
  });
}
