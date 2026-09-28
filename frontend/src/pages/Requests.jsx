import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Avatar, Badge, Button, Empty, Field, Loading, Modal, PageHead, Pager, PinModal, fieldErrors } from '../components/ui';
import { money, shortDate } from '../format';
import { useLoad } from '../hooks';

const STATUSES = ['', 'PENDING', 'PAID', 'DECLINED', 'CANCELLED'];

function NewRequest({ onClose, onCreated }) {
  const [form, setForm] = useState({ payer: '', amount: '', note: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const fe = fieldErrors(error);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.createRequest({ ...form, note: form.note || null });
      onCreated();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="Request money" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Alert>{error && !error.fieldErrors ? error.message : null}</Alert>
        <Field label="From (email or phone)" id="payer" error={fe.payer}>
          <input id="payer" required value={form.payer} onChange={set('payer')} autoComplete="off" />
        </Field>
        <Field label="Amount" id="req-amount" error={fe.amount}>
          <input id="req-amount" className="input-amount" type="number" inputMode="decimal" min="0.01" step="0.01"
                 required value={form.amount} onChange={set('amount')} />
        </Field>
        <Field label="What's it for?" id="req-note" error={fe.note} hint="Optional, up to 140 characters">
          <input id="req-note" maxLength={140} value={form.note} onChange={set('note')} />
        </Field>
        <Button type="submit" busy={busy}>Send request</Button>
      </form>
    </Modal>
  );
}

function RequestRow({ r, incoming, onPay, onAction }) {
  const [busy, setBusy] = useState(null);
  const name = incoming ? r.requesterName : r.payerName;
  const run = async (kind, fn) => {
    setBusy(kind);
    try { await fn(); } finally { setBusy(null); }
  };
  return (
    <li className="req-row">
      <Avatar name={name} tone={incoming ? 'out' : 'in'} />
      <div className="tx-main">
        <span className="tx-title">{incoming ? `${name} requested` : `You asked ${name}`}</span>
        <span className="tx-sub">{r.note ? `“${r.note}”, ` : ''}{shortDate(r.createdAt)}</span>
      </div>
      <div className="tx-side">
        <span className="amt">{money(r.amount, r.currency)}</span>
        {r.status !== 'PENDING' ? <Badge status={r.status} /> : null}
      </div>
      {r.status === 'PENDING' ? (
        <div className="req-actions">
          {incoming ? (
            <>
              <Button className="btn-sm" onClick={() => onPay(r)}>Pay</Button>
              <Button variant="ghost" className="btn-sm" busy={busy === 'decline'}
                      onClick={() => run('decline', () => onAction(() => api.declineRequest(r.id)))}>Decline</Button>
            </>
          ) : (
            <Button variant="ghost" className="btn-sm" busy={busy === 'cancel'}
                    onClick={() => run('cancel', () => onAction(() => api.cancelRequest(r.id)))}>Cancel request</Button>
          )}
        </div>
      ) : null}
    </li>
  );
}

export default function Requests() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'sent' ? 'sent' : 'incoming';
  const showNew = params.get('new') === '1';
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [paying, setPaying] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const list = useLoad(
    () => (tab === 'incoming' ? api.incoming({ status, page }) : api.outgoing({ status, page })),
    [tab, status, page],
  );

  const setParam = (k, v) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v); else next.delete(k);
    setParams(next, { replace: true });
  };
  const switchTab = (t) => { setPage(0); setMessage(null); setParam('tab', t === 'sent' ? 'sent' : null); };

  const action = async (fn) => {
    setError(null);
    try {
      await fn();
      await list.reload();
    } catch (e) {
      setError(e.message);
    }
  };

  const pay = async (pin) => {
    const r = paying;
    await api.payRequest(r.id, pin);
    setPaying(null);
    setMessage(`You paid ${r.requesterName} ${money(r.amount, r.currency)}.`);
    list.reload();
  };

  const items = list.data?.content || [];

  return (
    <div className="narrow wide">
      <PageHead title="Requests">
        <Button onClick={() => setParam('new', '1')}>Request money</Button>
      </PageHead>

      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'incoming'} className={`tab ${tab === 'incoming' ? 'tab-on' : ''}`}
                onClick={() => switchTab('incoming')}>To pay</button>
        <button role="tab" aria-selected={tab === 'sent'} className={`tab ${tab === 'sent' ? 'tab-on' : ''}`}
                onClick={() => switchTab('sent')}>Sent by you</button>
        <select className="tab-filter" aria-label="Filter by status" value={status}
                onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
          {STATUSES.map((s) => <option key={s} value={s}>{s ? s[0] + s.slice(1).toLowerCase() : 'All statuses'}</option>)}
        </select>
      </div>

      <Alert kind="success">{message}</Alert>
      <Alert>{error}</Alert>
      {tab === 'incoming' && !user?.pinSet ? (
        <Alert kind="info">You'll need a transaction PIN to pay requests. <Link to="/settings">Set PIN</Link></Alert>
      ) : null}

      <section className="panel">
        {list.loading && !list.data ? <Loading /> : null}
        <Alert>{list.error?.message}</Alert>
        {list.data && items.length === 0 ? (
          <Empty title={tab === 'incoming' ? 'Nobody has asked you for money' : "You haven't requested money yet"}>
            {tab === 'incoming' ? 'Requests from other people show up here.' : 'Split a bill or collect what you are owed.'}
          </Empty>
        ) : null}
        <ul className="tx-list">
          {items.map((r) => (
            <RequestRow key={r.id} r={r} incoming={tab === 'incoming'} onPay={setPaying} onAction={action} />
          ))}
        </ul>
        <Pager page={page} totalPages={list.data?.totalPages} onChange={setPage} />
      </section>

      {showNew ? (
        <NewRequest
          onClose={() => setParam('new', null)}
          onCreated={() => {
            setMessage('Request sent.');
            setPage(0);
            setParams({ tab: 'sent' }, { replace: true });
            if (tab === 'sent') list.reload();
          }}
        />
      ) : null}

      {paying ? (
        <PinModal
          title="Pay request"
          summary={<>Pay <strong>{money(paying.amount, paying.currency)}</strong> to {paying.requesterName}</>}
          onConfirm={pay}
          onClose={() => setPaying(null)}
        />
      ) : null}
    </div>
  );
}
