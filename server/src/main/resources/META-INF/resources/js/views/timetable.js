import { api } from '../api.js';
import { addDays, empty, fmtDate, fmtTime, h, isoDate, mount } from '../dom.js';
import { t } from '../i18n.js';
import { childPath } from '../state.js';
import { statusChip } from './common.js';

export async function render(container, params) {
  const day = params[0] && /^\d{4}-\d{2}-\d{2}$/.test(params[0]) ? new Date(params[0] + 'T12:00:00') : new Date();
  const lessons = await api.get(childPath('/timetable?date=' + isoDate(day)));
  const byDay = new Map();
  lessons.forEach((l) => byDay.set(l.date, [...(byDay.get(l.date) || []), l]));

  mount(container,
    h('h1', {}, t('timetable')),
    h('div', { class: 'tabs' },
      h('a', { class: 'button secondary', href: '#/timetable/' + isoDate(addDays(day, -7)) }, t('prevWeek')),
      h('a', { class: 'button secondary', href: '#/timetable' }, t('thisWeek')),
      h('a', { class: 'button secondary', href: '#/timetable/' + isoDate(addDays(day, 7)) }, t('nextWeek'))),
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
