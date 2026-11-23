import { api } from '../api.js';
import { empty, fmtDate, fmtDateTime, h, mount } from '../dom.js';
import { t } from '../i18n.js';
import { childPath } from '../state.js';

export async function render(container) {
  const [subjects, descriptive] = await Promise.all([api.get(childPath('/grades')), api.get(childPath('/descriptive-grades'))]);
  mount(container,
    h('h1', {}, t('grades')),
    descriptive.length ? descriptiveCard(descriptive) : null,
    subjects.length === 0 && descriptive.length === 0 ? empty(t('nothing')) : null,
    subjects.length === 0 ? null : h('div', { class: 'card table-scroll' },
      h('table', {},
        h('thead', {}, h('tr', {},
          h('th', {}, t('subject')), h('th', {}, t('semester1')), h('th', {}, t('semester2')),
          h('th', {}, t('summaryGrades')), h('th', {}, t('average')))),
        h('tbody', {}, subjects.map(subjectRow)))));
}

function subjectRow(subject) {
  return h('tr', {},
    h('td', {}, h('strong', {}, subject.subject)),
    h('td', {}, subject.semester1.map(grade), avg(subject.averageSemester1)),
    h('td', {}, subject.semester2.map(grade), avg(subject.averageSemester2)),
    h('td', {}, subject.summary.map(grade)),
    h('td', {}, h('strong', {}, subject.average === null ? '–' : subject.average.toFixed(2))));
}

function avg(value) {
  return value === null ? null : h('div', { class: 'muted small' }, `${t('average')}: ${value.toFixed(2)}`);
}

function grade(g) {
  const details = [
    g.category && `${t('category')}: ${g.category}`,
    `${t('weight')}: ${g.weight}`,
    g.teacher && `${t('teacher')}: ${g.teacher}`,
    g.addedAt && fmtDateTime(g.addedAt),
    g.comment && `${t('comment')}: ${g.comment}`
  ].filter(Boolean).join('\n');
  return h('span', { class: 'grade', title: details, tabindex: 0, style: { borderLeftColor: g.colorHex } }, g.value);
}

function descriptiveCard(subjects) {
  return h('div', { class: 'card' },
    h('h2', {}, t('descriptiveGrades')),
    subjects.map((subject) => h('div', {},
      h('h3', {}, subject.subject || '—'),
      h('ul', { class: 'list' }, subject.grades.map((g) => h('li', {},
        h('div', { class: 'row' },
          g.value ? h('span', { class: 'grade' }, g.value) : null,
          h('strong', {}, g.skill || ''),
          h('span', { class: 'spacer' }),
          h('span', { class: 'muted small' }, fmtDate(g.date))),
        g.comments.length ? h('div', { class: 'message-body' }, g.comments.join('\n')) : null,
        g.requirements.length ? h('div', { class: 'muted small' }, `${t('requirements')}: ${g.requirements.join('; ')}`) : null,
        g.teacher ? h('div', { class: 'muted small' }, g.teacher) : null))))));
}
