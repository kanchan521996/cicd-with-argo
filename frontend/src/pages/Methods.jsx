import { useState } from 'react';
import { api } from '../api';
import { Alert, Button, Empty, Field, Loading, Modal, PageHead, fieldErrors } from '../components/ui';
import { useLoad } from '../hooks';

function AddCard({ onClose, onDone }) {
  const [f, setF] = useState({ cardNumber: '', holderName: '', expiry: '', cvv: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const fe = fieldErrors(error);
  const set = (k, clean = (v) => v) => (e) => setF({ ...f, [k]: clean(e.target.value) });

  const formatCard = (v) => v.replace(/\D/g, '').slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ');
  const formatExpiry = (v) => {
    const d = v.replace(/\D/g, '').slice(0, 4);
    return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
  };

  const submit = async (e) => {
    e.preventDefault();
    const [mm, yy] = f.expiry.split('/');
    setBusy(true);
    setError(null);
    try {
      await api.addCard({
        cardNumber: f.cardNumber.replace(/\s/g, ''),
        holderName: f.holderName,
        expiryMonth: Number(mm),
        expiryYear: 2000 + Number(yy),
        cvv: f.cvv,
      });
      onDone('Card added.');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="Add a card" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Alert>{error && !error.fieldErrors ? error.message : null}</Alert>
        <Field label="Card number" id="cc" error={fe.cardNumber} hint="Test card: 4242 4242 4242 4242">
          <input id="cc" inputMode="numeric" autoComplete="cc-number" required value={f.cardNumber} onChange={set('cardNumber', formatCard)} />
        </Field>
        <Field label="Name on card" id="cc-name" error={fe.holderName}>
          <input id="cc-name" autoComplete="cc-name" required value={f.holderName} onChange={set('holderName')} />
        </Field>
        <div className="row-2">
          <Field label="Expiry (MM/YY)" id="cc-exp" error={fe.expiryMonth || fe.expiryYear}>
            <input id="cc-exp" inputMode="numeric" autoComplete="cc-exp" placeholder="MM/YY" required pattern="\d{2}/\d{2}"
                   value={f.expiry} onChange={set('expiry', formatExpiry)} />
          </Field>
          <Field label="CVV" id="cc-cvv" error={fe.cvv}>
            <input id="cc-cvv" inputMode="numeric" autoComplete="cc-csc" required maxLength={4}
                   value={f.cvv} onChange={set('cvv', (v) => v.replace(/\D/g, ''))} />
          </Field>
        </div>
        <p className="muted small">Only the brand and last four digits are stored. The CVV is never saved.</p>
        <Button type="submit" busy={busy}>Add card</Button>
      </form>
    </Modal>
  );
}

function AddBank({ onClose, onDone }) {
  const [f, setF] = useState({ bankName: '', holderName: '', accountNumber: '', routingCode: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const fe = fieldErrors(error);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.addBank({ ...f, accountNumber: f.accountNumber.replace(/\s/g, ''), routingCode: f.routingCode.trim() });
      onDone('Bank account linked.');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="Link a bank account" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Alert>{error && !error.fieldErrors ? error.message : null}</Alert>
        <Field label="Bank name" id="bk-name" error={fe.bankName}>
          <input id="bk-name" required value={f.bankName} onChange={set('bankName')} />
        </Field>
        <Field label="Account holder" id="bk-holder" error={fe.holderName}>
          <input id="bk-holder" required value={f.holderName} onChange={set('holderName')} />
        </Field>
        <Field label="Account number" id="bk-acct" error={fe.accountNumber} hint="6-17 digits. Numbers ending 0002 are declined in the test gateway.">
          <input id="bk-acct" inputMode="numeric" required value={f.accountNumber} onChange={set('accountNumber')} />
        </Field>
        <Field label="Routing / IFSC / sort code" id="bk-route" error={fe.routingCode}>
          <input id="bk-route" required value={f.routingCode} onChange={set('routingCode')} />
        </Field>
        <Button type="submit" busy={busy}>Link account</Button>
      </form>
    </Modal>
  );
}

export default function Methods() {
  const list = useLoad(() => api.paymentMethods());
  const [adding, setAdding] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const act = async (id, fn, msg) => {
    setBusyId(id);
    setError(null);
    setMessage(null);
    try {
      await fn();
      setMessage(msg);
      await list.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  };

  const done = (msg) => { setAdding(null); setMessage(msg); list.reload(); };
  const items = list.data || [];

  return (
    <div className="narrow">
      <PageHead title="Cards & banks">
        <Button variant="ghost" onClick={() => setAdding('bank')}>Link bank</Button>
        <Button onClick={() => setAdding('card')}>Add card</Button>
      </PageHead>
      <Alert kind="success">{message}</Alert>
      <Alert>{error}</Alert>

      <section className="panel">
        {list.loading && !list.data ? <Loading /> : null}
        <Alert>{list.error?.message}</Alert>
        {list.data && items.length === 0 ? (
          <Empty title="No cards or bank accounts yet">
            Add a card to top up your wallet, or link a bank account to withdraw.
          </Empty>
        ) : null}
        <ul className="method-list">
          {items.map((m) => (
            <li key={m.id} className="method">
              <span className={`method-mark ${m.type === 'CARD' ? 'is-card' : 'is-bank'}`} aria-hidden="true">
                {m.type === 'CARD' ? (m.brand || 'Card').slice(0, 4).toUpperCase() : 'BANK'}
              </span>
              <div className="tx-main">
                <span className="tx-title">{m.label}{m.primary ? <span className="badge badge-completed">Default</span> : null}</span>
                <span className="tx-sub">
                  {m.holderName}
                  {m.type === 'CARD' && m.expiryMonth ? `, expires ${String(m.expiryMonth).padStart(2, '0')}/${String(m.expiryYear).slice(-2)}` : ''}
                </span>
              </div>
              <div className="method-actions">
                {!m.primary ? (
                  <Button variant="ghost" className="btn-sm" busy={busyId === `d${m.id}`}
                          onClick={() => act(`d${m.id}`, () => api.makeDefault(m.id), 'Default updated.')}>Make default</Button>
                ) : null}
                <Button variant="danger" className="btn-sm" busy={busyId === `r${m.id}`}
                        onClick={() => {
                          if (window.confirm(`Remove ${m.label}?`)) act(`r${m.id}`, () => api.removeMethod(m.id), 'Removed.');
                        }}>Remove</Button>
              </div>
            </li>
          ))}
        </ul>
      </section>

      {adding === 'card' ? <AddCard onClose={() => setAdding(null)} onDone={done} /> : null}
      {adding === 'bank' ? <AddBank onClose={() => setAdding(null)} onDone={done} /> : null}
    </div>
  );
}
