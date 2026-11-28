// Small render helpers shared by several views.
import { api } from '../api.js';
import { fmtDate, fmtTime, h } from '../dom.js';
import { t } from '../i18n.js';

const TYPE_CLASS = { EVENT: 'warn', HOMEWORK: '', TIMETABLE_CHANGE: 'danger', FREE_DAY: 'ok', PARENT_MEETING: '' };

let typeLabels = null;
let entryTypesLoad = null;

// Entry types are a fixed server enum: fetched once per session; typeLabel reads the labels they carry.
export function entryTypes() {
  if (!entryTypesLoad) {
    const load = api.get('/api/entry-types').then((types) => {
      typeLabels = Object.fromEntries(types.map((x) => [x.id, x]));
      return Object.freeze(types);
    });
    load.catch(() => {
      if (entryTypesLoad === load) entryTypesLoad = null;
    });
    entryTypesLoad = load;
  }
  return entryTypesLoad;
}

export function forgetEntryTypes() {
  entryTypesLoad = null;
  typeLabels = null;
}

export function typeLabel(type, lang) {
  const entry = typeLabels && typeLabels[type];
  if (!entry) return type;
  return lang === 'en' ? entry.labelEn : entry.labelPl;
}

export function when(item) {
  if (item.allDay) {
    return item.start === item.end ? `${fmtDate(item.start)}, ${t('allDay')}` : `${fmtDate(item.start)} – ${fmtDate(item.end)}`;
  }
  return `${fmtDate(item.start)} ${fmtTime(item.startTime)}–${fmtTime(item.endTime)}`;
}

export function agendaItem(item, lang) {
  return h('li', {},
    h('div', { class: 'row' },
      h('span', { class: 'chip ' + (TYPE_CLASS[item.type] || '') }, typeLabel(item.type, lang)),
      h('strong', {}, item.title),
      h('span', { class: 'spacer' }),
      h('span', { class: 'muted small' }, when(item))),
    item.detail ? h('div', { class: 'muted small message-body' }, item.detail) : null,
    item.location ? h('div', { class: 'muted small' }, `${t('room')}: ${item.location}`) : null);
}

export function statusChip(status) {
  return status && status !== 'NORMAL'
    ? h('span', { class: 'chip ' + (status === 'CANCELLED' ? 'danger' : 'warn') }, t('status_' + status))
    : null;
}
