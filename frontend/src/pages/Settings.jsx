import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, Field, PageHead, fieldErrors } from '../components/ui';
import { dateTime } from '../format';

function useSubmit(fn) {
  const [error, setError] = useState(null);
  const [ok, setOk] = useState(null);
  const [busy, setBusy] = useState(false);
  const run = async (e) => {
    e.preventDefault();
    setBusy(true); setError(null); setOk(null);
    try { setOk(await fn()); } catch (err) { setError(err); } finally { setBusy(false); }
  };
  return { run, error, ok, busy, fe: fieldErrors(error), top: error && !error.fieldErrors ? error.message : null };
}

function Profile() {
  const { user, setUser } = useAuth();
  const [f, setF] = useState({ fullName: user.fullName, phone: user.phone });
  const s = useSubmit(async () => { setUser(await api.updateProfile(f)); return 'Profile saved.'; });
  return (
    <form className="panel stack" onSubmit={s.run}>
      <div className="panel-head"><h2>Profile</h2></div>
      <Alert kind="success">{s.ok}</Alert>
      <Alert>{s.top}</Alert>
      <Field label="Full name" id="p-name" error={s.fe.fullName}>
        <input id="p-name" required value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} />
      </Field>
      <Field label="Phone" id="p-phone" error={s.fe.phone}>
        <input id="p-phone" type="tel" required value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
      </Field>
      <Field label="Email" id="p-email" hint="Email can't be changed">
        <input id="p-email" value={user.email} disabled />
      </Field>
      <Button type="submit" busy={s.busy}>Save profile</Button>
    </form>
  );
}

function Pin() {
  const { user, refresh } = useAuth();
  const [f, setF] = useState({ password: '', pin: '', confirm: '' });
  const mismatch = f.confirm && f.pin !== f.confirm;
  const s = useSubmit(async () => {
    await api.setPin({ password: f.password, pin: f.pin });
    await refresh();
    setF({ password: '', pin: '', confirm: '' });
    return user.pinSet ? 'PIN changed.' : 'PIN set. You can now send money, pay bills and withdraw.';
  });
  const digits = (k) => (e) => setF({ ...f, [k]: e.target.value.replace(/\D/g, '').slice(0, 6) });
  return (
    <form className="panel stack" onSubmit={s.run} id="pin">
      <div className="panel-head"><h2>{user.pinSet ? 'Change transaction PIN' : 'Set transaction PIN'}</h2></div>
      <p className="muted small">Your 4-6 digit PIN confirms every payment. Five wrong attempts lock it for 15 minutes.</p>
      <Alert kind="success">{s.ok}</Alert>
      <Alert>{s.top}</Alert>
      <div className="row-2">
        <Field label="New PIN" id="pin-new" error={s.fe.pin}>
          <input id="pin-new" className="pin-input" type="password" inputMode="numeric" autoComplete="new-password"
                 required minLength={4} value={f.pin} onChange={digits('pin')} />
        </Field>
        <Field label="Confirm PIN" id="pin-confirm" error={mismatch ? "PINs don't match" : null}>
          <input id="pin-confirm" className="pin-input" type="password" inputMode="numeric" autoComplete="new-password"
                 required minLength={4} value={f.confirm} onChange={digits('confirm')} />
        </Field>
      </div>
      <Field label="Account password" id="pin-pw" error={s.fe.password}>
        <input id="pin-pw" type="password" autoComplete="current-password" required value={f.password}
               onChange={(e) => setF({ ...f, password: e.target.value })} />
      </Field>
      <Button type="submit" busy={s.busy} disabled={mismatch || f.pin.length < 4}>{user.pinSet ? 'Change PIN' : 'Set PIN'}</Button>
    </form>
  );
}

function Password() {
  const [f, setF] = useState({ currentPassword: '', newPassword: '' });
  const s = useSubmit(async () => {
    await api.changePassword(f);
    setF({ currentPassword: '', newPassword: '' });
    return 'Password changed.';
  });
  return (
    <form className="panel stack" onSubmit={s.run}>
      <div className="panel-head"><h2>Password</h2></div>
      <Alert kind="success">{s.ok}</Alert>
      <Alert>{s.top}</Alert>
      <Field label="Current password" id="pw-cur" error={s.fe.currentPassword}>
        <input id="pw-cur" type="password" autoComplete="current-password" required value={f.currentPassword}
               onChange={(e) => setF({ ...f, currentPassword: e.target.value })} />
      </Field>
      <Field label="New password" id="pw-new" error={s.fe.newPassword} hint="At least 8 characters with a letter and a number">
        <input id="pw-new" type="password" autoComplete="new-password" required value={f.newPassword}
               onChange={(e) => setF({ ...f, newPassword: e.target.value })} />
      </Field>
      <Button type="submit" busy={s.busy}>Change password</Button>
    </form>
  );
}

export default function Settings() {
  const { user } = useAuth();
  const [params] = useSearchParams();
  return (
    <div className="narrow">
      <PageHead title="Settings" />
      {params.get('welcome') === '1' && !user.pinSet ? (
        <Alert kind="info">Welcome to Paylane. Set a transaction PIN below, then add a card to top up your wallet.</Alert>
      ) : null}
      <Pin />
      <Profile />
      <Password />
      <p className="muted small center">Member since {dateTime(user.createdAt)}</p>
    </div>
  );
}
