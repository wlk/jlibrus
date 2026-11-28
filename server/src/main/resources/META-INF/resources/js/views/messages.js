import { api } from '../api.js';
import { empty, fmtDateTime, h, mount, toast } from '../dom.js';
import { t } from '../i18n.js';
import { childPath, go } from '../state.js';
import { groupBy } from './common.js';

// Routes: #/messages[/inbox|outbox[/page]] , #/messages/read/<mailbox>/<id> , #/messages/compose[/<replyToId>]
export async function render(container, params) {
  if (params[0] === 'read') return renderMessage(container, params[1], params[2]);
  if (params[0] === 'compose') return renderCompose(container, params[1]);
  const mailbox = params[0] === 'outbox' ? 'outbox' : 'inbox';
  const page = Math.max(1, Number(params[1]) || 1);
  const data = await api.get(childPath(`/messages?mailbox=${mailbox}&page=${page}`));
  mount(container,
    h('h1', {}, t('messages')),
    h('div', { class: 'tabs' },
      h('button', { class: mailbox === 'inbox' ? 'active' : '', on: { click: () => go('messages/inbox') } }, t('inbox')),
      h('button', { class: mailbox === 'outbox' ? 'active' : '', on: { click: () => go('messages/outbox') } }, t('outbox')),
      h('span', { class: 'spacer' }),
      h('button', { on: { click: () => go('messages/compose') } }, t('compose'))),
    h('div', { class: 'card' }, data.items.length === 0 ? empty(t('nothing')) : h('ul', { class: 'list' },
      data.items.map((m) => h('li', {},
        h('a', { href: `#/messages/read/${mailbox}/${encodeURIComponent(m.id)}`, class: m.unread && mailbox === 'inbox' ? 'unread' : '' },
          m.topic || '—'),
        h('div', { class: 'row small' },
          h('span', {}, m.counterpart),
          m.hasAttachments ? h('span', { class: 'chip' }, '📎') : null,
          h('span', { class: 'spacer' }),
          h('span', { class: 'muted' }, fmtDateTime(m.sentAt))),
        h('div', { class: 'muted small' }, m.preview))))),
    data.page * data.limit < data.total ? h('button', { class: 'secondary', on: { click: () => go(`messages/${mailbox}/${page + 1}`) } }, t('more')) : null);
}

async function renderMessage(container, mailbox, id) {
  const m = await api.get(childPath(`/messages/${encodeURIComponent(mailbox)}/${encodeURIComponent(id)}`));
  const attachments = m.attachments.map((a) => h('button', {
    class: 'secondary',
    on: {
      click: async () => {
        const link = await api.get(childPath(`/messages/${mailbox}/${encodeURIComponent(m.id)}/attachments/${encodeURIComponent(a.id)}`));
        window.open(link.url, '_blank', 'noopener');
      }
    }
  }, '📎 ' + a.filename));
  mount(container,
    h('p', {}, h('a', { href: '#/messages/' + mailbox }, t('back'))),
    h('div', { class: 'card' },
      h('h1', {}, m.topic),
      h('div', { class: 'row small muted' },
        h('span', {}, `${t('from')}: ${m.senderName || '—'}`),
        h('span', { class: 'spacer' }),
        h('span', {}, fmtDateTime(m.sentAt))),
      m.receivers.length ? h('div', { class: 'small muted' },
        `${t('to')}: ` + m.receivers.map((r) => r.name + (r.readAt ? ' ✓' : '')).join(', ')) : null,
      h('hr'),
      h('div', { class: 'message-body' }, m.body),
      attachments.length ? h('div', { class: 'row' }, attachments) : null,
      mailbox === 'inbox' && m.replyAllowed && m.senderAccountId
        ? h('p', {}, h('button', { on: { click: () => go(`messages/compose/${m.id}`) } }, t('reply')))
        : null));
  sessionStorage.setItem('jlibrus.reply', JSON.stringify({ id: m.id, to: m.senderAccountId, name: m.senderName, topic: m.topic }));
}

async function renderCompose(container, replyToId) {
  const reply = replyToId ? JSON.parse(sessionStorage.getItem('jlibrus.reply') || 'null') : null;
  const recipients = reply ? [] : await api.get(childPath('/messages/recipients'));
  const select = reply ? null : h('select', { id: 'recipients', multiple: true, size: 8, required: true },
    groupOptions(recipients));
  const topic = h('input', { id: 'topic', maxlength: 200, required: true, value: reply ? `Re: ${reply.topic}` : '' });
  const text = h('textarea', { id: 'text', maxlength: 10000, required: true });
  const submit = h('button', { type: 'submit' }, t('send'));

  async function onSubmit(event) {
    event.preventDefault();
    submit.disabled = true;
    const ids = reply ? [reply.to] : [...select.selectedOptions].map((o) => o.value);
    try {
      await api.post(childPath('/messages'), { recipientIds: ids, topic: topic.value, text: text.value, replyToId: reply ? reply.id : null });
      toast(t('sent'));
      go('messages/outbox');
    } catch (error) {
      toast(`${t('error')}: ${error.message}`);
      submit.disabled = false;
    }
  }

  mount(container,
    h('p', {}, h('a', { href: '#/messages' }, t('back'))),
    h('form', { class: 'card', on: { submit: onSubmit } },
      h('h1', {}, reply ? t('reply') : t('compose')),
      reply ? h('p', {}, `${t('to')}: ${reply.name}`) : [h('label', { for: 'recipients' }, t('recipients')), select],
      h('label', { for: 'topic' }, t('topic')), topic,
      h('label', { for: 'text' }, t('text')), text,
      h('p', {}, submit)));
}

function groupOptions(recipients) {
  return [...groupBy(recipients, (r) => r.groupName).entries()].map(([name, people]) =>
    h('optgroup', { label: name }, people.map((p) => h('option', { value: p.accountId }, p.name))));
}
