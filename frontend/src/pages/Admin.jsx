import { useState } from 'react';
import { api } from '../api';
import { Alert, Badge, Button, Empty, Field, Loading, Modal, PageHead, Pager } from '../components/ui';
import { STATUS_LABEL, TYPE_LABEL, dateTime, money } from '../format';
import { useLoad } from '../hooks';

function Stats() {
  const s = useLoad(() => api.adminStats());
  if (s.loading && !s.data) return <Loading />;
  if (s.error) return <Alert>{s.error.message}</Alert>;
  const d = s.data;
  const cells = [
    ['Users', d.totalUsers, d.frozenUsers ? `${d.frozenUsers} frozen` : 'none frozen'],
    ['Money held in wallets', money(d.totalWalletBalance, d.currency)],
    ['Transactions today', d.transactionsToday, `${d.failedToday} failed`],
    ['Volume today', money(d.volumeToday, d.currency)],
    ['Volume this month', money(d.volumeThisMonth, d.currency)],
  ];
  return (
    <div className="stats">
      {cells.map(([label, value, sub]) => (
        <div key={label} className="stat">
          <span className="stat-label">{label}</span>
          <span className="stat-value">{value}</span>
          {sub ? <span className="tx-sub">{sub}</span> : null}
        </div>
      ))}
    </div>
  );
}

function LimitModal({ user, onClose, onSaved }) {
  const [value, setValue] = useState(String(user.dailyLimit ?? ''));
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    try { await api.adminSetLimit(user.id, value); onSaved(); } catch (err) { setError(err.message); } finally { setBusy(false); }
  };
  return (
    <Modal title={`Daily limit for ${user.fullName}`} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <Field label="Daily limit" id="lim" error={error} hint="Applies to transfers, withdrawals and bill payments">
          <input id="lim" type="number" min="0" step="0.01" required value={value} onChange={(e) => setValue(e.target.value)} />
        </Field>
        <Button type="submit" busy={busy}>Save limit</Button>
      </form>
    </Modal>
  );
}

