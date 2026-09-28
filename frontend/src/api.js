// Thin fetch wrapper around the Paylane REST API.
// VITE_API_BASE_URL is optional: leave it empty to call the same origin (/api/...),
// which is how it works behind nginx or an ingress.
const BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const TOKEN_KEY = 'paylane.token';

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed with status ${status}`);
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors || null;
  }
}

export const tokenStore = {
  get: () => {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  set: (t) => {
    try { localStorage.setItem(TOKEN_KEY, t); } catch { /* storage unavailable */ }
  },
  clear: () => {
    try { localStorage.removeItem(TOKEN_KEY); } catch { /* storage unavailable */ }
  },
};

let onUnauthorized = () => {};
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

// crypto.randomUUID only exists on HTTPS/localhost, so fall back for plain-HTTP load balancers.
export function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return 'k-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
}

async function request(method, path, { body, query, idempotencyKey } = {}) {
  const url = new URL(BASE + path, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
    });
  }
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

  let res;
  try {
    res = await fetch(url.toString(), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, { message: "Can't reach the server. Check your connection and try again." });
  }

  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = { message: text }; }
  }
  if (!res.ok) {
    if (res.status === 401 && token) onUnauthorized();
    throw new ApiError(res.status, data);
  }
  return data;
}

export const api = {
  // auth
  register: (b) => request('POST', '/api/auth/register', { body: b }),
  login: (b) => request('POST', '/api/auth/login', { body: b }),

  // user
  me: () => request('GET', '/api/users/me'),
  updateProfile: (b) => request('PUT', '/api/users/me', { body: b }),
  changePassword: (b) => request('POST', '/api/users/me/password', { body: b }),
  setPin: (b) => request('POST', '/api/users/me/pin', { body: b }),
  lookup: (query) => request('GET', '/api/users/lookup', { query: { query } }),

  // wallet
  wallet: () => request('GET', '/api/wallet'),
  topUp: (b, key) => request('POST', '/api/wallet/topup', { body: b, idempotencyKey: key }),
  withdraw: (b, key) => request('POST', '/api/wallet/withdraw', { body: b, idempotencyKey: key }),

  // transfers & history
  send: (b, key) => request('POST', '/api/transfers', { body: b, idempotencyKey: key }),
  transactions: (q) => request('GET', '/api/transactions', { query: q }),
  transaction: (ref) => request('GET', `/api/transactions/${encodeURIComponent(ref)}`),
  summary: () => request('GET', '/api/transactions/summary'),

  // payment methods
  paymentMethods: () => request('GET', '/api/payment-methods'),
  addCard: (b) => request('POST', '/api/payment-methods/cards', { body: b }),
  addBank: (b) => request('POST', '/api/payment-methods/banks', { body: b }),
  makeDefault: (id) => request('PATCH', `/api/payment-methods/${id}/default`),
  removeMethod: (id) => request('DELETE', `/api/payment-methods/${id}`),

  // requests
  createRequest: (b) => request('POST', '/api/requests', { body: b }),
  incoming: (q) => request('GET', '/api/requests/incoming', { query: q }),
  outgoing: (q) => request('GET', '/api/requests/outgoing', { query: q }),
  pendingCount: () => request('GET', '/api/requests/pending-count'),
  payRequest: (id, pin) => request('POST', `/api/requests/${id}/pay`, { body: { pin } }),
  declineRequest: (id) => request('POST', `/api/requests/${id}/decline`),
  cancelRequest: (id) => request('POST', `/api/requests/${id}/cancel`),

  // bills
  billers: () => request('GET', '/api/billers'),
  payBill: (b, key) => request('POST', '/api/bills/pay', { body: b, idempotencyKey: key }),

  // notifications
  notifications: (q) => request('GET', '/api/notifications', { query: q }),
  unreadCount: () => request('GET', '/api/notifications/unread-count'),
  markRead: (id) => request('POST', `/api/notifications/${id}/read`),
  markAllRead: () => request('POST', '/api/notifications/read-all'),

  // admin
  adminStats: () => request('GET', '/api/admin/stats'),
  adminUsers: (q) => request('GET', '/api/admin/users', { query: q }),
  adminSetStatus: (id, status) => request('PATCH', `/api/admin/users/${id}/status`, { body: { status } }),
  adminSetLimit: (id, dailyLimit) => request('PATCH', `/api/admin/users/${id}/limit`, { body: { dailyLimit } }),
  adminTransactions: (q) => request('GET', '/api/admin/transactions', { query: q }),
  adminReverse: (ref, reason) =>
    request('POST', `/api/admin/transactions/${encodeURIComponent(ref)}/reverse`, { body: { reason } }),
};
