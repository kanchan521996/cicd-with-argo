import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, newIdempotencyKey } from '../api';
import { Alert, Button, Empty, Field, Loading, PageHead, Receipt, fieldErrors } from '../components/ui';
import { money } from '../format';
import { useLoad } from '../hooks';

const QUICK = [20, 50, 100, 250];

export default function AddMoney() {
  const nav = useNavigate();
  const methods = useLoad(() => api.paymentMethods());
  const wallet = useLoad(() => api.wallet());
  const cards = (methods.data || []).filter((m) => m.type === 'CARD');
  const [cardId, setCardId] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const currency = wallet.data?.currency || 'USD';
  const fe = fieldErrors(error);

  useEffect(() => {
    if (!cardId && cards.length) setCardId(String((cards.find((c) => c.primary) || cards[0]).id));
  }, [cards, cardId]);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setResult(await api.topUp({ paymentMethodId: Number(cardId), amount }, newIdempotencyKey()));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  if (result) {
    return (
      <div className="narrow">
        <Receipt tx={result} title="Money added" onDone={() => (result.status === 'FAILED' ? setResult(null) : nav('/'))}
                 doneLabel={result.status === 'FAILED' ? 'Try again' : 'Back to home'} />
      </div>
    );
  }

  return (
    <div className="narrow">
      <PageHead title="Add money" />
      {methods.loading && !methods.data ? <Loading /> : null}
      {methods.data && cards.length === 0 ? (
        <div className="panel">
          <Empty title="Link a card first" action={<Link className="btn btn-primary" to="/methods">Add a card</Link>}>
            Money is added from a debit or credit card.
          </Empty>
        </div>
      ) : null}
      {cards.length ? (
        <form onSubmit={submit} className="panel stack">
          <Alert>{error && !error.fieldErrors ? error.message : null}</Alert>
          <Field label="From card" id="card">
            <select id="card" value={cardId} onChange={(e) => setCardId(e.target.value)}>
              {cards.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
            </select>
          </Field>
          <Field label={`Amount (${currency})`} id="amount" error={fe.amount}>
            <input id="amount" className="input-amount" type="number" inputMode="decimal" min="0.01" step="0.01"
                   required value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <div className="chips" role="group" aria-label="Quick amounts">
            {QUICK.map((q) => (
              <button type="button" key={q} className={`chip ${Number(amount) === q ? 'chip-on' : ''}`}
                      onClick={() => setAmount(String(q))}>{money(q, currency)}</button>
            ))}
          </div>
          <Button type="submit" busy={busy}>Add {amount ? money(amount, currency) : 'money'}</Button>
          <p className="field-msg">Test cards: any card ending 0002 is declined, 9995 has insufficient funds.</p>
        </form>
      ) : null}
    </div>
  );
}
