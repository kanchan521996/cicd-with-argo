import { useEffect, useRef, useState } from 'react';
import { STATUS_LABEL, TYPE_LABEL, dateTime, initials, money, shortDate, titleFor } from '../format';

export function Field({ label, error, hint, children, id }) {
  return (
    <div className={`field${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? <p className="field-msg error-text">{error}</p> : hint ? <p className="field-msg">{hint}</p> : null}
    </div>
  );
}

export function Button({ variant = 'primary', busy, children, className = '', ...rest }) {
  return (
    <button className={`btn btn-${variant} ${className}`} disabled={busy || rest.disabled} {...rest}>
      {busy ? <span className="spinner" aria-hidden="true" /> : null}
      <span>{children}</span>
    </button>
  );
}

export function Alert({ kind = 'error', children }) {
  if (!children) return null;
  return <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function Badge({ status }) {
  return <span className={`badge badge-${String(status).toLowerCase()}`}>{STATUS_LABEL[status] || status}</span>;
}

export function Avatar({ name, tone }) {
  return <span className={`avatar ${tone ? `avatar-${tone}` : ''}`} aria-hidden="true">{initials(name)}</span>;
}

export function Empty({ title, children, action }) {
  return (
    <div className="empty">
      <p className="empty-title">{title}</p>
      {children ? <p className="muted">{children}</p> : null}
      {action}
    </div>
  );
}

export function Loading({ label = 'Loading' }) {
  return <div className="loading"><span className="spinner" aria-hidden="true" /> {label}…</div>;
}

export function Modal({ title, onClose, children }) {
  const ref = useRef(null);
  useEffect(() => {
    const prev = document.activeElement;
    ref.current?.querySelector('input, button, select, textarea')?.focus();
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); prev?.focus?.(); };
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} ref={ref}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close">×</button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** Asks for the transaction PIN, then calls onConfirm(pin). */
export function PinModal({ title = 'Enter your PIN', summary, onConfirm, onClose }) {
  const [pin, setPin] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onConfirm(pin);
    } catch (err) {
      setError(err.message);
      setPin('');
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={submit} className="stack">
        {summary ? <div className="pin-summary">{summary}</div> : null}
        <Field label="Transaction PIN" id="pin" error={error}>
          <input id="pin" className="pin-input" type="password" inputMode="numeric" autoComplete="one-time-code"
                 maxLength={6} value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} />
        </Field>
        <Button type="submit" busy={busy} disabled={pin.length < 4}>Confirm</Button>
      </form>
    </Modal>
  );
}

export function Amount({ tx }) {
  const sign = tx.direction === 'CREDIT' ? '+' : tx.direction === 'DEBIT' ? '−' : '';
  const cls = tx.status === 'FAILED' ? 'amt-failed' : tx.direction === 'CREDIT' ? 'amt-in' : 'amt-out';
  return <span className={`amt ${cls}`}>{sign}{money(tx.amount, tx.currency)}</span>;
}

export function TxRow({ tx, onOpen }) {
  const tone = tx.direction === 'CREDIT' ? 'in' : 'out';
  return (
    <li>
      <button className="tx-row" onClick={() => onOpen?.(tx)}>
        <Avatar name={tx.counterparty} tone={tone} />
        <span className="tx-main">
          <span className="tx-title">{titleFor(tx)}</span>
          <span className="tx-sub">
            {TYPE_LABEL[tx.type]}{tx.description && tx.type === 'TRANSFER' ? `, “${tx.description}”` : ''}
          </span>
        </span>
        <span className="tx-side">
          <Amount tx={tx} />
          <span className="tx-sub">
            {tx.status !== 'COMPLETED' ? <Badge status={tx.status} /> : shortDate(tx.createdAt)}
          </span>
        </span>
      </button>
    </li>
  );
}

export function TxDetail({ tx, onClose }) {
  const rows = [
    ['Reference', <code key="r">{tx.reference}</code>],
    ['Type', TYPE_LABEL[tx.type]],
    ['Status', <Badge key="s" status={tx.status} />],
    [tx.direction === 'CREDIT' ? 'From' : 'To', tx.counterparty],
    tx.counterpartyDetail ? ['Details', tx.counterpartyDetail] : null,
    tx.description ? ['Note', tx.description] : null,
    tx.failureReason ? ['Reason', tx.failureReason] : null,
    tx.balanceAfter != null ? ['Balance after', money(tx.balanceAfter, tx.currency)] : null,
    ['Created', dateTime(tx.createdAt)],
    tx.completedAt ? ['Completed', dateTime(tx.completedAt)] : null,
  ].filter(Boolean);
  return (
    <Modal title="Transaction details" onClose={onClose}>
      <div className="detail-amount"><Amount tx={tx} /></div>
      <dl className="detail-list">
        {rows.map(([k, v]) => (
          <div key={k}><dt>{k}</dt><dd>{v}</dd></div>
        ))}
      </dl>
    </Modal>
  );
}

/** Success panel shown after any money movement. */
export function Receipt({ tx, title, onDone, doneLabel = 'Done' }) {
  const failed = tx.status === 'FAILED';
  return (
    <div className={`receipt ${failed ? 'receipt-failed' : ''}`}>
      <p className="receipt-title">{failed ? 'Payment failed' : title}</p>
      <p className="receipt-amount">{money(tx.amount, tx.currency)}</p>
      {failed ? <p className="receipt-reason">{tx.failureReason}</p> : <p className="muted">{titleFor(tx)}</p>}
      <dl className="detail-list">
        <div><dt>Reference</dt><dd><code>{tx.reference}</code></dd></div>
        <div><dt>Date</dt><dd>{dateTime(tx.completedAt || tx.createdAt)}</dd></div>
        {tx.balanceAfter != null ? <div><dt>New balance</dt><dd>{money(tx.balanceAfter, tx.currency)}</dd></div> : null}
      </dl>
      <Button onClick={onDone}>{doneLabel}</Button>
    </div>
  );
}

export function Pager({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <div className="pager">
      <Button variant="ghost" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</Button>
      <span className="muted">Page {page + 1} of {totalPages}</span>
      <Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</Button>
    </div>
  );
}

export function PageHead({ title, children }) {
  return (
    <header className="page-head">
      <h1>{title}</h1>
      {children ? <div className="page-head-actions">{children}</div> : null}
    </header>
  );
}

export function fieldErrors(err) {
  return err?.fieldErrors || {};
}
