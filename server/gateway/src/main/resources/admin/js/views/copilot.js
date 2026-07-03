/* GISO Copilot — 产品 / 埋点流程答疑 */
import { $, esc, toast } from '../util.js';
import { api } from '../api.js';
import { t, applyI18n } from '../i18n.js';

const PROMPTS = {
  zh: [
    '埋点上报完整流程是什么？',
    'App Key 怎么配置 test/prod？',
    '事件进隔离区怎么办？',
    '注册表存在哪里？',
    'SSE 联调怎么用？',
    'Flutter 怎么接入 GISO？',
  ],
  en: [
    'What is the full tracking flow?',
    'How to configure App Keys for test/prod?',
    'How to handle quarantine events?',
    'Where is the registry stored?',
    'How does SSE live debug work?',
    'How do Flutter apps integrate GISO?',
  ],
};

let history = [];
let status = { provider: 'doc', ready: false };

function renderMessages() {
  const box = $('#copilot-messages');
  if (!box) return;
  if (!history.length) {
    box.innerHTML = `<p class="muted copilot-welcome">${esc(t('copilot.welcome'))}</p>`;
    return;
  }
  box.innerHTML = history.map((m) => `
    <div class="copilot-msg ${m.role}">
      <div class="copilot-role">${m.role === 'user' ? t('copilot.you') : t('copilot.bot')}</div>
      <div class="copilot-body">${formatAnswer(m.content)}</div>
    </div>`).join('');
  box.scrollTop = box.scrollHeight;
}

function formatAnswer(text) {
  return esc(text)
    .replace(/\n/g, '<br>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
}

function renderPrompts() {
  const el = $('#copilot-prompts');
  if (!el) return;
  const loc = document.documentElement.lang.startsWith('en') ? 'en' : 'zh';
  el.innerHTML = PROMPTS[loc].map((p) =>
    `<button type="button" class="copilot-chip" data-p="${esc(p)}">${esc(p)}</button>`).join('');
  el.querySelectorAll('.copilot-chip').forEach((b) => {
    b.onclick = () => send(b.dataset.p);
  });
}

async function send(text) {
  const input = $('#copilot-input');
  const msg = (text || input?.value || '').trim();
  if (!msg) return;
  if (input) input.value = '';
  history.push({ role: 'user', content: msg });
  renderMessages();
  $('#copilot-send')?.setAttribute('disabled', '');
  try {
    const resp = await api('/assistant/chat', {
      method: 'POST',
      body: JSON.stringify({
        message: msg,
        history: history.slice(0, -1).slice(-6),
      }),
    });
    if (resp.error) throw new Error(resp.error);
    history.push({ role: 'assistant', content: resp.answer || '' });
    renderMessages();
    renderSources(resp.sources || [], resp.suggested_followups || []);
  } catch (e) {
    toast(e.message || 'Copilot error');
  } finally {
    $('#copilot-send')?.removeAttribute('disabled');
  }
}

function renderSources(sources, followups) {
  const el = $('#copilot-meta');
  if (!el) return;
  let html = '';
  if (sources.length) {
    html += `<div class="copilot-sources"><span class="muted">${t('copilot.sources')}:</span> `
      + sources.map((s) => `<span class="pill">${esc(s)}</span>`).join(' ') + '</div>';
  }
  if (followups.length) {
    html += `<div class="copilot-followups">${followups.map((f) =>
      `<button type="button" class="copilot-chip" data-f="${esc(f)}">${esc(f)}</button>`).join('')}</div>`;
    el.querySelectorAll('[data-f]')?.forEach((b) => { b.onclick = () => send(b.dataset.f); });
  }
  el.innerHTML = html;
  el.querySelectorAll('.copilot-chip[data-f]').forEach((b) => {
    b.onclick = () => send(b.dataset.f);
  });
}

export async function initCopilot() {
  $('#copilot-form')?.addEventListener('submit', (e) => {
    e.preventDefault();
    send();
  });
  $('#copilot-clear')?.addEventListener('click', () => {
    history = [];
    renderMessages();
    $('#copilot-meta').innerHTML = '';
  });
  renderPrompts();
  renderMessages();
}

export async function renderCopilot() {
  applyI18n();
  try {
    status = await api('/assistant/status');
    const badge = $('#copilot-provider');
    if (badge) {
      badge.textContent = `${status.provider}${status.ready ? '' : ' (limited)'}`;
    }
  } catch { /* ignore */ }
  renderPrompts();
}
