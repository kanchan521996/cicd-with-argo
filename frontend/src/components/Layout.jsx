import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Avatar } from './ui';

const NAV = [
  { to: '/', label: 'Home', end: true, icon: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z' },
  { to: '/send', label: 'Send', icon: 'M4 12h14M13 6l6 6-6 6' },
  { to: '/requests', label: 'Requests', icon: 'M20 12H6M11 6l-6 6 6 6' },
  { to: '/bills', label: 'Bills', icon: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6' },
  { to: '/activity', label: 'Activity', icon: 'M4 6h16M4 12h16M4 18h10' },
  { to: '/methods', label: 'Cards & banks', icon: 'M3 6h18v12H3zM3 10h18' },
  { to: '/settings', label: 'Settings', icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19 12l2-1-2-4-2 1-2-2V4h-4v2L9 7 7 6l-2 4 2 1v2l-2 1 2 4 2-1 2 2v2h4v-2l2-2 2 1 2-4-2-1z' },
];

function Icon({ d }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={d} /></svg>
  );
}

export default function Layout() {
  const { user, logout } = useAuth();
  const [unread, setUnread] = useState(0);
  const [pending, setPending] = useState(0);
  const location = useLocation();

  useEffect(() => {
    let alive = true;
    const poll = () => {
      api.unreadCount().then((r) => alive && setUnread(r.unread)).catch(() => {});
      api.pendingCount().then((r) => alive && setPending(r.pending)).catch(() => {});
    };
    poll();
    const id = setInterval(poll, 20000);
    return () => { alive = false; clearInterval(id); };
  }, [location.pathname]);

  const items = user?.role === 'ADMIN'
    ? [...NAV, { to: '/admin', label: 'Admin', icon: 'M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z' }]
    : NAV;

  return (
    <div className="shell">
      <aside className="sidebar">
        <NavLink to="/" className="brand" aria-label="Paylane home">
          <img src="/favicon.svg" alt="" width="28" height="28" />
          <span>Paylane</span>
        </NavLink>
        <nav className="nav">
          {items.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="nav-link">
              <Icon d={n.icon} />
              <span>{n.label}</span>
              {n.to === '/requests' && pending > 0 ? <span className="count">{pending}</span> : null}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <Avatar name={user?.fullName} />
          <div className="who">
            <strong>{user?.fullName}</strong>
            <span>{user?.email}</span>
          </div>
        </div>
      </aside>

      <div className="main">
        <div className="topbar">
          <NavLink to="/" className="brand brand-mobile" aria-label="Paylane home">
            <img src="/favicon.svg" alt="" width="26" height="26" />
            <span>Paylane</span>
          </NavLink>
          <div className="topbar-actions">
            <NavLink to="/notifications" className="bell" aria-label={`Notifications, ${unread} unread`}>
              <Icon d="M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0" />
              {unread > 0 ? <span className="dot">{unread > 9 ? '9+' : unread}</span> : null}
            </NavLink>
            <button className="btn btn-ghost btn-sm" onClick={logout}>Sign out</button>
          </div>
        </div>
        <main className="content">
          <Outlet />
        </main>
      </div>

      <nav className="bottom-nav" aria-label="Primary">
        {items.slice(0, 5).map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className="bottom-link">
            <Icon d={n.icon} />
            <span>{n.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
