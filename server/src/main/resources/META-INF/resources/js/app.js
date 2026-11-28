// Bootstrap: language, login state, child switcher, navigation and routing.
import { api, logout, setUnauthorizedHandler } from './api.js';
import { h, mount, toast } from './dom.js';
import { applyStaticLabels, lang, setLang, t } from './i18n.js';
import { state, restoreChild, selectChild } from './state.js';
import { ROUTES, currentRoute, renderRoute } from './router.js';
import { renderLogin } from './views/login.js';
import { forgetEntryTypes } from './views/common.js';

const main = document.getElementById('main');
const nav = document.getElementById('nav');
const topbar = document.getElementById('topbar');
const childSelect = document.getElementById('child-select');
const langSelect = document.getElementById('lang-select');
const menuToggle = document.getElementById('menu-toggle');

// Asks the service worker to drop its offline API copies; showLogin is the one path out of a session (logout, any 401).
function forgetCachedData() {
  if (navigator.serviceWorker && navigator.serviceWorker.controller) {
    navigator.serviceWorker.controller.postMessage('logout');
  }
}

function showLogin() {
  forgetCachedData();
  forgetEntryTypes();
  topbar.hidden = true;
  nav.hidden = true;
  renderLogin(main, start);
}

function buildNav() {
  const active = currentRoute().name;
  mount(nav,
    ROUTES.map(([name]) => h('a', { href: '#/' + name, class: name === active ? 'active' : '' }, t(name))),
    h('a', { href: '#', on: { click: onLogout } }, t('logout')));
}

async function onLogout(event) {
  event.preventDefault();
  await logout();
  showLogin();
}

function buildChildSelect() {
  mount(childSelect, state.children.map((c) =>
    h('option', { value: c.id, selected: c.id === state.childId }, c.className ? `${c.name} (${c.className})` : c.name)));
  childSelect.hidden = state.children.length < 2;
}

async function start() {
  try {
    await api.get('/api/auth/me');
  } catch (e) {
    return;
  }
  topbar.hidden = false;
  nav.hidden = false;
  try {
    state.children = await api.get('/api/children');
  } catch (e) {
    state.children = [];
    toast(t('error') + ': ' + (e.message || e));
  }
  restoreChild();
  buildChildSelect();
  buildNav();
  if (!state.childId && currentRoute().name !== 'settings') {
    mount(main, h('div', { class: 'card' }, t('noChildren')));
    return;
  }
  renderRoute(main);
}

function closeMenuOnMobile() {
  if (window.matchMedia('(max-width: 760px)').matches) {
    nav.classList.add('closed');
    menuToggle.setAttribute('aria-expanded', 'false');
  }
}

setLang(lang());
langSelect.value = lang();
applyStaticLabels();
setUnauthorizedHandler(showLogin);
closeMenuOnMobile();

langSelect.addEventListener('change', () => {
  setLang(langSelect.value);
  applyStaticLabels();
  buildNav();
  renderRoute(main);
});

childSelect.addEventListener('change', () => {
  selectChild(childSelect.value);
  renderRoute(main);
});

menuToggle.addEventListener('click', () => {
  const closed = nav.classList.toggle('closed');
  menuToggle.setAttribute('aria-expanded', String(!closed));
});

window.addEventListener('hashchange', () => {
  buildNav();
  closeMenuOnMobile();
  renderRoute(main);
});

window.addEventListener('offline', () => toast(t('offline')));

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {
    // the app works without offline support
  });
}

start();
