const form = document.getElementById('login-form');
const err = document.getElementById('err');
const btn = document.getElementById('btn-submit');

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  err.textContent = '';
  btn.disabled = true;
  const fd = new FormData(form);
  try {
    const r = await fetch('/admin/api/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: fd.get('username')?.toString().trim(),
        password: fd.get('password')?.toString() ?? '',
      }),
    });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) {
      err.textContent = data.error || '登录失败';
      return;
    }
    const next = new URLSearchParams(location.search).get('next') || '/admin/';
    location.href = next;
  } catch {
    err.textContent = '网络错误，请重试';
  } finally {
    btn.disabled = false;
  }
});

fetch('/admin/api/me', { credentials: 'same-origin' })
  .then((r) => (r.ok ? r.json() : null))
  .then((me) => {
    if (me?.username && me?.auth_enabled !== false && !me?.error) {
      location.replace(new URLSearchParams(location.search).get('next') || '/admin/');
    }
  })
  .catch(() => {});
