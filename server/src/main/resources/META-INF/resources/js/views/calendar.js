import { api } from '../api.js';
import { addDays, empty, fmtDate, h, isoDate, mount } from '../dom.js';
import { lang, t } from '../i18n.js';
import { childPath } from '../state.js';
import { agendaItem, dateParam, datePager, entryTypes, groupBy } from './common.js';

const RANGE_DAYS = 31;

export async function render(container, params) {
  const start = dateParam(params[0]);
  const end = addDays(start, RANGE_DAYS);
  const [items] = await Promise.all([api.get(childPath(`/agenda?from=${isoDate(start)}&to=${isoDate(end)}`)), entryTypes()]);
  const byDay = groupBy(items.filter((i) => i.type !== 'LESSON'), (i) => i.start);

  mount(container,
    h('h1', {}, t('calendar')),
    datePager('calendar', start, RANGE_DAYS, t('today')),
    h('p', { class: 'muted small' }, `${fmtDate(isoDate(start))} – ${fmtDate(isoDate(end))}`),
    byDay.size === 0 ? empty(t('nothing')) : [...byDay.entries()].map(([day, dayItems]) =>
      h('div', { class: 'card' },
        h('h3', {}, fmtDate(day, { weekday: 'long', day: 'numeric', month: 'long' })),
        h('ul', { class: 'list' }, dayItems.map((i) => agendaItem(i, lang()))))));
}
