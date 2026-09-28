import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth';
import Layout from './components/Layout';
import { Loading } from './components/ui';
import Activity from './pages/Activity';
import AddMoney from './pages/AddMoney';
import Admin from './pages/Admin';
import { Login, Register } from './pages/Auth';
import Bills from './pages/Bills';
import Dashboard from './pages/Dashboard';
import Methods from './pages/Methods';
import Notifications from './pages/Notifications';
import Requests from './pages/Requests';
import Send from './pages/Send';
import Settings from './pages/Settings';
import Withdraw from './pages/Withdraw';

function Protected({ children, role }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="boot"><Loading /></div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (role && user.role !== role) return <Navigate to="/" replace />;
  return children;
}

function PublicOnly({ children }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="boot"><Loading /></div>;
  if (user) return <Navigate to="/" replace />;
  return children;
}

function NotFound() {
  return (
    <div className="narrow center">
      <h1>Page not found</h1>
      <p className="muted">That page doesn't exist.</p>
      <a className="btn btn-primary" href="/">Go home</a>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<PublicOnly><Login /></PublicOnly>} />
      <Route path="/register" element={<PublicOnly><Register /></PublicOnly>} />
      <Route element={<Protected><Layout /></Protected>}>
        <Route index element={<Dashboard />} />
        <Route path="send" element={<Send />} />
        <Route path="add-money" element={<AddMoney />} />
        <Route path="withdraw" element={<Withdraw />} />
        <Route path="requests" element={<Requests />} />
        <Route path="bills" element={<Bills />} />
        <Route path="activity" element={<Activity />} />
        <Route path="methods" element={<Methods />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="settings" element={<Settings />} />
        <Route path="admin" element={<Protected role="ADMIN"><Admin /></Protected>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
