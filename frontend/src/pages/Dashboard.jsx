import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Empty, Loading, TxDetail, TxRow } from '../components/ui';
import { money } from '../format';
import { useLoad } from '../hooks';

const ACTIONS = [
  { to: '/add-money', label: 'Add money' },
  { to: '/send', label: 'Send' },
  { to: '/requests?new=1', label: 'Request' },
  { to: '/bills', label: 'Pay a bill' },
  { to: '/withdraw', label: 'Withdraw' },
];

export default function Dashboard() {
  const { user } = useAuth();
  const wallet = useLoad(() => api.wallet());
  const summary = useLoad(() => api.summary());
  const recent = useLoad(() => api.transactions({ size: 6 }));
  const incoming = useLoad(() => api.incoming({ status: 'PENDING', size: 3 }));
  const [open, setOpen] = useState(null);

  const w = wallet.data;
  const firstName = user?.fullName?.split(' ')[0];

  return (
    <div className="dash">
      <p className="greeting">Hi {firstName}</p>

      {!user?.pinSet ? (
        <Alert kind="info">
          Set a transaction PIN before you send money, pay bills or withdraw. <Link to="/settings">Set PIN</Link>
        </Alert>
      ) : null}

      <section className="slab" aria-label="Wallet balance">
        <div className="slab-top">
          <span className="slab-label">Available balance</span>
          {w?.status === 'FROZEN' ? <span className="badge badge-frozen">Frozen</span> : null}
        </div>
        <p className="slab-balance">{w ? money(w.balance, w.currency) : '—'}</p>
        {w ? (
          <div className="slab-limit">
            <div className="limit-bar" role="progressbar" aria-label="Daily limit used"
                 aria-valuemin={0} aria-valuemax={Number(w.dailyLimit)} aria-valuenow={Number(w.spentToday)}>
              <span style={{ width: `${Math.min(100, (Number(w.spentToday) / Math.max(1, Number(w.dailyLimit))) * 100)}%` }} />
            </div>
            <span>{money(w.remainingToday, w.currency)} left of today's {money(w.dailyLimit, w.currency)} limit</span>
          </div>
        ) : null}
        <nav className="slab-actions" aria-label="Quick actions">
          {ACTIONS.map((a) => <Link key={a.to} to={a.to} className="slab-action">{a.label}</Link>)}
        </nav>
      </section>

      <div className="dash-grid">
        <section className="panel">
          <div className="panel-head">
            <h2>Recent activity</h2>
            <Link to="/activity">See all</Link>
          </div>
          {recent.loading && !recent.data ? <Loading /> : null}
          <Alert>{recent.error?.message}</Alert>
          {recent.data && recent.data.content.length === 0 ? (
            <Empty title="No transactions yet" action={<Link className="btn btn-primary" to="/add-money">Add money</Link>}>
              Add money from a card to start paying people.
            </Empty>
          ) : null}
          {recent.data?.content.length ? (
            <ul className="tx-list">
              {recent.data.content.map((tx) => <TxRow key={tx.id} tx={tx} onOpen={setOpen} />)}
            </ul>
          ) : null}
        </section>

        <aside className="dash-side">
          <section className="panel">
            <h2>This month</h2>
            {summary.data ? (
              <dl className="month">
                <div><dt>Money in</dt><dd className="amt-in">{money(summary.data.receivedThisMonth, summary.data.currency)}</dd></div>
                <div><dt>Money out</dt><dd>{money(summary.data.spentThisMonth, summary.data.currency)}</dd></div>
              </dl>
            ) : <Loading />}
          </section>

          <section className="panel">
            <div className="panel-head">
              <h2>Waiting on you</h2>
              <Link to="/requests">Requests</Link>
            </div>
            {incoming.data?.content.length ? (
              <ul className="mini-list">
                {incoming.data.content.map((r) => (
                  <li key={r.id}>
                    <span><strong>{r.requesterName}</strong> asked for {money(r.amount, r.currency)}</span>
                    <Link to="/requests" className="btn btn-ghost btn-sm">Review</Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted">No one is waiting on a payment from you.</p>
            )}
          </section>
        </aside>
      </div>

      {open ? <TxDetail tx={open} onClose={() => setOpen(null)} /> : null}
    </div>
  );
}
