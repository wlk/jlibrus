import { api } from '../api.js';
import { empty, fmtDate, fmtDateTime, h, mount } from '../dom.js';
import { t } from '../i18n.js';
import { childPath } from '../state.js';

const POLARITY = { POSITIVE: ['ok', 'positive'], NEGATIVE: ['danger', 'negative'], NEUTRAL: ['', 'neutral'] };

export async function render(container) {
  const data = await api.get(childPath('/behaviour'));
  mount(container,
    h('h1', {}, t('behaviour')),
    h('div', { class: 'card' },
      h('h2', {}, t('notes')),
      data.notes.length === 0 ? empty(t('nothing')) : h('ul', { class: 'list' }, data.notes.map((n) => h('li', {},
        h('div', { class: 'row' },
          h('span', { class: 'chip ' + POLARITY[n.polarity][0] }, t(POLARITY[n.polarity][1])),
          h('strong', {}, n.category),
          h('span', { class: 'spacer' }),
          h('span', { class: 'muted small' }, fmtDate(n.date))),
        h('div', {}, n.text),
        h('div', { class: 'muted small' }, n.teacher))))),
    h('div', { class: 'card' },
      h('h2', {}, t('points')),
      data.points.length === 0 ? empty(t('nothing')) : h('ul', { class: 'list' }, data.points.map((p) => h('li', {},
        h('div', { class: 'row' },
          h('span', { class: 'grade' }, p.display || '·'),
          h('span', {}, p.text),
          h('span', { class: 'spacer' }),
          h('span', { class: 'muted small' }, fmtDateTime(p.addedAt))),
        h('div', { class: 'muted small' }, p.teacher))))));
}
