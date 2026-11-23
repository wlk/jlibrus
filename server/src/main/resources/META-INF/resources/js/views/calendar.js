import { api } from '../api.js';
import { addDays, empty, fmtDate, h, isoDate, mount } from '../dom.js';
import { lang, t } from '../i18n.js';
import { childPath } from '../state.js';
import { agendaItem, setTypeLabels } from './common.js';

const RANGE_DAYS = 31;

export async function render(container, params) {
  const start = params[0] && /^\d{4}-\d{2}-\d{2}$/.test(params[0]) ? new Date(params[0] + 'T12:00:00') : new Date();
  const end = addDays(start, RANGE_DAYS);
  const [items, types] = await Promise.all([
    api.get(childPath(`/agenda?from=${isoDate(start)}&to=${isoDate(end)}`)),
    api.get('/api/entry-types')
  ]);
  setTypeLabels(types);
  const visible = items.filter((i) => i.type !== 'LESSON');
  const byDay = new Map();
  visible.forEach((i) => byDay.set(i.start, [...(byDay.get(i.start) || []), i]));

  mount(container,
    h('h1', {}, t('calendar')),
    h('div', { class: 'tabs' },
      h('a', { class: 'button secondary', href: '#/calendar/' + isoDate(addDays(start, -RANGE_DAYS)) }, t('prevWeek')),
      h('a', { class: 'button secondary', href: '#/calendar' }, t('today')),
      h('a', { class: 'button secondary', href: '#/calendar/' + isoDate(end) }, t('nextWeek'))),
    h('p', { class: 'muted small' }, `${fmtDate(isoDate(start))} – ${fmtDate(isoDate(end))}`),
    byDay.size === 0 ? empty(t('nothing')) : [...byDay.entries()].map(([day, dayItems]) =>
      h('div', { class: 'card' },
        h('h3', {}, fmtDate(day, { weekday: 'long', day: 'numeric', month: 'long' })),
        h('ul', { class: 'list' }, dayItems.map((i) => agendaItem(i, lang()))))));
}
