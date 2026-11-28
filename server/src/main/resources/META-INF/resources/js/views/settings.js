import { api } from '../api.js';
import { empty, h, mount, toast } from '../dom.js';
import { lang, t } from '../i18n.js';
import { state } from '../state.js';
import { action, entryTypes, typeLabel } from './common.js';

const FREQUENCIES = ['DAILY', 'WEEKLY', 'MONTHLY'];

export async function render(container) {
  const [feeds, subscriptions, types, schedule] = await Promise.all([
    api.get('/api/feeds'), api.get('/api/subscriptions'), entryTypes(), api.get('/api/schedule')]);
  const rerender = () => render(container);
  mount(container,
    h('h1', {}, t('settings')),
    h('div', { class: 'card' },
      h('h2', {}, t('feeds')),
      h('p', { class: 'muted small' }, t('feedHelp')),
      feeds.length === 0 ? empty(t('nothing')) : feeds.map((f) => feedCard(f, types, rerender)),
      h('h3', {}, t('newFeed')),
      feedForm(null, types.filter((x) => x.calendar), rerender)),
    h('div', { class: 'card' },
      h('h2', {}, t('subscriptions')),
      h('p', { class: 'muted small' }, t('subscriptionHelp')),
      h('p', { class: 'small' }, `${t('schedule')}: ${t('DAILY')} ${schedule.daily} · ${t('WEEKLY')} ${schedule.weekly} · `
        + `${t('MONTHLY')} ${schedule.monthly} (${schedule.timezone})`),
      subscriptions.length === 0 ? empty(t('nothing')) : subscriptions.map((s) => subscriptionCard(s, types, rerender)),
      h('h3', {}, t('add')),
      subscriptionForm(null, types, rerender)),
    h('div', { class: 'card' },
      h('button', { class: 'secondary', on: { click: refresh } }, t('refresh'))));
}

function refresh(event) {
  return action(event.currentTarget, async () => {
    await api.post('/api/refresh');
    toast(t('refreshed'));
  });
}

// Click handler that asks first, then runs fn as an action of the clicked button.
function confirmed(question, fn) {
  return (event) => {
    if (confirm(question)) action(event.currentTarget, fn);
  };
}

function typeChecks(types, selected) {
  return h('div', { class: 'checks' }, types.map((x) => h('label', {},
    h('input', { type: 'checkbox', name: 'type', value: x.id, checked: selected.includes(x.id) }), typeLabel(x.id, lang()))));
}

function childChecks(selected) {
  return h('div', { class: 'checks' }, state.children.map((c) => h('label', {},
    h('input', { type: 'checkbox', name: 'child', value: c.id, checked: selected.includes(c.id) }), c.name)),
  h('span', { class: 'muted small' }, `(${t('allChildren')} = ∅)`));
}

function checked(form, name) {
  return [...form.querySelectorAll(`input[name="${name}"]:checked`)].map((i) => i.value);
}

function feedCard(feed, types, rerender) {
  const url = location.origin + feed.path;
  const input = h('input', { value: url, readonly: true, 'aria-label': 'URL' });
  return h('div', { class: 'card' },
    h('div', { class: 'row' }, h('strong', {}, feed.name), h('span', { class: 'spacer' }),
      h('span', { class: 'muted small' }, feed.childIds.length ? feed.childIds.join(', ') : t('allChildren'))),
    h('div', { class: 'copy' }, input, h('button', {
      class: 'secondary', type: 'button',
      on: { click: (e) => action(e.currentTarget, async () => {
        await navigator.clipboard.writeText(url);
        toast(t('copied'));
      }) }
    }, t('copy'))),
    h('p', { class: 'small' }, h('a', { href: url.replace(/^https?:/, 'webcal:') }, 'webcal://')),
    h('details', {}, h('summary', {}, t('save')), feedForm(feed, types.filter((x) => x.calendar), rerender)),
    h('div', { class: 'row' },
      h('button', { class: 'secondary', on: { click: confirmed(t('confirmReset'), async () => {
        await api.post(`/api/feeds/${feed.id}/reset-token`);
        await rerender();
      }) } }, t('resetToken')),
      h('button', { class: 'danger', on: { click: confirmed(t('confirmDelete'), async () => {
        await api.del(`/api/feeds/${feed.id}`);
        await rerender();
      }) } }, t('delete'))));
}

