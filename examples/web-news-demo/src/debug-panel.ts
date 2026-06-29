import { ADMIN_URL, TRACK_DEBUG, TRACK_ENDPOINT } from './config';

const DID_KEY = '_giso_did';

function deviceId(): string {
  try {
    return localStorage.getItem(DID_KEY) ?? '（启动后生成）';
  } catch {
    return '（无存储）';
  }
}

export function mountDebugPanel(getPgid: () => string): HTMLElement {
  const panel = document.createElement('aside');
  panel.className = 'debug-panel';
  panel.innerHTML = `
    <div class="debug-head">
      <strong>GISO 联调</strong>
      <button type="button" class="debug-copy" title="复制 did">复制 did</button>
      <a class="debug-admin" href="${ADMIN_URL}" target="_blank" rel="noopener">管理台 ↗</a>
    </div>
    <div class="debug-line" data-k="did">did: ${deviceId()}</div>
    <div class="debug-line" data-k="pgid">pgid: —</div>
    <div class="debug-line debug-mono" data-k="endpoint">endpoint: ${TRACK_ENDPOINT}</div>
    <div class="debug-line" data-k="mode">${TRACK_DEBUG ? 'debug · 实时上报 · env=test' : '生产模式 · 攒批上报'}</div>
    <div class="debug-line" data-k="net">网关: 检测中…</div>
  `;

  const pgidEl = panel.querySelector('[data-k="pgid"]')!;
  const netEl = panel.querySelector('[data-k="net"]')!;
  const didEl = panel.querySelector('[data-k="did"]')!;

  const refresh = () => {
    pgidEl.textContent = `pgid: ${getPgid()}`;
    didEl.textContent = `did: ${deviceId()}`;
  };
  refresh();
  setInterval(refresh, 2000);

  panel.querySelector('.debug-copy')!.addEventListener('click', async () => {
    const did = deviceId();
    try {
      await navigator.clipboard.writeText(did);
      netEl.textContent = 'did 已复制到剪贴板';
      setTimeout(probeGateway, 1500);
    } catch {
      netEl.textContent = `请手动复制: ${did}`;
    }
  });

  async function probeGateway() {
    const configUrl = TRACK_ENDPOINT.replace(/\/v1\/track\/?$/, '/v1/config');
    try {
      const r = await fetch(configUrl, { method: 'GET' });
      if (r.ok) {
        netEl.textContent = '网关: 已连通 ✓';
        netEl.className = 'debug-line debug-ok';
      } else {
        netEl.textContent = `网关: HTTP ${r.status} ✗`;
        netEl.className = 'debug-line debug-bad';
      }
    } catch {
      netEl.textContent = '网关: 不可达 ✗（请确认 deploy 已启动）';
      netEl.className = 'debug-line debug-bad';
    }
  }
  probeGateway();

  return panel;
}
