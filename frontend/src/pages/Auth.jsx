import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { Alert, Button, Field, fieldErrors } from '../components/ui';

function AuthFrame({ title, lead, children }) {
  return (
    <div className="auth">
      <section className="auth-art" aria-hidden="true">
        <div className="auth-brand"><img src="/favicon.svg" alt="" width="34" height="34" /> Paylane</div>
        <div className="auth-ticket">
          <span className="auth-ticket-label">Sent to Maya</span>
          <span className="auth-ticket-amount">$48.20</span>
          <span className="auth-ticket-note">Dinner at Lucca's</span>
        </div>
        <p className="auth-line">Your money, moving the moment you need it to.</p>
      </section>
      <section className="auth-panel">
        <div className="auth-form">
          <h1>{title}</h1>
          <p className="muted">{lead}</p>
          {children}
        </div>
      </section>
    </div>
  );
}

export function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(form.email, form.password);
      nav('/');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthFrame title="Sign in" lead="Welcome back. Enter your email and password.">
      <form onSubmit={submit} className="stack">
        <Alert>{error?.message}</Alert>
        <Field label="Email" id="email">
          <input id="email" type="email" autoComplete="email" required value={form.email}
                 onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Password" id="password">
          <input id="password" type="password" autoComplete="current-password" required value={form.password}
                 onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        <Button type="submit" busy={busy}>Sign in</Button>
        <p className="muted center">New to Paylane? <Link to="/register">Create an account</Link></p>
      </form>
    </AuthFrame>
  );
}

export function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const fe = fieldErrors(error);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(form);
      nav('/settings?welcome=1');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthFrame title="Create your account" lead="It takes a minute. You'll get a wallet right away.">
      <form onSubmit={submit} className="stack">
        <Alert>{error && !error.fieldErrors ? error.message : null}</Alert>
        <Field label="Full name" id="fullName" error={fe.fullName}>
          <input id="fullName" autoComplete="name" required value={form.fullName} onChange={set('fullName')} />
        </Field>
        <Field label="Email" id="email" error={fe.email}>
          <input id="email" type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
        </Field>
        <Field label="Phone number" id="phone" error={fe.phone} hint="Digits only, for example +14155550123">
          <input id="phone" type="tel" autoComplete="tel" required value={form.phone} onChange={set('phone')} />
        </Field>
        <Field label="Password" id="password" error={fe.password} hint="At least 8 characters with a letter and a number">
          <input id="password" type="password" autoComplete="new-password" required value={form.password}
                 onChange={set('password')} />
        </Field>
        <Button type="submit" busy={busy}>Create account</Button>
        <p className="muted center">Already have an account? <Link to="/login">Sign in</Link></p>
      </form>
    </AuthFrame>
  );
}
