import { redirectIfAuthenticated, signIn } from './auth.js';

const form = document.getElementById('login-form');
const err = document.getElementById('err');
const btn = document.getElementById('btn-submit');

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  err.textContent = '';
  btn.disabled = true;
  const fd = new FormData(form);
  try {
    await signIn(
      fd.get('username')?.toString().trim() ?? '',
      fd.get('password')?.toString() ?? '',
    );
    const next = new URLSearchParams(location.search).get('next') || '/admin/';
    location.replace(next);
  } catch (ex) {
    err.textContent = ex.message || '登录失败';
    if (ex.code === 'no_space_membership') {
      err.textContent = ex.message;
    }
    if (ex.attemptsRemaining != null && ex.code === 'invalid_credentials') {
      err.textContent = ex.message;
    }
  } finally {
    btn.disabled = false;
  }
});

redirectIfAuthenticated();
