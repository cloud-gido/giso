/* 可视化圈选 v2：在页面截图上框选元素，生成 registry draft */
import { $, $$, esc, toast } from '../util.js';
import { api, uploadScreenshot } from '../api.js';
import { canEditRegistry } from '../session.js';
import { t, applyI18n } from '../i18n.js';

let rects = [];
let drawing = false;
let start = null;
let imgW = 1;
let imgH = 1;

function resetCanvas() {
  rects = [];
  const canvas = $('#vp-canvas');
  if (canvas) {
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawImage();
  }
  renderRectList();
}

function drawImage() {
  const img = $('#vp-image');
  const canvas = $('#vp-canvas');
  if (!img?.complete || !canvas) return;
  imgW = img.naturalWidth || img.width;
  imgH = img.naturalHeight || img.height;
  canvas.width = img.clientWidth;
  canvas.height = img.clientHeight;
  redraw();
}

function redraw() {
  const canvas = $('#vp-canvas');
  const img = $('#vp-image');
  if (!canvas || !img?.complete || !img.naturalWidth) return;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
  ctx.strokeStyle = '#3b82f6';
  ctx.lineWidth = 2;
  ctx.fillStyle = 'rgba(59,130,246,0.15)';
  rects.forEach((r, i) => {
    const x = r.x * canvas.width;
    const y = r.y * canvas.height;
    const w = r.w * canvas.width;
    const h = r.h * canvas.height;
    ctx.fillRect(x, y, w, h);
    ctx.strokeRect(x, y, w, h);
    ctx.fillStyle = '#1e40af';
    ctx.font = '12px sans-serif';
    ctx.fillText(r.eid || `#${i + 1}`, x + 4, y + 14);
    ctx.fillStyle = 'rgba(59,130,246,0.15)';
  });
}

function renderRectList() {
  const el = $('#vp-rect-list');
  if (!el) return;
  if (!rects.length) {
    el.innerHTML = `<p class="muted">${t('vp.empty')}</p>`;
    return;
  }
  el.innerHTML = rects.map((r, i) => `
    <div class="vp-row">
      <input data-i="${i}" data-f="eid" value="${esc(r.eid || '')}" placeholder="${t('vp.eidPh')}" pattern="[a-z][a-z0-9_]{0,31}">
      <input data-i="${i}" data-f="desc" value="${esc(r.desc || '')}" placeholder="${t('vp.desc')}">
      <button type="button" class="ghost" data-del="${i}">×</button>
    </div>`).join('');
  $$('[data-i]', el).forEach((inp) => {
    inp.oninput = () => {
      const i = +inp.dataset.i;
      rects[i][inp.dataset.f] = inp.value.trim();
    };
  });
  $$('[data-del]', el).forEach((btn) => {
    btn.onclick = () => {
      rects.splice(+btn.dataset.del, 1);
      renderRectList();
      redraw();
    };
  });
}

function bindCanvas() {
  const canvas = $('#vp-canvas');
  if (!canvas) return;
  canvas.onmousedown = (e) => {
    if (!canEditRegistry()) return;
    drawing = true;
    const rect = canvas.getBoundingClientRect();
    start = { x: (e.clientX - rect.left) / canvas.width, y: (e.clientY - rect.top) / canvas.height };
  };
  canvas.onmousemove = (e) => {
    if (!drawing || !start) return;
    const rect = canvas.getBoundingClientRect();
    const cx = (e.clientX - rect.left) / canvas.width;
    const cy = (e.clientY - rect.top) / canvas.height;
    redraw();
    const ctx = canvas.getContext('2d');
    const x = Math.min(start.x, cx) * canvas.width;
    const y = Math.min(start.y, cy) * canvas.height;
    const w = Math.abs(cx - start.x) * canvas.width;
    const h = Math.abs(cy - start.y) * canvas.height;
    ctx.strokeStyle = '#f59e0b';
    ctx.strokeRect(x, y, w, h);
  };
  canvas.onmouseup = (e) => {
    if (!drawing || !start) return;
    drawing = false;
    const rect = canvas.getBoundingClientRect();
    const cx = (e.clientX - rect.left) / canvas.width;
    const cy = (e.clientY - rect.top) / canvas.height;
    const x = Math.min(start.x, cx);
    const y = Math.min(start.y, cy);
    const w = Math.abs(cx - start.x);
    const h = Math.abs(cy - start.y);
    start = null;
    if (w < 0.01 || h < 0.01) return;
    rects.push({ x, y, w, h, eid: '', desc: '', bounds: { x, y, w, h } });
    renderRectList();
    redraw();
  };
}

async function submitDraft() {
  const pgid = $('#vp-pgid')?.value?.trim();
  const screenshot = $('#vp-screenshot')?.value?.trim();
  if (!pgid) return toast(t('vp.needPgid'));
  const elements = rects.filter((r) => r.eid).map((r) => ({
    eid: r.eid,
    desc: r.desc || r.eid,
    bounds: r.bounds,
    selectors: [{ platform: 'web', type: 'bounds_norm', value: JSON.stringify(r.bounds) }],
    params: [],
    status: 'draft',
  }));
  if (!elements.length) return toast(t('vp.needRect'));
  const res = await api('/registry/visual-draft', {
    method: 'POST',
    body: JSON.stringify({ pgid, screenshot, elements }),
  });
  if (res.errors?.length) toast(res.errors.join('; '));
  else toast(t('vp.saved', { n: res.created_elements?.length || 0 }));
}

function importViewTree() {
  try {
    const raw = $('#vp-viewtree')?.value?.trim();
    if (!raw) return;
    const nodes = JSON.parse(raw);
    if (!Array.isArray(nodes)) throw new Error('array expected');
    for (const n of nodes) {
      if (!n.bounds) continue;
      rects.push({
        x: n.bounds.x, y: n.bounds.y, w: n.bounds.w, h: n.bounds.h,
        eid: n.suggested_eid || n.eid || '',
        desc: n.label || n.desc || '',
        bounds: n.bounds,
      });
    }
    renderRectList();
    drawImage();
    toast(t('vp.imported', { n: rects.length }));
  } catch (e) {
    toast(t('vp.importFail') + ': ' + e.message);
  }
}

export function initVisualPicker() {
  $('#vp-file')?.addEventListener('change', async () => {
    const input = $('#vp-file');
    const file = input?.files?.[0];
    if (input) input.value = '';
    if (!file) return;
    if (!canEditRegistry()) return toast('无权上传');
    try {
      toast('上传中…');
      const url = await uploadScreenshot(file);
      $('#vp-screenshot').value = url;
      const img = $('#vp-image');
      img.onload = drawImage;
      img.hidden = false;
      img.src = url;
      toast('上传成功');
    } catch (e) {
      toast(e.message || '上传失败');
    }
  });
  $('#vp-load-img')?.addEventListener('click', () => {
    const url = $('#vp-screenshot')?.value?.trim();
    if (!url) return toast(t('vp.needScreenshot'));
    const img = $('#vp-image');
    img.onload = drawImage;
    img.src = url;
  });
  $('#vp-reset')?.addEventListener('click', resetCanvas);
  $('#vp-submit')?.addEventListener('click', () => submitDraft().catch((e) => toast(e.message)));
  $('#vp-import-vt')?.addEventListener('click', importViewTree);
  bindCanvas();
  window.addEventListener('resize', drawImage);
}

export function renderVisualPicker() {
  applyI18n();
  const btn = $('#vp-submit');
  if (!btn) return;
  if (canEditRegistry()) btn.removeAttribute('hidden');
  else btn.setAttribute('hidden', '');
}
