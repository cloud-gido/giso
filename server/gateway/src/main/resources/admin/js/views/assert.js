/* 用例断言：声明期望事件序列，与设备实报有序比对 */
import { $, esc, toast } from '../util.js';
import { api } from '../api.js';

function parseExpect(text) {
  return text.split('\n').map((l) => l.trim()).filter(Boolean).map((l) => {
    const parts = l.split(/\s+/);
    const e = { event: parts[0] };
    parts.slice(1).forEach((p) => {
      const [k, v] = p.split('=');
      if (k && v && ['pgid', 'eid', 'code'].includes(k)) e[k] = v;
    });
    return e;
  });
}

export function initAssert() {
  $('#btn-assert-run').onclick = async () => {
    const did = $('#assert-did').value.trim();
    if (!did) { toast('请填写完整 did（精确匹配）', 'error'); return; }
    const expect = parseExpect($('#assert-input').value);
    if (!expect.length) { toast('请至少声明一条期望事件', 'error'); return; }

    const r = await api('/assert', { method: 'POST', body: JSON.stringify({ did, expect }) });
    if (r.error) { toast(r.error, 'error'); return; }

    $('#assert-summary').innerHTML = r.pass
      ? `<div class="verdict pass"><span class="verdict-icon">✓</span>
           <div><b>用例通过</b><span>命中 ${r.matched}/${r.expected} · 实报 ${r.actual_events} 条</span></div></div>`
      : `<div class="verdict fail"><span class="verdict-icon">✗</span>
           <div><b>用例未通过</b><span>命中 ${r.matched}/${r.expected} · 实报 ${r.actual_events} 条</span></div></div>`;

    $('#assert-result').innerHTML = r.detail.map((d, idx) => {
      const desc = [d.expect.event,
        d.expect.pgid && `pgid=${d.expect.pgid}`,
        d.expect.eid && `eid=${d.expect.eid}`,
        d.expect.code && `code=${d.expect.code}`].filter(Boolean).join(' ');
      return `<div class="assert-row ${d.hit ? 'hit' : 'miss'}">
        <span class="step-no">${idx + 1}</span>
        <span class="badge ${d.hit ? 'ok' : 'error'}">${d.hit ? '命中 #' + d.at : '缺失'}</span>
        <span class="key">${esc(desc)}</span></div>`;
    }).join('');
  };
}
