import { api } from '../api.js';
import { empty, fmtDate, h, isoDate, mount } from '../dom.js';
import { t } from '../i18n.js';
import { childPath } from '../state.js';

export async function render(container) {
  const items = await api.get(childPath('/homework'));
  const today = isoDate(new Date());
  mount(container,
    h('h1', {}, t('homework')),
    items.length === 0 ? empty(t('nothing')) : items.map((hw) => h('div', { class: 'card' },
      h('div', { class: 'row' },
        h('strong', {}, hw.topic),
        h('span', { class: 'spacer' }),
        h('span', { class: 'chip ' + (hw.dueOn && hw.dueOn >= today ? 'warn' : '') }, `${t('due')}: ${fmtDate(hw.dueOn)}`)),
      h('div', { class: 'message-body' }, hw.text),
      h('div', { class: 'muted small' }, `${hw.teacher} · ${t('assigned')}: ${fmtDate(hw.assignedOn)}`),
      hw.attachments.length ? h('div', { class: 'small' }, `${t('attachments')}: ${hw.attachments.join(', ')}`) : null)));
}
