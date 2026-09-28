export function money(amount, currency = 'USD') {
  const n = Number(amount ?? 0);
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(n);
  } catch {
    return `${currency} ${n.toFixed(2)}`;
  }
}

export function dateTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit',
  });
}

export function shortDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const today = new Date();
  const sameDay = d.toDateString() === today.toDateString();
  if (sameDay) return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export const TYPE_LABEL = {
  TOPUP: 'Money added',
  WITHDRAWAL: 'Withdrawal',
  TRANSFER: 'Transfer',
  BILL_PAYMENT: 'Bill payment',
  REVERSAL: 'Reversal',
};

export const STATUS_LABEL = {
  PENDING: 'Pending',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  REVERSED: 'Reversed',
  PAID: 'Paid',
  DECLINED: 'Declined',
  CANCELLED: 'Cancelled',
  ACTIVE: 'Active',
  FROZEN: 'Frozen',
};

export function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?';
}

export function titleFor(tx) {
  if (tx.type === 'TRANSFER') return tx.direction === 'CREDIT' ? `From ${tx.counterparty}` : `To ${tx.counterparty}`;
  if (tx.type === 'REVERSAL') return tx.direction === 'CREDIT' ? `Refund from ${tx.counterparty}` : `Reversed to ${tx.counterparty}`;
  if (tx.type === 'TOPUP') return `Added from ${tx.counterparty}`;
  if (tx.type === 'WITHDRAWAL') return `Withdrew to ${tx.counterparty}`;
  if (tx.type === 'BILL_PAYMENT') return tx.counterparty;
  return tx.counterparty;
}
