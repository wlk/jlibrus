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

// Number of the newest render; an older one still waiting for the API must not show its result.
let latest = 0;

/** Makes every render in flight stale, e.g. before the login screen replaces the view. */
export function supersedeRender() {
  latest += 1;
}

function errorCard(error) {
  return h('div', { class: 'card error' },
    h('strong', {}, t('error') + ': '),
    error.code === 'librus_module_unavailable' ? t('moduleUnavailable') : (error.message || String(error)));
}

// Each render fills its own element, shown only if no newer render started meanwhile; later re-renders of a
// superseded view (after an action) then land in a detached element instead of over the current view.
export async function renderRoute(container) {
  supersedeRender();
  const render = latest;
  const { name, params } = currentRoute();
  const view = h('div', { class: 'view' });
  mount(container, h('p', { class: 'muted' }, t('loading')));
  try {
    await VIEWS[name].render(view, params);
  } catch (error) {
    if (render !== latest || (error instanceof ApiError && error.status === 401)) return;
    mount(view, errorCard(error));
  }
  if (render !== latest) return;
  mount(container, view);
  container.focus({ preventScroll: true });
}
