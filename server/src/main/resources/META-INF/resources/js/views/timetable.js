import { api } from '../api.js';
import { empty, fmtDate, fmtTime, h, isoDate, mount } from '../dom.js';
import { t } from '../i18n.js';
import { childPath } from '../state.js';
import { dateParam, datePager, groupBy, statusChip } from './common.js';

const WEEK_DAYS = 7;

export async function render(container, params) {
  const day = dateParam(params[0]);
  const lessons = await api.get(childPath('/timetable?date=' + isoDate(day)));
  const byDay = groupBy(lessons, (l) => l.date);

  mount(container,
    h('h1', {}, t('timetable')),
    datePager('timetable', day, WEEK_DAYS, t('thisWeek')),
    byDay.size === 0 ? empty(t('nothing')) : [...byDay.entries()].map(([date, items]) => dayCard(date, items)));
}

function dayCard(date, lessons) {
  return h('div', { class: 'card table-scroll' },
    h('h3', {}, fmtDate(date, { weekday: 'long', day: 'numeric', month: 'long' })),
    h('table', {},
      h('thead', {}, h('tr', {}, h('th', {}, t('no')), h('th', {}, t('time')), h('th', {}, t('subject')),
        h('th', {}, t('teacher')), h('th', {}, t('room')))),
      h('tbody', {}, lessons.map((l) => h('tr', { class: 'status-' + l.status },
        h('td', {}, String(l.lessonNo)),
        h('td', { class: 'muted' }, `${fmtTime(l.from)}–${fmtTime(l.to)}`),
        h('td', {}, l.subject, ' ', statusChip(l.status),
          l.movedToDate ? h('div', { class: 'small muted' }, '→ ' + fmtDate(l.movedToDate)) : null),
        h('td', {}, l.teacher, l.originalTeacher ? h('div', { class: 'small muted' }, `(${l.originalTeacher})`) : null),
        h('td', { class: 'muted small' }, l.room || ''))))));
}
