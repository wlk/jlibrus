// Hash router: #/<view>/<params...>. Each view module exports render(container, params).
import { h, mount } from './dom.js';
import { t } from './i18n.js';
import { ApiError } from './api.js';
import * as dashboard from './views/dashboard.js';
import * as grades from './views/grades.js';
import * as attendance from './views/attendance.js';
import * as timetable from './views/timetable.js';
import * as calendar from './views/calendar.js';
import * as homework from './views/homework.js';
import * as messages from './views/messages.js';
import * as announcements from './views/announcements.js';
import * as behaviour from './views/behaviour.js';
import * as settings from './views/settings.js';

export const ROUTES = [
  ['dashboard', dashboard], ['grades', grades], ['attendance', attendance], ['timetable', timetable],
  ['calendar', calendar], ['homework', homework], ['messages', messages], ['announcements', announcements],
  ['behaviour', behaviour], ['settings', settings]
];

const VIEWS = Object.fromEntries(ROUTES);

export function currentRoute() {
  const parts = location.hash.replace(/^#\/?/, '').split('/').filter(Boolean).map(decodeURIComponent);
  const name = VIEWS[parts[0]] ? parts[0] : 'dashboard';
  return { name, params: parts.slice(1) };
}

export function go(path) {
  location.hash = '#/' + path;
}

export async function renderRoute(container) {
  const { name, params } = currentRoute();
  mount(container, h('p', { class: 'muted' }, t('loading')));
  try {
    await VIEWS[name].render(container, params);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return;
    mount(container, h('div', { class: 'card error' },
      h('strong', {}, t('error') + ': '),
      error.code === 'librus_module_unavailable' ? t('moduleUnavailable') : (error.message || String(error))));
  }
  container.focus({ preventScroll: true });
}
