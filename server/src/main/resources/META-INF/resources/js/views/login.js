import { login } from '../api.js';
import { h, mount } from '../dom.js';
import { t } from '../i18n.js';

export function renderLogin(container, onSuccess) {
  const user = h('input', { id: 'username', name: 'username', autocomplete: 'username', required: true });
  const pass = h('input', { id: 'password', name: 'password', type: 'password', autocomplete: 'current-password', required: true });
  const error = h('p', { class: 'error', role: 'alert', hidden: true }, t('loginFailed'));
  const submit = h('button', { type: 'submit' }, t('login'));

  async function onSubmit(event) {
    event.preventDefault();
    submit.disabled = true;
    error.hidden = true;
    const ok = await login(user.value, pass.value).catch(() => false);
    submit.disabled = false;
    if (ok) {
      pass.value = '';
      onSuccess();
    } else {
      error.hidden = false;
      pass.select();
    }
  }

  mount(container, h('form', { class: 'card login', on: { submit: onSubmit } },
    h('h1', {}, 'jlibrus'),
    h('label', { for: 'username' }, t('username')), user,
    h('label', { for: 'password' }, t('password')), pass,
    error,
    h('p', {}, submit)));
  user.focus();
}
