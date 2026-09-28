import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, newIdempotencyKey } from '../api';
import { useAuth } from '../auth';
import { Alert, Avatar, Button, Field, PageHead, PinModal, Receipt, fieldErrors } from '../components/ui';
import { money } from '../format';
import { useLoad } from '../hooks';

export default function Send() {
  const { user } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const wallet = useLoad(() => api.wallet());
  const [form, setForm] = useState({ recipient: params.get('to') || '', amount: params.get('amount') || '', note: '' });
  const [recipient, setRecipient] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [askPin, setAskPin] = useState(false);
  const [result, setResult] = useState(null);
  // One key per attempt: retries after a timeout reuse it, so the server never charges twice.
  const [idemKey, setIdemKey] = useState(newIdempotencyKey);
  const fe = fieldErrors(error);
  const currency = wallet.data?.currency || 'USD';

  const review = async (e) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      setRecipient(await api.lookup(form.recipient.trim()));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  const confirm = async (pin) => {
    const tx = await api.send({ recipient: form.recipient.trim(), amount: form.amount, note: form.note || null, pin }, idemKey);
    setAskPin(false);
    setResult(tx);
  };

  if (result) {
    return (
      <div className="narrow">
        <Receipt tx={result} title="Money sent" onDone={() => nav('/')} doneLabel="Back to home" />
        <Button variant="ghost" className="full" onClick={() => {
          setResult(null); setRecipient(null); setForm({ recipient: '', amount: '', note: '' }); setIdemKey(newIdempotencyKey());
        }}>Send to someone else</Button>
      </div>
    );
  }

  return (
    <div className="narrow">
      <PageHead title="Send money" />
      {!user?.pinSet ? <Alert kind="info">You need a transaction PIN to send money. <Link to="/settings">Set PIN</Link></Alert> : null}

      {!recipient ? (
        <form onSubmit={review} className="panel stack">
          <Alert>{error && !error.fieldErrors ? error.message : null}</Alert>
          <Field label="Recipient" id="recipient" hint="Their Paylane email or phone number" error={fe.recipient}>
            <input id="recipient" required value={form.recipient} autoComplete="off"
                   onChange={(e) => setForm({ ...form, recipient: e.target.value })} />
          </Field>
          <Field label={`Amount (${currency})`} id="amount" error={fe.amount}
                 hint={wallet.data ? `Balance ${money(wallet.data.balance, currency)}` : null}>
            <input id="amount" type="number" inputMode="decimal" min="0.01" step="0.01" required value={form.amount}
                   onChange={(e) => setForm({ ...form, amount: e.target.value })} className="input-amount" />
          </Field>
          <Field label="Note (optional)" id="note" error={fe.note}>
            <input id="note" maxLength={140} value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
          </Field>
          <Button type="submit" busy={busy}>Review</Button>
        </form>
      ) : (
        <div className="panel stack">
          <div className="confirm-recipient">
            <Avatar name={recipient.fullName} tone="in" />
            <div>
              <strong>{recipient.fullName}</strong>
              <span className="muted">{recipient.maskedEmail}, {recipient.maskedPhone}</span>
            </div>
          </div>
          <p className="confirm-amount">{money(form.amount, currency)}</p>
          {form.note ? <p className="muted center">“{form.note}”</p> : null}
          <Alert>{error?.message}</Alert>
          <Button onClick={() => setAskPin(true)} disabled={!user?.pinSet}>Send {money(form.amount, currency)}</Button>
          <Button variant="ghost" onClick={() => setRecipient(null)}>Change details</Button>
        </div>
      )}

      {askPin ? (
        <PinModal
          summary={<>Send <strong>{money(form.amount, currency)}</strong> to {recipient?.fullName}</>}
          onConfirm={confirm}
          onClose={() => setAskPin(false)}
        />
      ) : null}
    </div>
  );
}
