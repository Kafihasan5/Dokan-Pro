/**
 * The subset of firebase-admin/database the server code uses, over the Realtime Database REST API.
 * Supports: ref(path).get/set/update/remove/push/transaction, orderByChild/orderByValue/orderByKey,
 * startAt/endAt/equalTo/limitToFirst/limitToLast queries, and ServerValue.TIMESTAMP.
 */
import { accessToken, env } from './google.js';

export const ServerValue = { TIMESTAMP: { '.sv': 'timestamp' } };

const clean = (path) => String(path || '').split('/').filter(Boolean).join('/');

async function rest(path, { method = 'GET', body, params, headers = {} } = {}) {
  const qs = new URLSearchParams(params || {});
  const url = `${env().databaseURL}/${clean(path)}.json${qs.size ? `?${qs}` : ''}`;
  const res = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${await accessToken()}`, ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return res;
}

async function restJson(path, opts) {
  const res = await rest(path, opts);
  const text = await res.text();
  if (!res.ok) throw new Error(`Database ${opts?.method || 'GET'} ${clean(path)} failed (${res.status}): ${text.slice(0, 200)}`);
  return text ? JSON.parse(text) : null;
}

class Snapshot {
  constructor(key, value, order) {
    this.key = key;
    this._v = value ?? null;
    this._order = order;
  }
  val() {
    return this._v;
  }
  exists() {
    return this._v !== null;
  }
  numChildren() {
    return this._v && typeof this._v === 'object' ? Object.keys(this._v).length : 0;
  }
  /** Children in query order (REST returns an unordered object). */
  forEach(fn) {
    if (!this._v || typeof this._v !== 'object') return false;
    const entries = Object.entries(this._v);
    const by = this._order;
    const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
    entries.sort(([ka, va], [kb, vb]) => {
      if (by?.child) return cmp(va?.[by.child] ?? null, vb?.[by.child] ?? null) || cmp(ka, kb);
      if (by?.value) return cmp(va, vb) || cmp(ka, kb);
      return cmp(ka, kb);
    });
    for (const [k, v] of entries) if (fn(new Snapshot(k, v)) === true) return true;
    return false;
  }
}

// Firebase push ids: 8 time chars + 12 random chars, sortable by creation time.
const PUSH_CHARS = '-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz';
function pushId() {
  let now = Date.now();
  let id = '';
  for (let i = 0; i < 8; i++) {
    id = PUSH_CHARS.charAt(now % 64) + id;
    now = Math.floor(now / 64);
  }
  const rnd = crypto.getRandomValues(new Uint8Array(12));
  for (const r of rnd) id += PUSH_CHARS.charAt(r % 64);
  return id;
}

class Query {
  constructor(path, q = {}) {
    this.path = clean(path);
    this.q = q;
  }
  _with(extra) {
    return new Query(this.path, { ...this.q, ...extra });
  }
  orderByChild(child) {
    return this._with({ orderBy: child, order: { child } });
  }
  orderByValue() {
    return this._with({ orderBy: '$value', order: { value: true } });
  }
  orderByKey() {
    return this._with({ orderBy: '$key', order: {} });
  }
  startAt(v) {
    return this._with({ startAt: v });
  }
  endAt(v) {
    return this._with({ endAt: v });
  }
  equalTo(v) {
    return this._with({ equalTo: v });
  }
  limitToFirst(n) {
    return this._with({ limitToFirst: n });
  }
  limitToLast(n) {
    return this._with({ limitToLast: n });
  }
  async get() {
    const q = this.q;
    const params = {};
    const filtered = ['startAt', 'endAt', 'equalTo', 'limitToFirst', 'limitToLast'].some((k) => q[k] !== undefined);
    if (q.orderBy || filtered) params.orderBy = JSON.stringify(q.orderBy || '$key');
    for (const k of ['startAt', 'endAt', 'equalTo']) if (q[k] !== undefined) params[k] = JSON.stringify(q[k]);
    for (const k of ['limitToFirst', 'limitToLast']) if (q[k] !== undefined) params[k] = String(q[k]);
    const value = await restJson(this.path, { params });
    return new Snapshot(this.path.split('/').pop() || null, value, q.order);
  }
  once() {
    return this.get();
  }
}

class Reference extends Query {
  get key() {
    return this.path.split('/').pop() || null;
  }
  child(p) {
    return new Reference(`${this.path}/${p}`);
  }
  async set(value) {
    if (value === null || value === undefined) return this.remove();
    await restJson(this.path, { method: 'PUT', body: value, params: { print: 'silent' } });
  }
  async update(values) {
    // Multi-path PATCH at this location; null values delete.
    await restJson(this.path, { method: 'PATCH', body: values, params: { print: 'silent' } });
  }
  async remove() {
    await restJson(this.path, { method: 'DELETE', params: { print: 'silent' } });
  }
  push(value) {
    const ref = new Reference(`${this.path}/${pushId()}`);
    if (value === undefined) return ref;
    const p = ref.set(value).then(() => ref);
    ref.then = p.then.bind(p);
    return ref;
  }
  /** Optimistic transaction with ETags, like the SDK's retry loop. */
  async transaction(update) {
    for (let attempt = 0; attempt < 12; attempt++) {
      const res = await rest(this.path, { headers: { 'X-Firebase-ETag': 'true' } });
      const text = await res.text();
      if (!res.ok) throw new Error(`Database transaction read failed (${res.status})`);
      const etag = res.headers.get('ETag');
      const current = text ? JSON.parse(text) : null;
      const next = update(current === null ? null : structuredClone(current));
      if (next === undefined) return { committed: false, snapshot: new Snapshot(this.key, current) };
      const write = await rest(this.path, {
        method: next === null ? 'DELETE' : 'PUT',
        body: next === null ? undefined : next,
        headers: { 'if-match': etag },
      });
      if (write.status === 412) continue; // someone else wrote first; retry with fresh data
      const out = await write.text();
      if (!write.ok) throw new Error(`Database transaction write failed (${write.status}): ${out.slice(0, 200)}`);
      return { committed: true, snapshot: new Snapshot(this.key, next === null ? null : out ? JSON.parse(out) : next) };
    }
    throw new Error('Database transaction gave up after too many conflicts');
  }
}

const database = { ref: (path = '') => new Reference(path) };
export const getDatabase = () => database;
