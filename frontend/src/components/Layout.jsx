import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  Bell,
  Check,
  CheckCircle2,
  Code,
  Copy,
  HelpCircle,
  History,
  KeyRound,
  LayoutDashboard,
  LogOut,
  Menu,
  Repeat,
  Send,
  ShieldAlert,
  ShieldCheck,
  Wallet as WalletIcon,
  X,
} from 'lucide-react';
import client, { TOKEN_STORAGE_KEY, extractApiError } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { getInitials } from '../utils/formatters';
import BrandMark from './BrandMark';

const CUSTOMER_NAV_ITEMS = [
  { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { to: '/send-money', label: 'Send Money', icon: Send },
  { to: '/history', label: 'Transactions', icon: History },
  { to: '/escrow', label: 'Escrow', icon: ShieldCheck },
  { to: '/recurring', label: 'Recurring', icon: Repeat },
  { to: '/developer', label: 'Developer', icon: Code },
];

const PAGE_TITLES = {
  '/dashboard': 'Overview',
  '/send-money': 'Send Money',
  '/history': 'Transactions',
  '/escrow': 'Escrow',
  '/recurring': 'Recurring Transfers',
  '/developer': 'Developer Platform',
  '/admin': 'Admin Console',
};

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [showHelpTip, setShowHelpTip] = useState(false);
  const [copiedRef, setCopiedRef] = useState(false);
  const [pwdModalOpen, setPwdModalOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [pwdSubmitting, setPwdSubmitting] = useState(false);
  const [pwdError, setPwdError] = useState('');
  const [pwdSuccess, setPwdSuccess] = useState('');
  const [toasts, setToasts] = useState([]);

  // 2FA Security state
  const [securityTab, setSecurityTab] = useState('password'); // 'password' | '2fa'
  const [twoFactorStatus, setTwoFactorStatus] = useState({ enabled: false, remainingBackupCodes: 0 });
  const [twoFactorSetup, setTwoFactorSetup] = useState(null);
  const [setupCode, setSetupCode] = useState('');
  const [disablePassword, setDisablePassword] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [showDisableForm, setShowDisableForm] = useState(false);
  const [loading2Fa, setLoading2Fa] = useState(false);
  const [twoFaError, setTwoFaError] = useState('');
  const [twoFaSuccess, setTwoFaSuccess] = useState('');
  const [copiedSecret, setCopiedSecret] = useState(false);

  const loadTwoFactorStatus = async () => {
    try {
      const { data } = await client.get('/auth/2fa/status');
      setTwoFactorStatus(data);
    } catch {
      // Ignored
    }
  };

  const handleOpenSecurityModal = () => {
    setPwdModalOpen(true);
    setSecurityTab('password');
    setPwdError('');
    setPwdSuccess('');
    setTwoFaError('');
    setTwoFaSuccess('');
    setTwoFactorSetup(null);
    setShowDisableForm(false);
    loadTwoFactorStatus();
  };

  const handleStart2FaSetup = async () => {
    setLoading2Fa(true);
    setTwoFaError('');
    setTwoFaSuccess('');
    try {
      const { data } = await client.post('/auth/2fa/setup');
      setTwoFactorSetup(data);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not start 2FA setup.');
      setTwoFaError(message);
    } finally {
      setLoading2Fa(false);
    }
  };

  const handleEnable2Fa = async (e) => {
    e.preventDefault();
    if (!setupCode.trim()) {
      setTwoFaError('Please enter the 6-digit code from your authenticator app.');
      return;
    }
    setLoading2Fa(true);
    setTwoFaError('');
    try {
      const { data } = await client.post('/auth/2fa/enable', { code: setupCode.trim() });
      setTwoFactorStatus(data);
      setTwoFactorSetup(null);
      setSetupCode('');
      setTwoFaSuccess('Two-factor authentication successfully activated!');
    } catch (err) {
      const { message } = extractApiError(err, 'Failed to enable 2FA.');
      setTwoFaError(message);
    } finally {
      setLoading2Fa(false);
    }
  };

  const handleDisable2Fa = async (e) => {
    e.preventDefault();
    if (!disablePassword || !disableCode.trim()) {
      setTwoFaError('Password and current 2FA/backup code are required.');
      return;
    }
    setLoading2Fa(true);
    setTwoFaError('');
    try {
      const { data } = await client.post('/auth/2fa/disable', {
        password: disablePassword,
        code: disableCode.trim(),
      });
      setTwoFactorStatus(data);
      setShowDisableForm(false);
      setDisablePassword('');
      setDisableCode('');
      setTwoFaSuccess('Two-factor authentication has been disabled.');
    } catch (err) {
      const { message } = extractApiError(err, 'Failed to disable 2FA.');
      setTwoFaError(message);
    } finally {
      setLoading2Fa(false);
    }
  };

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (!token || !user?.id) return undefined;

    const controller = new AbortController();
    let active = true;
    let reconnectTimer = null;

    const connectStream = async () => {
      try {
        const base = (client.defaults.baseURL || '/api').replace(/\/$/, '');
        const response = await fetch(`${base}/notifications/stream`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error(`SSE HTTP ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (active) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const frames = buffer.split('\n\n');
          buffer = frames.pop() || '';

          for (const frame of frames) {
            const lines = frame.split('\n');
            let eventName = 'message';
            const dataLines = [];
            for (const line of lines) {
              if (line.startsWith('event:')) eventName = line.slice(6).trim();
              else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
            }
            if (eventName === 'connected' || dataLines.length === 0) continue;
            try {
              const payload = JSON.parse(dataLines.join('\n'));
              const toastId = `${Date.now()}-${Math.random()}`;
              setToasts((prev) => [
                ...prev.slice(-2),
                { id: toastId, title: payload.title || 'DhanPay Update', message: payload.message || '' },
              ]);
              setTimeout(() => {
                setToasts((prev) => prev.filter((t) => t.id !== toastId));
              }, 6000);
              window.dispatchEvent(new CustomEvent('dhanpay:notification', { detail: payload }));
            } catch {
              // Ignore non-JSON keepalive comments
            }
          }
        }
      } catch {
        if (active && !controller.signal.aborted) {
          reconnectTimer = setTimeout(connectStream, 5000);
        }
      }
    };

    connectStream();
    return () => {
      active = false;
      controller.abort();
      if (reconnectTimer) clearTimeout(reconnectTimer);
    };
  }, [user?.id]);

  const navItems =
    user?.role === 'ROLE_ADMIN'
      ? [...CUSTOMER_NAV_ITEMS, { to: '/admin', label: 'Admin Console', icon: ShieldAlert }]
      : CUSTOMER_NAV_ITEMS;

  const pageTitle = PAGE_TITLES[location.pathname] || 'Overview';
  const firstName = user?.fullName?.trim().split(/\s+/)[0] || 'Customer';

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const handleCopyWallet = async () => {
    if (!user?.walletReference) return;
    try {
      await navigator.clipboard.writeText(user.walletReference);
      setCopiedRef(true);
      setTimeout(() => setCopiedRef(false), 1800);
    } catch {
      setCopiedRef(false);
    }
  };

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    setPwdError('');
    setPwdSuccess('');
    if (!currentPassword) {
      setPwdError('Current password is required.');
      return;
    }
    if (newPassword.length < 8 || newPassword.length > 72) {
      setPwdError('New password must be between 8 and 72 characters.');
      return;
    }
    if (newPassword !== confirmNewPassword) {
      setPwdError('New passwords do not match.');
      return;
    }

    setPwdSubmitting(true);
    try {
      await client.patch('/auth/password', { currentPassword, newPassword });
      setPwdSuccess('Password updated successfully.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmNewPassword('');
    } catch (err) {
      const { message } = extractApiError(err, 'Unable to update password.');
      setPwdError(message);
    } finally {
      setPwdSubmitting(false);
    }
  };

  const sidebarNavLinkClass = ({ isActive }) =>
    `relative flex items-center gap-3 px-6 py-3 text-sm font-medium transition ${
      isActive
        ? 'bg-gradient-to-r from-[#05334D]/12 via-[#00C896]/15 to-transparent text-[#05334D] font-semibold border-r-[3px] border-[#05334D]'
        : 'text-slate-500 hover:bg-slate-100/70 hover:text-[#05334D]'
    }`;

  return (
    <div className="min-h-screen px-2 py-3 sm:px-6 sm:py-6">
      <div className="mx-auto flex min-h-[calc(100vh-3rem)] max-w-[1240px] overflow-hidden rounded-[28px] border border-white/80 bg-white shadow-card">
        {/* Left Sidebar (Desktop) */}
        <aside className="no-print hidden lg:flex w-60 shrink-0 flex-col justify-between border-r border-slate-100 bg-[#F7FCFC] py-7">
          <div>
            <div className="px-6">
              <NavLink to="/dashboard" className="focus:outline-none">
                <BrandMark compact />
              </NavLink>
            </div>

            <nav className="mt-10 space-y-1">
              {navItems.map(({ to, label, icon: Icon }) => (
                <NavLink key={to} to={to} className={sidebarNavLinkClass}>
                  <Icon className="h-4 w-4 shrink-0" />
                  <span>{label}</span>
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="space-y-1 px-3">
            {showHelpTip && (
              <div className="mx-3 mb-2 rounded-2xl border border-emerald-200/70 bg-white p-3 text-[11px] text-slate-600 shadow-sm">
                <p className="font-semibold text-[#05334D]">Double-Entry INR Ledger</p>
                <p className="mt-1 leading-relaxed">
                  Share your <span className="font-mono font-semibold">{user?.walletReference || 'WLT-IND'}</span> reference to receive zero-fee P2P transfers.
                </p>
              </div>
            )}

            <button
              type="button"
              onClick={handleOpenSecurityModal}
              className="flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium text-slate-500 transition hover:bg-slate-100/80 hover:text-[#05334D]"
            >
              <KeyRound className="h-4 w-4" />
              <span>Security</span>
            </button>

            <button
              type="button"
              onClick={() => setShowHelpTip((v) => !v)}
              className="flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium text-slate-500 transition hover:bg-slate-100/80 hover:text-[#05334D]"
            >
              <HelpCircle className="h-4 w-4" />
              <span>Help</span>
            </button>

            <button
              type="button"
              onClick={handleLogout}
              className="flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium text-slate-500 transition hover:bg-rose-50 hover:text-rose-600"
            >
              <LogOut className="h-4 w-4" />
              <span>Logout</span>
            </button>
          </div>
        </aside>

        {/* Main Content Workspace */}
        <div className="flex min-w-0 flex-1 flex-col bg-[#FAFCFC]">
          {/* Top Greeting & Action Header */}
          <header className="no-print flex items-center justify-between gap-4 border-b border-slate-100 bg-white px-5 py-4 sm:px-8 sm:py-5">
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => setMobileMenuOpen((v) => !v)}
                aria-label="Toggle navigation"
                className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-[#05334D] lg:hidden"
              >
                {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
              </button>

              <div>
                <h1 className="text-xl font-bold tracking-tight text-[#0F172A] sm:text-2xl">
                  {pageTitle}
                </h1>
                <p className="text-xs text-slate-500 sm:text-sm">
                  Hi {firstName}, Welcome back
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2.5 sm:gap-3">
              {user?.walletReference && (
                <button
                  type="button"
                  onClick={handleCopyWallet}
                  title="Copy wallet reference"
                  className="hidden sm:inline-flex items-center gap-2 rounded-xl border border-slate-200/80 bg-white px-3 py-2 text-xs font-mono font-semibold text-[#05334D] shadow-sm transition hover:border-[#00C896] hover:bg-emerald-50/40"
                >
                  <WalletIcon className="h-3.5 w-3.5 text-[#00C896]" />
                  <span>{user.walletReference}</span>
                  {copiedRef ? (
                    <Check className="h-3.5 w-3.5 text-[#00C896]" />
                  ) : (
                    <Copy className="h-3.5 w-3.5 text-slate-400" />
                  )}
                </button>
              )}

              <div className="flex items-center gap-2.5 rounded-full border border-slate-200/80 bg-white py-1 pl-1.5 pr-3 shadow-sm">
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-sky-200 text-xs font-bold text-sky-900">
                  {getInitials(user?.fullName)}
                </div>
                <div className="hidden md:block text-left">
                  <p className="text-xs font-semibold text-slate-900 leading-tight">
                    {user?.fullName || 'Customer'}
                  </p>
                  <p className="text-[10px] font-medium text-slate-400 leading-tight">
                    {user?.role === 'ROLE_ADMIN' ? 'Administrator' : 'INR Wallet'}
                  </p>
                </div>
              </div>
            </div>
          </header>

          {/* Mobile Drawer Navigation */}
          {mobileMenuOpen && (
            <div className="no-print border-b border-slate-100 bg-[#F7FCFC] px-4 py-3 lg:hidden">
              <nav className="grid grid-cols-2 gap-2">
                {navItems.map(({ to, label, icon: Icon }) => (
                  <NavLink
                    key={to}
                    to={to}
                    onClick={() => setMobileMenuOpen(false)}
                    className={({ isActive }) =>
                      `flex items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold transition ${
                        isActive
                          ? 'bg-[#05334D] text-white'
                          : 'bg-white text-slate-600 ring-1 ring-slate-200/70'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4" />
                    <span>{label}</span>
                  </NavLink>
                ))}
                <button
                  type="button"
                  onClick={handleLogout}
                  className="flex items-center gap-2 rounded-xl bg-white px-3 py-2.5 text-xs font-semibold text-rose-600 ring-1 ring-rose-200/70"
                >
                  <LogOut className="h-4 w-4" />
                  <span>Logout</span>
                </button>
              </nav>
            </div>
          )}

          <main className="flex-1 p-5 sm:p-7">
            <Outlet />
          </main>

          <footer className="no-print border-t border-slate-100 bg-white/80 px-5 py-3.5 sm:px-8">
            <div className="flex flex-col items-center justify-between gap-2 text-[11px] text-slate-400 sm:flex-row">
              <span>DhanPay — Secure Transfers. Smarter Money Management.</span>
              <span>Double-Entry General Ledger • Idempotent P2P Engine</span>
            </div>
          </footer>
        </div>
      </div>

      {pwdModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#032539]/60 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-3xl border border-white/80 bg-white shadow-2xl">
            <div className="flex items-start justify-between bg-[#05334D] px-6 py-4 text-white">
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10">
                  <KeyRound className="h-4 w-4 text-[#00D084]" />
                </div>
                <div>
                  <h3 className="text-base font-bold">Account Security</h3>
                  <p className="text-xs text-slate-300">Password &amp; Two-Factor Authentication</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setPwdModalOpen(false)}
                className="text-slate-300 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Security Tabs */}
            <div className="grid grid-cols-2 gap-1 border-b border-slate-100 bg-slate-50/70 p-2">
              <button
                type="button"
                onClick={() => setSecurityTab('password')}
                className={`rounded-2xl py-2 text-xs font-semibold transition ${
                  securityTab === 'password'
                    ? 'bg-white text-[#05334D] shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                Password
              </button>
              <button
                type="button"
                onClick={() => {
                  setSecurityTab('2fa');
                  loadTwoFactorStatus();
                }}
                className={`flex items-center justify-center gap-1.5 rounded-2xl py-2 text-xs font-semibold transition ${
                  securityTab === '2fa'
                    ? 'bg-white text-[#05334D] shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                <ShieldCheck className="h-3.5 w-3.5 text-[#00C896]" />
                Two-Factor (2FA)
                {twoFactorStatus.enabled && (
                  <span className="h-2 w-2 rounded-full bg-[#00C896]" />
                )}
              </button>
            </div>

            {securityTab === 'password' ? (
              <form onSubmit={handlePasswordChange} className="p-6 space-y-4">
                {pwdError && (
                  <div className="flex items-start gap-2 rounded-2xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
                    <span>{pwdError}</span>
                  </div>
                )}

                {pwdSuccess && (
                  <div className="flex items-center gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                    <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00C896]" />
                    <span>{pwdSuccess}</span>
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
                    Current Password
                  </label>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    className="mt-1.5 w-full rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-2.5 text-sm text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
                    New Password
                  </label>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="Minimum 8 characters"
                    className="mt-1.5 w-full rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-2.5 text-sm text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
                    Confirm New Password
                  </label>
                  <input
                    type="password"
                    value={confirmNewPassword}
                    onChange={(e) => setConfirmNewPassword(e.target.value)}
                    className="mt-1.5 w-full rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-2.5 text-sm text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                  />
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setPwdModalOpen(false)}
                    className="rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                  >
                    Close
                  </button>
                  <button
                    type="submit"
                    disabled={pwdSubmitting}
                    className="rounded-full bg-[#00D084] px-5 py-2 text-xs font-bold text-[#032539] hover:bg-[#00C896] disabled:opacity-50"
                  >
                    {pwdSubmitting ? 'Updating...' : 'Update Password'}
                  </button>
                </div>
              </form>
            ) : (
              <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
                {twoFaError && (
                  <div className="flex items-start gap-2 rounded-2xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
                    <span>{twoFaError}</span>
                  </div>
                )}

                {twoFaSuccess && (
                  <div className="flex items-center gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                    <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00C896]" />
                    <span>{twoFaSuccess}</span>
                  </div>
                )}

                {twoFactorStatus.enabled ? (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between rounded-2xl border border-emerald-200 bg-emerald-50/70 p-4">
                      <div className="flex items-center gap-3">
                        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#00D084] text-[#032539]">
                          <ShieldCheck className="h-5 w-5" />
                        </span>
                        <div>
                          <p className="text-sm font-bold text-slate-900">2FA Protection Active</p>
                          <p className="text-xs text-slate-500">
                            Required for transfers exceeding ₹10,000.
                          </p>
                        </div>
                      </div>
                      <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-bold text-emerald-800">
                        Active
                      </span>
                    </div>

                    <div className="flex justify-between items-center text-xs text-slate-500 px-1">
                      <span>Remaining recovery codes:</span>
                      <span className="font-bold text-slate-900">{twoFactorStatus.remainingBackupCodes}</span>
                    </div>

                    {!showDisableForm ? (
                      <button
                        type="button"
                        onClick={() => setShowDisableForm(true)}
                        className="w-full rounded-2xl border border-rose-200 bg-rose-50 py-2.5 text-xs font-semibold text-rose-700 hover:bg-rose-100 transition"
                      >
                        Disable Two-Factor Authentication
                      </button>
                    ) : (
                      <form onSubmit={handleDisable2Fa} className="space-y-3 rounded-2xl bg-slate-50 p-4 border border-slate-200">
                        <p className="text-xs font-semibold text-slate-700">Confirm Disabling 2FA</p>
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-500">Account Password</label>
                          <input
                            type="password"
                            value={disablePassword}
                            onChange={(e) => setDisablePassword(e.target.value)}
                            className="mt-1 w-full rounded-xl border border-slate-200 bg-white p-2 text-xs text-slate-900 focus:outline-none"
                          />
                        </div>
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-500">Current 6-Digit Code (or Backup Code)</label>
                          <input
                            type="text"
                            value={disableCode}
                            onChange={(e) => setDisableCode(e.target.value)}
                            placeholder="123456"
                            className="mt-1 w-full rounded-xl border border-slate-200 bg-white p-2 text-xs font-mono text-slate-900 focus:outline-none"
                          />
                        </div>
                        <div className="flex justify-end gap-2 pt-1">
                          <button
                            type="button"
                            onClick={() => setShowDisableForm(false)}
                            className="rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-600 hover:bg-white"
                          >
                            Cancel
                          </button>
                          <button
                            type="submit"
                            disabled={loading2Fa}
                            className="rounded-full bg-rose-600 px-4 py-1 text-xs font-semibold text-white hover:bg-rose-700 disabled:opacity-50"
                          >
                            {loading2Fa ? 'Disabling...' : 'Confirm Disable'}
                          </button>
                        </div>
                      </form>
                    )}
                  </div>
                ) : twoFactorSetup ? (
                  <form onSubmit={handleEnable2Fa} className="space-y-4">
                    <p className="text-xs text-slate-600">
                      Scan this QR code in Google Authenticator or Authy, or copy the key manually:
                    </p>

                    <div className="flex flex-col items-center justify-center rounded-2xl border border-slate-100 bg-slate-50 p-4">
                      <div
                        className="h-44 w-44 rounded-xl border-2 border-white bg-white p-1.5 shadow-sm"
                        dangerouslySetInnerHTML={{ __html: twoFactorSetup.qrSvg }}
                      />
                      <div className="mt-3 flex items-center gap-2">
                        <span className="font-mono text-xs font-semibold text-slate-700">{twoFactorSetup.secret}</span>
                        <button
                          type="button"
                          onClick={async () => {
                            await navigator.clipboard.writeText(twoFactorSetup.secret);
                            setCopiedSecret(true);
                            setTimeout(() => setCopiedSecret(false), 2000);
                          }}
                          className="rounded-full bg-white p-1 text-slate-500 shadow hover:text-slate-800"
                        >
                          {copiedSecret ? <Check className="h-3 w-3 text-[#00C896]" /> : <Copy className="h-3 w-3" />}
                        </button>
                      </div>
                    </div>

                    <div>
                      <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                        One-Time Backup Recovery Codes
                      </label>
                      <div className="mt-1.5 grid grid-cols-2 gap-1.5 rounded-xl border border-slate-200 bg-slate-50 p-2.5 font-mono text-[11px] text-slate-700">
                        {twoFactorSetup.backupCodes?.map((code) => (
                          <span key={code} className="text-center">{code}</span>
                        ))}
                      </div>
                      <p className="mt-1 text-[10px] text-slate-400">Save these codes in a safe place.</p>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-700">
                        Enter 6-Digit Authenticator Code
                      </label>
                      <input
                        type="text"
                        maxLength={6}
                        value={setupCode}
                        onChange={(e) => setSetupCode(e.target.value)}
                        placeholder="123456"
                        className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white p-2.5 font-mono text-center text-sm font-bold tracking-widest text-slate-900 focus:border-[#00C896] focus:outline-none"
                      />
                    </div>

                    <div className="flex justify-end gap-2 pt-1">
                      <button
                        type="button"
                        onClick={() => setTwoFactorSetup(null)}
                        className="rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={loading2Fa}
                        className="rounded-full bg-[#00D084] px-5 py-2 text-xs font-bold text-[#032539] hover:bg-[#00C896] disabled:opacity-50"
                      >
                        {loading2Fa ? 'Verifying...' : 'Activate 2FA'}
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="space-y-4 text-center py-2">
                    <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-amber-50 text-amber-600">
                      <ShieldAlert className="h-7 w-7" />
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-slate-900">2FA is Not Enabled</h4>
                      <p className="mt-1 text-xs text-slate-500 max-w-xs mx-auto">
                        Protect your funds with RFC 6238 TOTP authenticator verification for sign-in and transfers over ₹10,000.
                      </p>
                    </div>
                    <button
                      type="button"
                      disabled={loading2Fa}
                      onClick={handleStart2FaSetup}
                      className="inline-flex items-center gap-2 rounded-full bg-[#05334D] px-6 py-2.5 text-xs font-bold text-white shadow hover:bg-[#084566] disabled:opacity-50"
                    >
                      <ShieldCheck className="h-4 w-4 text-[#00D084]" />
                      {loading2Fa ? 'Generating Secret...' : 'Setup Authenticator App'}
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {toasts.length > 0 && (
        <div className="fixed bottom-5 right-5 z-50 flex w-80 flex-col gap-2.5">
          {toasts.map((toast) => (
            <div
              key={toast.id}
              className="flex items-start gap-3 rounded-2xl border border-emerald-200 bg-[#05334D] p-3.5 text-white shadow-xl"
            >
              <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-xl bg-[#00D084] text-[#032539]">
                <Bell className="h-4 w-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-bold text-white">{toast.title}</p>
                <p className="mt-0.5 text-[11px] leading-relaxed text-slate-200">{toast.message}</p>
              </div>
              <button
                type="button"
                onClick={() => setToasts((prev) => prev.filter((t) => t.id !== toast.id))}
                className="text-slate-300 hover:text-white"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
