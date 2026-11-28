// Small render helpers shared by several views.
import { api, ApiError } from '../api.js';
import { addDays, fmtDate, fmtTime, h, isoDate, toast } from '../dom.js';
import { t } from '../i18n.js';

const TYPE_CLASS = { EVENT: 'warn', HOMEWORK: '', TIMETABLE_CHANGE: 'danger', FREE_DAY: 'ok', PARENT_MEETING: '' };
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;

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

// Runs what a button started: the button is disabled until fn settles and a failure becomes a toast, never an
// unhandled rejection or a button stuck disabled. A 401 stays silent: the login screen is already shown (as in
// renderRoute).
export async function action(button, fn) {
  button.disabled = true;
  try {
    await fn();
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) {
      toast(`${t('error')}: ${error.message || error}`);
    }
  } finally {
    button.disabled = false;
  }
}

// A YYYY-MM-DD route parameter as that day at local noon (safe from DST shifts); now when absent or malformed.
export function dateParam(param) {
  return param && ISO_DAY.test(param) ? new Date(param + 'T12:00:00') : new Date();
}

// Map of key -> items with that key, keys in first-seen order, items in input order.
export function groupBy(items, keyOf) {
  return items.reduce((groups, item) => {
    const key = keyOf(item);
    return groups.set(key, [...(groups.get(key) || []), item]);
  }, new Map());
}

// Previous / current / next links of a view paged by date: #/<route>[/<YYYY-MM-DD>].
export function datePager(route, start, days, currentLabel) {
  const link = (text, href) => h('a', { class: 'button secondary', href }, text);
  return h('div', { class: 'tabs' },
    link(t('prevWeek'), `#/${route}/${isoDate(addDays(start, -days))}`),
    link(currentLabel, `#/${route}`),
    link(t('nextWeek'), `#/${route}/${isoDate(addDays(start, days))}`));
}

export function statusChip(status) {
  return status && status !== 'NORMAL'
    ? h('span', { class: 'chip ' + (status === 'CANCELLED' ? 'danger' : 'warn') }, t('status_' + status))
    : null;
}
