import { api } from '../api.js';
import { empty, fmtDate, h, mount } from '../dom.js';
import { t } from '../i18n.js';
import { childPath } from '../state.js';

const KIND_CLASS = { ABSENT: 'danger', EXCUSED: 'ok', LATE: 'warn', RELEASED: '' };
const JUSTIFICATION_CLASS = { ACCEPTED: 'ok', REJECTED: 'danger', PENDING: 'warn' };

export async function render(container) {
  const report = await api.get(childPath('/attendance'));
  const stats = report.stats;
  mount(container,
    h('h1', {}, t('attendance')),
    h('div', { class: 'grid' },
      h('div', { class: 'card' },
        h('h3', {}, t('presence')),
        h('div', { class: 'big' }, stats.presencePercent === null ? '–' : `${stats.presencePercent}%`),
        h('div', { class: 'muted small' }, `${stats.lessons} ${t('lessons')}`)),
      h('div', { class: 'card' },
        ['ABSENT', 'EXCUSED', 'LATE', 'RELEASED'].map((kind) =>
          h('div', { class: 'row' }, h('span', { class: 'chip ' + KIND_CLASS[kind] }, t('kind_' + kind)),
            h('strong', {}, String(stats.byKind[kind] || 0)))))),
    h('div', { class: 'card' },
      h('h2', {}, t('absences')),
      report.absences.length === 0 ? empty(t('nothing')) : h('div', { class: 'table-scroll' }, h('table', {},
        h('tbody', {}, report.absences.map((a) => h('tr', {},
          h('td', {}, fmtDate(a.date)),
          h('td', { class: 'muted' }, a.lessonNo === null ? '' : String(a.lessonNo)),
          h('td', {}, a.subject),
          h('td', {}, h('span', { class: 'chip ' + (KIND_CLASS[a.kind] || '') }, a.type || t('kind_' + a.kind))),
          h('td', { class: 'muted small' }, a.teacher))))))),
    report.justifications.length ? justificationsCard(report.justifications) : null);
}

function justificationsCard(items) {
  return h('div', { class: 'card' },
    h('h2', {}, t('justifications')),
    h('ul', { class: 'list' }, items.map((j) => h('li', {},
      h('div', { class: 'row' },
        h('span', { class: 'chip ' + JUSTIFICATION_CLASS[j.status] }, t('just_' + j.status)),
        h('strong', {}, j.from === j.to || !j.to ? fmtDate(j.from) : `${fmtDate(j.from)} – ${fmtDate(j.to)}`),
        j.hasAttachment ? h('span', { class: 'chip' }, '📎') : null,
        h('span', { class: 'spacer' }),
        h('span', { class: 'muted small' }, `${t('sentOn')}: ${fmtDate(j.postedAt)}`)),
      j.message ? h('div', { class: 'message-body' }, j.message) : null,
      h('div', { class: 'muted small' },
        [j.justifiedAbsences ? `${j.justifiedAbsences} ${t('justifiedAbsences')}` : null, j.teachers.join(', ')]
          .filter(Boolean).join(' · '))))));
}
