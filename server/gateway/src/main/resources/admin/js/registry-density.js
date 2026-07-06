/* 注册表表格密度（不用 CSS transform，避免破坏 sticky / 布局） */
const DENSITY_KEY = 'giso_reg_density';
const DENSITIES = ['compact', 'normal', 'comfort'];

function applyDensity(view, density) {
  if (!view || !DENSITIES.includes(density)) return;
  DENSITIES.forEach((d) => view.classList.remove(`registry-density-${d}`));
  view.classList.add(`registry-density-${density}`);
  document.querySelectorAll('#reg-density [data-density]').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.density === density);
  });
  localStorage.setItem(DENSITY_KEY, density);
}

export function initRegistryDensity() {
  const view = document.getElementById('view-registry');
  if (!view) return;
  let density = localStorage.getItem(DENSITY_KEY) || 'normal';
  if (!DENSITIES.includes(density)) density = 'normal';
  applyDensity(view, density);

  document.querySelectorAll('#reg-density [data-density]').forEach((btn) => {
    btn.addEventListener('click', () => applyDensity(view, btn.dataset.density));
  });

  document.addEventListener('keydown', (e) => {
    if (!view.classList.contains('active')) return;
    if (!(e.ctrlKey || e.metaKey) || e.altKey) return;
    const tag = (e.target?.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return;
    let density = localStorage.getItem(DENSITY_KEY) || 'normal';
    const i = DENSITIES.indexOf(density);
    if (e.key === '=' || e.key === '+') {
      e.preventDefault();
      applyDensity(view, DENSITIES[Math.min(DENSITIES.length - 1, i + 1)]);
    } else if (e.key === '-') {
      e.preventDefault();
      applyDensity(view, DENSITIES[Math.max(0, i - 1)]);
    } else if (e.key === '0') {
      e.preventDefault();
      applyDensity(view, 'normal');
    }
  });
}
