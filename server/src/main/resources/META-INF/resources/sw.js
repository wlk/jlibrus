// Service worker: app shell offline, last API answers available offline (read-only), nothing else cached.
const VERSION = 'jlibrus-v4';
const SHELL = [
  '/', '/index.html', '/css/app.css', '/manifest.webmanifest',
  '/js/app.js', '/js/api.js', '/js/dom.js', '/js/i18n.js', '/js/state.js', '/js/router.js',
  '/js/views/login.js', '/js/views/common.js', '/js/views/dashboard.js', '/js/views/grades.js', '/js/views/attendance.js',
  '/js/views/timetable.js', '/js/views/calendar.js', '/js/views/homework.js', '/js/views/messages.js',
  '/js/views/announcements.js', '/js/views/behaviour.js', '/js/views/settings.js',
  '/icons/icon-192.png', '/icons/icon-32.png'
];
const API_CACHE = VERSION + '-api';

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => !k.startsWith(VERSION)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin) {
    return;
  }
  if (url.pathname.startsWith('/api/auth') || url.pathname.startsWith('/ical/') || url.pathname.startsWith('/q/')) {
    return;
  }
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request));
    return;
  }
  event.respondWith(staleWhileRevalidate(request));
});

// App shell: answer from cache at once, refresh the cached copy in the background so a deploy reaches
// every client on its next start.
async function staleWhileRevalidate(request) {
  const cache = await caches.open(VERSION);
  const cached = await cache.match(request);
  const network = fetch(request).then((response) => {
    if (response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  });
  if (cached) {
    network.catch(() => undefined);
    return cached;
  }
  return network;
}

async function networkFirst(request) {
  const cache = await caches.open(API_CACHE);
  try {
    const response = await fetch(request);
    if (response.ok) {
      cache.put(request, response.clone());
    } else if (response.status === 401) {
      await caches.delete(API_CACHE);
    }
    return response;
  } catch (error) {
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    throw error;
  }
}

self.addEventListener('message', (event) => {
  // Only pages of this app may control the cache.
  if (event.origin !== self.location.origin) {
    return;
  }
  if (event.data === 'logout') {
    caches.delete(API_CACHE);
  }
});
