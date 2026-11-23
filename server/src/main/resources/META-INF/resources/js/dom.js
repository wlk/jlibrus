// Safe DOM building: text always goes through textContent, never innerHTML.
import { lang } from './i18n.js';

export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'on') Object.entries(value).forEach(([event, fn]) => el.addEventListener(event, fn));
    else if (key === 'style') Object.assign(el.style, value);
    else if (key in el && typeof value !== 'string') el[key] = value;
    else el.setAttribute(key, value === true ? '' : String(value));
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

export function mount(container, ...children) {
  container.replaceChildren();
  append(container, children);
}

function locale() {
  return lang() === 'en' ? 'en-GB' : 'pl-PL';
}

export function fmtDate(iso, opts = { weekday: 'short', day: 'numeric', month: 'short' }) {
  if (!iso) return '';
  const date = new Date(iso.length === 10 ? iso + 'T12:00:00' : iso);
  return date.toLocaleDateString(locale(), opts);
}

export function fmtDateTime(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  return date.toLocaleString(locale(), { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function fmtTime(time) {
  return time ? time.substring(0, 5) : '';
}

export function isoDate(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function addDays(date, days) {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
}

export function toast(text) {
  const el = document.getElementById('toast');
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { el.hidden = true; }, 3500);
}

export function loading(container) {
  mount(container, h('p', { class: 'muted' }, '…'));
}

export function empty(text) {
  return h('p', { class: 'muted' }, text);
}
