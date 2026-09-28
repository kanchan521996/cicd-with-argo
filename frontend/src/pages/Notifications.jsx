import { useState } from 'react';
import { api } from '../api';
import { Alert, Button, Empty, Loading, PageHead, Pager } from '../components/ui';
import { dateTime } from '../format';
import { useLoad } from '../hooks';

export default function Notifications() {
  const [page, setPage] = useState(0);
  const list = useLoad(() => api.notifications({ page, size: 20 }), [page]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const items = list.data?.content || [];
  const anyUnread = items.some((n) => !n.read);

  const markOne = async (n) => {
    if (n.read) return;
    list.setData({ ...list.data, content: items.map((x) => (x.id === n.id ? { ...x, read: true } : x)) });
    try { await api.markRead(n.id); } catch (e) { setError(e.message); list.reload(); }
  };

  const markAll = async () => {
    setBusy(true);
    setError(null);
    try { await api.markAllRead(); await list.reload(); } catch (e) { setError(e.message); } finally { setBusy(false); }
  };

  return (
    <div className="narrow">
      <PageHead title="Notifications">
        {anyUnread ? <Button variant="ghost" className="btn-sm" busy={busy} onClick={markAll}>Mark all as read</Button> : null}
      </PageHead>
      <Alert>{error}</Alert>
      <section className="panel">
        {list.loading && !list.data ? <Loading /> : null}
        <Alert>{list.error?.message}</Alert>
        {list.data && items.length === 0 ? <Empty title="You're all caught up">Payments and requests will show up here.</Empty> : null}
        <ul className="note-list">
          {items.map((n) => (
            <li key={n.id}>
              <button className={`note ${n.read ? '' : 'note-unread'}`} onClick={() => markOne(n)}>
                <span className="note-dot" aria-hidden="true" />
                <span className="tx-main">
                  <span className="tx-title">{n.title}{!n.read ? <span className="sr-only"> (unread)</span> : null}</span>
                  <span className="note-msg">{n.message}</span>
                  <span className="tx-sub">{dateTime(n.createdAt)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
        <Pager page={page} totalPages={list.data?.totalPages} onChange={setPage} />
      </section>
    </div>
  );
}
