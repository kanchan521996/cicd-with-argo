import { useState } from 'react';
import { api } from '../api';
import { Alert, Button, Empty, Loading, PageHead, Pager, TxDetail, TxRow } from '../components/ui';
import { STATUS_LABEL, TYPE_LABEL } from '../format';
import { useLoad } from '../hooks';

const EMPTY = { type: '', status: '', from: '', to: '', reference: '' };
const TX_STATUSES = ['PENDING', 'COMPLETED', 'FAILED', 'REVERSED'];

export default function Activity() {
  const [draft, setDraft] = useState(EMPTY);
  const [filters, setFilters] = useState(EMPTY);
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(null);
  const list = useLoad(() => api.transactions({ ...filters, reference: filters.reference.trim(), page, size: 20 }), [filters, page]);
  const set = (k) => (e) => setDraft({ ...draft, [k]: e.target.value });
  const active = Object.values(filters).some(Boolean);

  const apply = (e) => { e.preventDefault(); setPage(0); setFilters(draft); };
  const clear = () => { setDraft(EMPTY); setFilters(EMPTY); setPage(0); };
  const items = list.data?.content || [];

  return (
    <div className="narrow wide">
      <PageHead title="Activity" />
      <form className="filters" onSubmit={apply}>
        <label>
          <span>Type</span>
          <select value={draft.type} onChange={set('type')}>
            <option value="">All</option>
            {Object.entries(TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        <label>
          <span>Status</span>
          <select value={draft.status} onChange={set('status')}>
            <option value="">All</option>
            {TX_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </label>
        <label>
          <span>From</span>
          <input type="date" value={draft.from} onChange={set('from')} />
        </label>
        <label>
          <span>To</span>
          <input type="date" value={draft.to} onChange={set('to')} />
        </label>
        <label className="filters-ref">
          <span>Reference</span>
          <input value={draft.reference} onChange={set('reference')} placeholder="PL2026…" />
        </label>
        <div className="filters-actions">
          <Button type="submit" className="btn-sm">Apply</Button>
          {active ? <Button type="button" variant="ghost" className="btn-sm" onClick={clear}>Clear</Button> : null}
        </div>
      </form>

      <section className="panel">
        {list.loading && !list.data ? <Loading /> : null}
        <Alert>{list.error?.message}</Alert>
        {list.data && items.length === 0 ? (
          <Empty title={active ? 'No transactions match these filters' : 'No transactions yet'}>
            {active ? 'Try widening the dates or clearing filters.' : 'Add money to your wallet to get started.'}
          </Empty>
        ) : null}
        <ul className="tx-list">
          {items.map((tx) => <TxRow key={tx.id ?? tx.reference} tx={tx} onOpen={setOpen} />)}
        </ul>
        <Pager page={page} totalPages={list.data?.totalPages} onChange={setPage} />
      </section>
      {open ? <TxDetail tx={open} onClose={() => setOpen(null)} /> : null}
    </div>
  );
}