function feedForm(feed, calendarTypes, rerender) {
  const name = h('input', { name: 'name', required: true, maxlength: 200, value: feed ? feed.name : 'Librus' });
  const submit = h('button', { type: 'submit' }, feed ? t('save') : t('add'));
  const form = h('form', {
    on: { submit: (event) => {
      event.preventDefault();
      const body = { name: name.value, childIds: checked(form, 'child'), types: checked(form, 'type') };
      action(submit, async () => {
        await (feed ? api.put(`/api/feeds/${feed.id}`, body) : api.post('/api/feeds', body));
        await rerender();
      });
    } }
  },
  h('label', {}, t('name')), name,
  h('label', {}, t('children')), childChecks(feed ? feed.childIds : []),
  h('label', {}, t('types')), typeChecks(calendarTypes, feed ? feed.types : calendarTypes.filter((x) => x.id !== 'LESSON').map((x) => x.id)),
  h('p', {}, submit));
  return form;
}

function subscriptionCard(sub, types, rerender) {
  return h('div', { class: 'card' },
    h('div', { class: 'row' },
      h('strong', {}, sub.email.value),
      sub.enabled ? null : h('span', { class: 'chip' }, '✕'),
      h('span', { class: 'spacer' }),
      h('span', { class: 'muted small' }, sub.frequencies.map((f) => t(f)).join(', '))),
    h('details', {}, h('summary', {}, t('save')), subscriptionForm(sub, types, rerender)),
    h('div', { class: 'row' },
      FREQUENCIES.filter((f) => sub.frequencies.includes(f)).map((f) => h('button', {
        class: 'secondary',
        on: { click: (e) => action(e.currentTarget, async () => {
          await api.post(`/api/subscriptions/${sub.id}/send?frequency=${f}`);
          toast(`${t('sendTest')}: ${sub.email.value}`);
        }) }
      }, `${t('sendTest')} (${t(f)})`)),
      h('button', { class: 'danger', on: { click: confirmed(t('confirmDelete'), async () => {
        await api.del(`/api/subscriptions/${sub.id}`);
        await rerender();
      }) } }, t('delete'))));
}

function subscriptionForm(sub, types, rerender) {
  const email = h('input', { type: 'email', name: 'email', required: true, maxlength: 254, value: sub ? sub.email.value : '' });
  const enabled = h('input', { type: 'checkbox', name: 'enabled', checked: sub ? sub.enabled : true });
  const submit = h('button', { type: 'submit' }, sub ? t('save') : t('add'));
  const form = h('form', {
    on: { submit: (event) => {
      event.preventDefault();
      const body = { email: email.value, frequencies: checked(form, 'frequency'), types: checked(form, 'type'),
        childIds: checked(form, 'child'), enabled: enabled.checked };
      action(submit, async () => {
        await (sub ? api.put(`/api/subscriptions/${sub.id}`, body) : api.post('/api/subscriptions', body));
        await rerender();
      });
    } }
  },
  h('label', {}, t('email')), email,
  h('label', {}, t('frequencies')),
  h('div', { class: 'checks' }, FREQUENCIES.map((f) => h('label', {},
    h('input', { type: 'checkbox', name: 'frequency', value: f, checked: sub ? sub.frequencies.includes(f) : f === 'DAILY' }), t(f)))),
  h('label', {}, t('children')), childChecks(sub ? sub.childIds : []),
  h('label', {}, t('types')), typeChecks(types, sub ? sub.types : types.filter((x) => x.id !== 'LESSON').map((x) => x.id)),
  h('div', { class: 'checks' }, h('label', {}, enabled, t('enabled'))),
  h('p', {}, submit));
  return form;
}
