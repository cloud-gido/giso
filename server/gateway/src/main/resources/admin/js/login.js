import { fetchMe, login } from './auth.js';

const form = document.getElementById('login-form');
const err = document.getElementById('err');
const btn = document.getElementById('btn-submit');

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  err.textContent = '';
  btn.disabled = true;
  const fd = new FormData(form);
  try {
    await login(
      fd.get('username')?.toString().trim() ?? '',
      fd.get('password')?.toString() ?? '',
    );
    const next = new URLSearchParams(location.search).get('next') || '/admin/';
    location.replace(next);
  } catch (ex) {
    err.textContent = ex.message || '登录失败';
  } finally {
    btn.disabled = false;
  }
});

fetchMe().then((me) => {
  if (me) {
    const next = new URLSearchParams(location.search).get('next') || '/admin/';
    location.replace(next);
  }
});
