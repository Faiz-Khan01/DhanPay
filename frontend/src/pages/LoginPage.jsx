import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { AlertCircle, ArrowRight, Lock, Mail, Sparkles } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { extractApiError } from '../api/client';
import BrandMark from '../components/BrandMark';

export default function LoginPage() {
  const { isAuthenticated, user, login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  if (isAuthenticated) {
    return <Navigate to={user?.role === 'ROLE_ADMIN' ? '/admin' : '/dashboard'} replace />;
  }

  const validate = () => {
    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      return 'Email address is required.';
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      return 'Please enter a valid email address.';
    }
    if (!password) {
      return 'Password is required.';
    }
    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');

    const validationError = validate();
    if (validationError) {
      setErrorMsg(validationError);
      return;
    }

    setSubmitting(true);
    try {
      const authPayload = await login({ email, password });
      const target = authPayload?.user?.role === 'ROLE_ADMIN' ? '/admin' : '/dashboard';
      navigate(target, { replace: true });
    } catch (err) {
      const { message } = extractApiError(err, 'Unable to sign in. Please verify your credentials.');
      setErrorMsg(message);
    } finally {
      setSubmitting(false);
    }
  };

  const fillDemoCustomer = () => {
    setEmail('aarav@example.com');
    setPassword('Customer@1234');
    setErrorMsg('');
  };

  const fillDemoAdmin = () => {
    setEmail('admin@dhanpay.internal');
    setPassword('Admin@1234');
    setErrorMsg('');
  };

  return (
    <div className="min-h-screen flex flex-col justify-center px-4 py-10 sm:px-6">
      <div className="mx-auto w-full max-w-md">
        <div className="mb-7 flex justify-center">
          <BrandMark showTagline />
        </div>

        <div className="overflow-hidden rounded-[28px] border border-white/80 bg-white shadow-card">
          <div className="bg-[#05334D] px-7 py-6 text-white">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-emerald-300">
              Instant INR Ledger
            </div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight">Welcome back</h1>
            <p className="mt-1 text-sm text-slate-300">
              Sign in to access your INR wallet and instant ledger transfers.
            </p>
          </div>

          <div className="p-7">
            {errorMsg && (
              <div
                role="alert"
                className="mb-5 flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
              >
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
                <span>{errorMsg}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
              <div>
                <label
                  htmlFor="login-email"
                  className="block text-xs font-semibold uppercase tracking-wider text-slate-500"
                >
                  Email Address
                </label>
                <div className="relative mt-1.5">
                  <Mail className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    id="login-email"
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@example.com"
                    className="w-full rounded-2xl border border-slate-200 bg-slate-50/70 py-2.5 pl-10 pr-4 text-sm text-slate-900 placeholder-slate-400 transition focus:border-[#00C896] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]/25"
                  />
                </div>
              </div>

              <div>
                <label
                  htmlFor="login-password"
                  className="block text-xs font-semibold uppercase tracking-wider text-slate-500"
                >
                  Password
                </label>
                <div className="relative mt-1.5">
                  <Lock className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    id="login-password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
                    className="w-full rounded-2xl border border-slate-200 bg-slate-50/70 py-2.5 pl-10 pr-4 text-sm text-slate-900 placeholder-slate-400 transition focus:border-[#00C896] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]/25"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="mt-2 flex w-full items-center justify-center gap-2 rounded-full bg-[#00D084] px-5 py-3 text-sm font-semibold text-[#032539] shadow-md shadow-emerald-500/20 transition hover:bg-[#00C896] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submitting ? 'Signing in...' : 'Sign In to DhanPay'}
                {!submitting && <ArrowRight className="h-4 w-4" />}
              </button>
            </form>

            <div className="mt-5 rounded-2xl border border-slate-200/80 bg-slate-50/80 p-3.5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="text-xs text-slate-500">
                  <p className="flex items-center gap-1.5 font-semibold text-slate-700">
                    <Sparkles className="h-3.5 w-3.5 text-[#00C896]" />
                    Seeded Demo Accounts
                  </p>
                  <p className="mt-0.5 font-mono text-[11px] text-slate-500">
                    aarav@example.com • admin@dhanpay.internal
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={fillDemoCustomer}
                    className="shrink-0 rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200 transition hover:bg-emerald-100"
                  >
                    Customer
                  </button>
                  <button
                    type="button"
                    onClick={fillDemoAdmin}
                    className="shrink-0 rounded-full bg-[#05334D] px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-[#084566]"
                  >
                    Admin
                  </button>
                </div>
              </div>
            </div>

            <p className="mt-6 text-center text-sm text-slate-500">
              New to DhanPay?{' '}
              <Link to="/register" className="font-semibold text-[#05334D] hover:text-[#00C896] hover:underline">
                Create a free wallet
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
