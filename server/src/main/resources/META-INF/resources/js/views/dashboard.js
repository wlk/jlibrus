import { api } from '../api.js';
import { empty, fmtDate, fmtDateTime, fmtTime, h, mount } from '../dom.js';
import { lang, t } from '../i18n.js';
import { childPath } from '../state.js';
import { agendaItem, setTypeLabels, statusChip, typeLabel } from './common.js';

export async function render(container) {
  const [data, types] = await Promise.all([api.get(childPath('/dashboard')), api.get('/api/entry-types')]);
  setTypeLabels(types);
  const lessonsTitle = data.lessonsDay === data.today ? t('today') : fmtDate(data.lessonsDay, { weekday: 'long', day: 'numeric', month: 'long' });

  mount(container,
    h('h1', {}, `${data.child.name}`, h('span', { class: 'muted small' }, ` ${data.child.className} · ${data.child.school}`)),
    h('div', { class: 'grid' },
      h('div', { class: 'card' },
        h('h3', {}, t('luckyNumber')),
        h('div', { class: 'big' }, data.luckyNumber ? String(data.luckyNumber.number) : '–'),
        data.luckyNumber ? h('div', { class: 'muted small' }, fmtDate(data.luckyNumber.day)) : null),
      h('div', { class: 'card' },
        h('h3', {}, t('unread')),
        h('div', { class: 'big' }, data.unreadMessages === null ? '–' : String(data.unreadMessages)),
        h('a', { href: '#/messages' }, t('messages')))),
    h('div', { class: 'card' },
      h('h2', {}, `${t('lessonsOn')}: ${lessonsTitle}`),
      data.lessons.length === 0 ? empty(t('nothing')) : h('table', {}, h('tbody', {}, data.lessons.map(lessonRow)))),
    h('div', { class: 'card' },
      h('h2', {}, t('upcoming')),
      data.upcoming.length === 0 ? empty(t('nothing')) : h('ul', { class: 'list' }, data.upcoming.map((i) => agendaItem(i, lang())))),
    h('div', { class: 'card' },
      h('h2', {}, t('recent')),
      data.recent.length === 0 ? empty(t('nothing')) : h('ul', { class: 'list' }, data.recent.map(recentItem))));
}

function lessonRow(lesson) {
  return h('tr', { class: 'status-' + lesson.status },
    h('td', {}, String(lesson.lessonNo)),
    h('td', { class: 'muted' }, `${fmtTime(lesson.from)}–${fmtTime(lesson.to)}`),
    h('td', {}, lesson.subject, ' ', statusChip(lesson.status)),
    h('td', { class: 'muted small' }, lesson.room || ''));
}

function recentItem(entry) {
  return h('li', {},
    h('div', { class: 'row' },
      h('span', { class: 'chip' }, typeLabel(entry.type, lang())),
      h('strong', {}, entry.title),
      h('span', { class: 'spacer' }),
      h('span', { class: 'muted small' }, fmtDateTime(entry.noticedAt))),
    entry.detail ? h('div', { class: 'muted small' }, entry.detail) : null,
    entry.author ? h('div', { class: 'muted small' }, entry.author) : null);
}
