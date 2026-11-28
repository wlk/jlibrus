import { api } from '../api.js';
import { empty, fmtDate, h, mount, toast } from '../dom.js';
import { t } from '../i18n.js';
import { childPath } from '../state.js';
import { action } from './common.js';

export async function render(container) {
  const items = await api.get(childPath('/announcements'));
  mount(container,
    h('h1', {}, t('announcements')),
    items.length === 0 ? empty(t('nothing')) : items.map((a) => card(a, container)));
}

function card(a, container) {
  const markButton = a.read ? null : h('button', {
    class: 'secondary',
    on: {
      click: (e) => action(e.currentTarget, async () => {
        await api.post(childPath(`/announcements/${encodeURIComponent(a.id)}/read`));
        toast(t('read'));
        await render(container);
      })
    }
  }, t('markRead'));
  return h('div', { class: 'card' },
    h('div', { class: 'row' },
      h('strong', { class: a.read ? '' : 'unread' }, a.title),
      a.read ? null : h('span', { class: 'chip warn' }, t('newBadge')),
      h('span', { class: 'spacer' }),
      h('span', { class: 'muted small' }, fmtDate(a.startDate))),
    h('div', { class: 'message-body' }, a.content),
    h('div', { class: 'row' }, h('span', { class: 'muted small' }, a.author), h('span', { class: 'spacer' }), markButton));
}