function Users() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [limitFor, setLimitFor] = useState(null);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const list = useLoad(() => api.adminUsers({ search: query, page }), [query, page]);
  const items = list.data?.content || [];

  const toggle = async (u) => {
    const next = u.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    if (next === 'FROZEN' && !window.confirm(`Freeze ${u.fullName}? They won't be able to sign in or move money.`)) return;
    setBusyId(u.id);
    setError(null);
    try { await api.adminSetStatus(u.id, next); await list.reload(); } catch (e) { setError(e.message); } finally { setBusyId(null); }
  };

  return (
    <section className="panel">
      <form className="search" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(search.trim()); }}>
        <input aria-label="Search users" placeholder="Search by name, email or phone" value={search}
               onChange={(e) => setSearch(e.target.value)} />
        <Button type="submit" className="btn-sm">Search</Button>
      </form>
      <Alert>{error || list.error?.message}</Alert>
      {list.loading && !list.data ? <Loading /> : null}
      {list.data && items.length === 0 ? <Empty title="No users found" /> : null}
      {items.length ? (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Contact</th><th>Status</th><th className="num">Balance</th><th className="num">Daily limit</th><th>Joined</th><th /></tr>
            </thead>
            <tbody>
              {items.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.fullName}</strong>{u.role === 'ADMIN' ? <span className="badge badge-pending">Admin</span> : null}</td>
                  <td>{u.email}<br /><span className="muted">{u.phone}</span></td>
                  <td><Badge status={u.status} /></td>
                  <td className="num">{u.balance != null ? money(u.balance) : '—'}</td>
                  <td className="num">{u.dailyLimit != null ? money(u.dailyLimit) : '—'}</td>
                  <td>{dateTime(u.createdAt)}</td>
                  <td className="row-actions">
                    {u.role !== 'ADMIN' ? (
                      <>
                        <Button variant="ghost" className="btn-sm" onClick={() => setLimitFor(u)}>Limit</Button>
                        <Button variant={u.status === 'ACTIVE' ? 'danger' : 'ghost'} className="btn-sm" busy={busyId === u.id}
                                onClick={() => toggle(u)}>{u.status === 'ACTIVE' ? 'Freeze' : 'Unfreeze'}</Button>
                      </>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      <Pager page={page} totalPages={list.data?.totalPages} onChange={setPage} />
      {limitFor ? (
        <LimitModal user={limitFor} onClose={() => setLimitFor(null)} onSaved={() => { setLimitFor(null); list.reload(); }} />
      ) : null}
    </section>
  );
}

function ReverseModal({ tx, onClose, onDone }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try { await api.adminReverse(tx.reference, reason); onDone(); } catch (err) { setError(err.message); } finally { setBusy(false); }
  };
  return (
    <Modal title="Reverse transfer" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <div className="pin-summary">
          Return <strong>{money(tx.amount, tx.currency)}</strong> from {tx.receiver} to {tx.sender}
          <br /><code>{tx.reference}</code>
        </div>
        <Field label="Reason" id="rev-reason" error={error}>
          <input id="rev-reason" required maxLength={140} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Button type="submit" variant="danger" busy={busy}>Reverse transfer</Button>
      </form>
    </Modal>
  );
}

function Transactions() {
  const [filters, setFilters] = useState({ type: '', status: '', reference: '' });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(0);
  const [reversing, setReversing] = useState(null);
  const [message, setMessage] = useState(null);
  const list = useLoad(() => api.adminTransactions({ ...applied, page }), [applied, page]);
  const items = list.data?.content || [];
  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value });

  return (
    <section className="panel">
      <form className="filters" onSubmit={(e) => { e.preventDefault(); setPage(0); setApplied({ ...filters, reference: filters.reference.trim() }); }}>
        <label><span>Type</span>
          <select value={filters.type} onChange={set('type')}>
            <option value="">All</option>
            {Object.entries(TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        <label><span>Status</span>
          <select value={filters.status} onChange={set('status')}>
            <option value="">All</option>
            {['PENDING', 'COMPLETED', 'FAILED', 'REVERSED'].map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </label>
        <label className="filters-ref"><span>Reference</span>
          <input value={filters.reference} onChange={set('reference')} />
        </label>
        <div className="filters-actions"><Button type="submit" className="btn-sm">Apply</Button></div>
      </form>
      <Alert kind="success">{message}</Alert>
      <Alert>{list.error?.message}</Alert>
      {list.loading && !list.data ? <Loading /> : null}
      {list.data && items.length === 0 ? <Empty title="No transactions" /> : null}
      {items.length ? (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Reference</th><th>Type</th><th>From</th><th>To</th><th className="num">Amount</th><th>Status</th><th>Created</th><th /></tr>
            </thead>
            <tbody>
              {items.map((t) => (
                <tr key={t.id}>
                  <td><code>{t.reference}</code>{t.reversalOf ? <><br /><span className="muted">reverses {t.reversalOf}</span></> : null}</td>
                  <td>{TYPE_LABEL[t.type]}</td>
                  <td>{t.sender || '—'}</td>
                  <td>{t.receiver || '—'}</td>
                  <td className="num">{money(t.amount, t.currency)}</td>
                  <td><Badge status={t.status} />{t.failureReason ? <><br /><span className="muted">{t.failureReason}</span></> : null}</td>
                  <td>{dateTime(t.createdAt)}</td>
                  <td className="row-actions">
                    {t.type === 'TRANSFER' && t.status === 'COMPLETED' ? (
                      <Button variant="ghost" className="btn-sm" onClick={() => { setMessage(null); setReversing(t); }}>Reverse</Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      <Pager page={page} totalPages={list.data?.totalPages} onChange={setPage} />
      {reversing ? (
        <ReverseModal tx={reversing} onClose={() => setReversing(null)}
                      onDone={() => { setMessage(`Reversed ${reversing.reference}.`); setReversing(null); list.reload(); }} />
      ) : null}
    </section>
  );
}

export default function Admin() {
  const [tab, setTab] = useState('users');
  return (
    <div className="admin">
      <PageHead title="Admin" />
      <Stats />
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'users'} className={`tab ${tab === 'users' ? 'tab-on' : ''}`} onClick={() => setTab('users')}>Users</button>
        <button role="tab" aria-selected={tab === 'tx'} className={`tab ${tab === 'tx' ? 'tab-on' : ''}`} onClick={() => setTab('tx')}>Transactions</button>
      </div>
      {tab === 'users' ? <Users /> : <Transactions />}
    </div>
  );
}
