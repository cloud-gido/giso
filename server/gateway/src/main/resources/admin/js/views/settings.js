/* 系统设置 — Copilot LLM + 出口管道勾选（对齐 GIDO 系统设置） */
import { $, $$, esc, toast } from '../util.js';
import { api } from '../api.js';
import { t } from '../i18n.js';

let settings = null;

function renderSinks() {
  const box = $('#settings-sinks');
  if (!box || !settings) return;
  const enabled = new Set(settings.sinks?.enabled || []);
  const catalog = settings.sink_catalog || [];
  box.innerHTML = catalog.map((s) => `
    <label class="settings-sink-row">
      <input type="checkbox" name="sink" value="${esc(s.id)}" ${enabled.has(s.id) ? 'checked' : ''}
        ${settings.writable ? '' : 'disabled'}>
      <div>
        <b>${esc(s.title || s.id)}</b>
        <p class="muted">${esc(s.desc)}</p>
      </div>
    </label>`).join('');
  const hint = $('#settings-sink-hint');
  if (hint) {
    hint.innerHTML = t('settings.sinkHint');
  }
}

function fillAssistantForm() {
  const a = settings?.assistant || {};
  $('#set-assistant-enabled') && ($('#set-assistant-enabled').checked = !!a.enabled);
  $('#set-assistant-provider') && ($('#set-assistant-provider').value = a.provider || 'doc');
  $('#set-openai-base') && ($('#set-openai-base').value = a.openai_base_url || '');
  $('#set-openai-model') && ($('#set-openai-model').value = a.openai_model || '');
  $('#set-openai-key') && ($('#set-openai-key').value = '');
  $('#set-openai-key') && ($('#set-openai-key').placeholder = a.openai_api_key_set
    ? (a.openai_api_key_masked || '••••') : t('settings.apiKeyPh'));
  $('#set-gido-proxy') && ($('#set-gido-proxy').value = a.gido_proxy_url || '');
  const ro = !settings?.writable;
  $$('#view-settings input, #view-settings select, #view-settings button.save').forEach((el) => {
    if (el.id === 'settings-refresh') return;
    if (ro) el.setAttribute('disabled', '');
    else el.removeAttribute('disabled');
  });
  if ($('#settings-readonly-note')) {
    $('#settings-readonly-note').hidden = !ro;
  }
}

export async function renderSettings() {
  try {
    settings = await api('/settings');
    renderSinks();
    fillAssistantForm();
    const active = $('#settings-active-sinks');
    if (active) {
      active.textContent = (settings.sinks?.active || []).join(', ') || '—';
    }
  } catch (e) {
    toast(e.message);
  }
}

export function initSettings() {
  $('#settings-refresh')?.addEventListener('click', () => renderSettings());
  $('#settings-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!settings?.writable) return toast(t('settings.readonly'));
    const sinks = [...$$('input[name=sink]:checked')].map((x) => x.value);
    if (!sinks.length) return toast(t('settings.sinkRequired'));
    const body = {
      assistant: {
        enabled: $('#set-assistant-enabled')?.checked,
        provider: $('#set-assistant-provider')?.value,
        openai_base_url: $('#set-openai-base')?.value?.trim(),
        openai_model: $('#set-openai-model')?.value?.trim(),
        openai_api_key: $('#set-openai-key')?.value?.trim(),
        gido_proxy_url: $('#set-gido-proxy')?.value?.trim(),
      },
      sinks: { enabled: sinks },
    };
    try {
      const r = await api('/settings', { method: 'PUT', body: JSON.stringify(body) });
      toast(t('settings.saved', { sinks: (r.active_sinks || []).join(', ') }));
      await renderSettings();
    } catch (err) {
      toast(err.message || 'save failed');
    }
  });
}
