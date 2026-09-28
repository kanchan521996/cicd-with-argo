import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, newIdempotencyKey } from '../api';
import { useAuth } from '../auth';
import { Alert, Avatar, Button, Field, Loading, PageHead, PinModal, Receipt, fieldErrors } from '../components/ui';
import { money } from '../format';
import { useLoad } from '../hooks';

export default function Bills() {
  const { user } = useAuth();
  const billers = useLoad(() => api.billers());
  const wallet = useLoad(() => api.wallet());
  const [biller, setBiller] = useState(null);
  const [account, setAccount] = useState('');
  const [amount, setAmount] = useState('');
  const [askPin, setAskPin] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const [idemKey, setIdemKey] = useState(newIdempotencyKey);
  const currency = wallet.data?.currency || 'USD';
  const fe = fieldErrors(error);

  const groups = useMemo(() => {
    const g = {};
    (billers.data || []).forEach((b) => { (g[b.category] ||= []).push(b); });
    return Object.entries(g);
  }, [billers.data]);

  const reset = () => {
    setBiller(null); setAccount(''); setAmount(''); setResult(null); setError(null);
    setIdemKey(newIdempotencyKey());
    wallet.reload();
  };

  const confirm = async (pin) => {
    try {
      const tx = await api.payBill({ billerId: biller.id, accountReference: account.trim(), amount, pin }, idemKey);
      setAskPin(false);
      setResult(tx);
    } catch (err) {
      if (err.fieldErrors) { setAskPin(false); setError(err); return; }
      throw err;
    }
  };

  if (result) {
    return <div className="narrow"><Receipt tx={result} title={`Paid ${biller.name}`} onDone={reset} doneLabel="Pay another bill" /></div>;
  }

  return (
    <div className="narrow">
      <PageHead title="Pay a bill" />
      {!user?.pinSet ? <Alert kind="info">You need a transaction PIN to pay bills. <Link to="/settings">Set PIN</Link></Alert> : null}
      {billers.loading && !billers.data ? <Loading /> : null}
      <Alert>{billers.error?.message}</Alert>

      {!biller ? (
        groups.map(([cat, list]) => (
          <section key={cat} className="panel">
            <div className="panel-head"><h2>{cat}</h2></div>
            <ul className="biller-list">
              {list.map((b) => (
                <li key={b.id}>
                  <button className="tx-row" onClick={() => { setBiller(b); setError(null); }}>
                    <Avatar name={b.name} />
                    <span className="tx-main">
                      <span className="tx-title">{b.name}</span>
                      <span className="tx-sub">{b.accountLabel}</span>
                    </span>
                    <span className="chev" aria-hidden="true">›</span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))
      ) : (
        <form className="panel stack" onSubmit={(e) => { e.preventDefault(); setError(null); setAskPin(true); }}>
          <div className="biller-picked">
            <Avatar name={biller.name} />
            <div>
              <strong>{biller.name}</strong>
              <span className="muted">{biller.category}</span>
            </div>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setBiller(null)}>Change</button>
          </div>
          <Alert>{error && !error.fieldErrors ? error.message : null}</Alert>
          <Field label={biller.accountLabel} id="acct" error={fe.accountReference} hint="Letters, digits or dashes, as printed on your bill">
            <input id="acct" required minLength={4} maxLength={40} value={account} onChange={(e) => setAccount(e.target.value)} />
          </Field>
          <Field label={`Amount (${currency})`} id="bill-amount" error={fe.amount}
                 hint={wallet.data ? `Balance ${money(wallet.data.balance, currency)}` : null}>
            <input id="bill-amount" className="input-amount" type="number" inputMode="decimal" min="0.01" step="0.01"
                   required value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Button type="submit" disabled={!user?.pinSet}>Continue</Button>
        </form>
      )}

      {askPin ? (
        <PinModal
          title="Confirm bill payment"
          summary={<>Pay <strong>{money(amount, currency)}</strong> to {biller.name}<br /><span className="muted">{biller.accountLabel}: {account}</span></>}
          onConfirm={confirm}
          onClose={() => setAskPin(false)}
        />
      ) : null}
    </div>
  );
}
