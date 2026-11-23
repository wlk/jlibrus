// Fetch wrapper: same-origin JSON, 401 means "show the login screen".
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message || code || String(status));
    this.status = status;
    this.code = code;
  }
}

let onUnauthorized = () => {};

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

async function request(method, path, body) {
  const options = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const response = await fetch(path, options);
  if (response.status === 401) {
    onUnauthorized();
    throw new ApiError(401, 'unauthorized');
  }
  if (response.status === 204) {
    return null;
  }
  const isJson = (response.headers.get('Content-Type') || '').includes('json');
  const payload = isJson ? await response.json() : await response.text();
  if (!response.ok) {
    throw new ApiError(response.status, payload && payload.error, payload && payload.message);
  }
  return payload;
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  del: (path) => request('DELETE', path)
};

export async function login(username, password) {
  const body = new URLSearchParams({ j_username: username, j_password: password });
  const response = await fetch('/j_security_check', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  });
  return response.ok;
}

export async function logout() {
  await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
  if (navigator.serviceWorker && navigator.serviceWorker.controller) {
    navigator.serviceWorker.controller.postMessage('logout');
  }
}
