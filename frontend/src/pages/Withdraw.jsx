import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, newIdempotencyKey } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, Empty, Field, Loading, PageHead, PinModal, Receipt, fieldErrors } from '../components/ui';
import { money } from '../format';
import { useLoad } from '../hooks';

export default function Withdraw() {
  const { user } = useAuth();
  const nav = useNavigate();
  const methods = useLoad(() => api.paymentMethods());
  const wallet = useLoad(() => api.wallet());
  const banks = (methods.data || []).filter((m) => m.type === 'BANK_ACCOUNT');
  const [bankId, setBankId] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState(null);
  const [askPin, setAskPin] = useState(false);
  const [result, setResult] = useState(null);
  const [idemKey] = useState(newIdempotencyKey);
  const currency = wallet.data?.currency || 'USD';
  const fe = fieldErrors(error);

  useEffect(() => {
    if (!bankId && banks.length) setBankId(String((banks.find((b) => b.primary) || banks[0]).id));
  }, [banks, bankId]);

  const confirm = async (pin) => {
    const tx = await api.withdraw({ paymentMethodId: Number(bankId), amount, pin }, idemKey);
    setAskPin(false);
    setResult(tx);
  };

  const bank = banks.find((b) => String(b.id) === bankId);

  if (result) {
    return <div className="narrow"><Receipt tx={result} title="Withdrawal sent" onDone={() => nav('/')} doneLabel="Back to home" /></div>;
  }

  return (
    <div className="narrow">
      <PageHead title="Withdraw" />
      {!user?.pinSet ? <Alert kind="info">You need a transaction PIN to withdraw. <Link to="/settings">Set PIN</Link></Alert> : null}
      {methods.loading && !methods.data ? <Loading /> : null}
      {methods.data && banks.length === 0 ? (
        <div className="panel">
          <Empty title="Link a bank account first" action={<Link className="btn btn-primary" to="/methods">Add a bank account</Link>}>
            Withdrawals go to a bank account in your name.
          </Empty>
        </div>
      ) : null}
      {banks.length ? (
        <form className="panel stack" onSubmit={(e) => { e.preventDefault(); setError(null); setAskPin(true); }}>
          <Alert>{error?.message}</Alert>
          <Field label="To bank account" id="bank">
            <select id="bank" value={bankId} onChange={(e) => setBankId(e.target.value)}>
              {banks.map((b) => <option key={b.id} value={b.id}>{b.label}</option>)}
            </select>
          </Field>
          <Field label={`Amount (${currency})`} id="amount" error={fe.amount}
                 hint={wallet.data ? `Balance ${money(wallet.data.balance, currency)}` : null}>
            <input id="amount" className="input-amount" type="number" inputMode="decimal" min="0.01" step="0.01"
                   required value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Button type="submit" disabled={!user?.pinSet}>Withdraw</Button>
        </form>
      ) : null}
      {askPin ? (
        <PinModal
          summary={<>Withdraw <strong>{money(amount, currency)}</strong> to {bank?.label}</>}
          onConfirm={confirm}
          onClose={() => setAskPin(false)}
        />
      ) : null}
    </div>
  );
}
