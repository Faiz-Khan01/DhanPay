import { useCallback, useEffect, useState } from 'react';
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Lock,
  RefreshCw,
  ScrollText,
  Search,
  ShieldAlert,
  ShieldCheck,
  Unlock,
  Users,
  Wallet as WalletIcon,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { formatINR, formatTimestamp, getStatusBadge } from '../utils/formatters';

function formatAuditDetails(rawDetails) {
  if (!rawDetails) return '—';
  try {
    const parsed = JSON.parse(rawDetails);
    return Object.entries(parsed)
      .map(([key, val]) => `${key}: ${val}`)
      .join(' • ');
  } catch {
    return rawDetails;
  }
}

export default function AdminDashboardPage() {
  const [stats, setStats] = useState({
    totalUsers: 0,
    activeWallets: 0,
    frozenWallets: 0,
    totalTransferVolume: 0,
    successfulTransfers: 0,
    failedTransfers: 0,
  });
  const [reconciliation, setReconciliation] = useState(null);
  const [auditingLedger, setAuditingLedger] = useState(false);

  const [usersPageData, setUsersPageData] = useState({
    content: [],
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });
  const [userSearchInput, setUserSearchInput] = useState('');
  const [appliedUserSearch, setAppliedUserSearch] = useState('');
  const [userPage, setUserPage] = useState(0);

  const [auditPageData, setAuditPageData] = useState({
    content: [],
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });
  const [auditPage, setAuditPage] = useState(0);

  const [loadingInitial, setLoadingInitial] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [actionBanner, setActionBanner] = useState('');

  // Freeze modal state
  const [freezeTarget, setFreezeTarget] = useState(null);
  const [freezeReason, setFreezeReason] = useState('');
  const [freezeModalError, setFreezeModalError] = useState('');
  const [mutatingWalletId, setMutatingWalletId] = useState(null);

  const fetchStatsAndReconciliation = useCallback(async () => {
    const [statsRes, reconRes] = await Promise.all([
      client.get('/admin/stats'),
      client.get('/admin/reconciliation'),
    ]);
    setStats(statsRes.data);
    setReconciliation(reconRes.data);
  }, []);

  const fetchUsers = useCallback(async () => {
    const params = { page: userPage, size: 8 };
    if (appliedUserSearch) params.search = appliedUserSearch;
    const { data } = await client.get('/admin/users', { params });
    setUsersPageData(data);
  }, [userPage, appliedUserSearch]);

  const fetchAuditLogs = useCallback(async () => {
    const { data } = await client.get('/admin/audit-logs', {
      params: { page: auditPage, size: 8 },
    });
    setAuditPageData(data);
  }, [auditPage]);

  useEffect(() => {
    let active = true;
    async function init() {
      setLoadingInitial(true);
      setErrorMsg('');
      try {
        await Promise.all([
          fetchStatsAndReconciliation(),
          fetchUsers(),
          fetchAuditLogs(),
        ]);
      } catch (err) {
        if (!active) return;
        const { message } = extractApiError(err, 'Failed to load admin console telemetry.');
        setErrorMsg(message);
      } finally {
        if (active) setLoadingInitial(false);
      }
    }
    init();
    return () => {
      active = false;
    };
  }, [fetchStatsAndReconciliation, fetchUsers, fetchAuditLogs]);

  const handleRunLedgerAudit = async () => {
    setAuditingLedger(true);
    setErrorMsg('');
    try {
      const { data } = await client.get('/admin/reconciliation');
      setReconciliation(data);
    } catch (err) {
      const { message } = extractApiError(err, 'Ledger reconciliation failed.');
      setErrorMsg(message);
    } finally {
      setAuditingLedger(false);
    }
  };

  const handleUserSearchSubmit = (e) => {
    e.preventDefault();
    setUserPage(0);
    setAppliedUserSearch(userSearchInput.trim());
  };

  const openFreezeModal = (row) => {
    setFreezeTarget(row);
    setFreezeReason('');
    setFreezeModalError('');
  };

  const handleConfirmFreeze = async (e) => {
    e.preventDefault();
    const trimmed = freezeReason.trim();
    if (trimmed.length < 3) {
      setFreezeModalError('Please provide a reason (at least 3 characters) for the audit log.');
      return;
    }
    setFreezeModalError('');
    setMutatingWalletId(freezeTarget.walletId);
    try {
      await client.patch(`/admin/wallets/${freezeTarget.walletId}/freeze`, {
        reason: trimmed,
      });
      setActionBanner(`Wallet ${freezeTarget.walletReference} frozen and logged to audit trail.`);
      setFreezeTarget(null);
      await Promise.all([fetchStatsAndReconciliation(), fetchUsers(), fetchAuditLogs()]);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not freeze wallet.');
      setFreezeModalError(message);
    } finally {
      setMutatingWalletId(null);
    }
  };

  const handleUnfreezeWallet = async (row) => {
    setErrorMsg('');
    setActionBanner('');
    setMutatingWalletId(row.walletId);
    try {
      await client.patch(`/admin/wallets/${row.walletId}/unfreeze`, {
        reason: `Unfrozen via Admin Console for ${row.email}`,
      });
      setActionBanner(`Wallet ${row.walletReference} restored to ACTIVE status.`);
      await Promise.all([fetchStatsAndReconciliation(), fetchUsers(), fetchAuditLogs()]);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not unfreeze wallet.');
      setErrorMsg(message);
    } finally {
      setMutatingWalletId(null);
    }
  };

  const isBalanced = reconciliation?.systemBalanced === true;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-emerald-700 ring-1 ring-emerald-200/80">
            Admin Operations Console
          </span>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900">
            System Governance &amp; Ledger Audit
          </h1>
          <p className="text-sm text-slate-500">
            Monitor settlement health, enforce wallet controls, and inspect immutable audit logs.
          </p>
        </div>
      </div>

      {errorMsg && (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
          <span>{errorMsg}</span>
        </div>
      )}

      {actionBanner && (
        <div className="flex items-center justify-between gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00C896]" />
            <span>{actionBanner}</span>
          </div>
          <button
            type="button"
            onClick={() => setActionBanner('')}
            className="text-emerald-600 hover:text-emerald-900"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Metric Cards */}
      <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Total Users
            </span>
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-sky-50 text-[#05334D]">
              <Users className="h-4 w-4" />
            </span>
          </div>
          <p className="mt-3 text-3xl font-bold text-slate-900 tabular-nums">
            {loadingInitial ? '...' : stats.totalUsers}
          </p>
          <p className="mt-1 text-xs text-slate-400">Registered platform accounts</p>
        </div>

        <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Active Wallets
            </span>
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50 text-[#00C896]">
              <WalletIcon className="h-4 w-4" />
            </span>
          </div>
          <p className="mt-3 text-3xl font-bold text-slate-900 tabular-nums">
            {loadingInitial ? '...' : stats.activeWallets}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            Frozen wallets:{' '}
            <span className="font-semibold text-orange-500">{stats.frozenWallets}</span>
          </p>
        </div>

        <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Settled Volume (₹)
            </span>
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#05334D] text-[#00D084]">
              <Activity className="h-4 w-4" />
            </span>
          </div>
          <p className="mt-3 text-3xl font-bold text-slate-900 tabular-nums">
            {loadingInitial ? '...' : formatINR(stats.totalTransferVolume)}
          </p>
          <p className="mt-1 text-xs text-slate-400">Cumulative SUCCESS transfers</p>
        </div>

        <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Successful Transfers
            </span>
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50 text-[#00C896]">
              <CheckCircle2 className="h-4 w-4" />
            </span>
          </div>
          <p className="mt-3 text-3xl font-bold text-slate-900 tabular-nums">
            {loadingInitial ? '...' : stats.successfulTransfers}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            Failed attempts:{' '}
            <span className="font-semibold text-rose-500">{stats.failedTransfers}</span>
          </p>
        </div>
      </section>

      {/* Live Ledger Audit Widget (Dark Petrol Feature Card) */}
      <section className="rounded-3xl bg-[#05334D] p-6 text-white shadow-floating space-y-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-bold text-white">Live General Ledger Audit</h2>
            <p className="text-xs text-slate-300">
              Compares sum of materialized wallet balances against Σ(Credits) − Σ(Debits).
            </p>
          </div>

          <button
            type="button"
            onClick={handleRunLedgerAudit}
            disabled={auditingLedger}
            className="inline-flex items-center justify-center gap-2 rounded-full bg-[#00D084] px-5 py-2.5 text-xs font-bold text-[#032539] shadow transition hover:bg-[#00C896] disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${auditingLedger ? 'animate-spin' : ''}`} />
            {auditingLedger ? 'Auditing Ledger...' : 'Run Ledger Audit'}
          </button>
        </div>

        {reconciliation && (
          <>
            <div
              className={`flex flex-col gap-3 rounded-2xl border p-4 sm:flex-row sm:items-center sm:justify-between ${
                isBalanced
                  ? 'border-emerald-400/30 bg-emerald-500/15 text-emerald-100'
                  : 'border-rose-400/40 bg-rose-500/20 text-rose-100'
              }`}
            >
              <div className="flex items-center gap-3">
                {isBalanced ? (
                  <ShieldCheck className="h-7 w-7 shrink-0 text-[#00D084]" />
                ) : (
                  <ShieldAlert className="h-7 w-7 shrink-0 text-rose-300" />
                )}
                <div>
                  <p className="text-sm font-bold text-white">
                    {isBalanced ? 'General Ledger Balanced' : 'Discrepancy Detected'}
                  </p>
                  <p className="text-xs text-slate-200">
                    {reconciliation.totalWalletsAudited} wallets audited • Last verified{' '}
                    {formatTimestamp(reconciliation.auditedAt)}
                  </p>
                </div>
              </div>

              <span
                className={`self-start rounded-full px-3 py-1 text-xs font-bold sm:self-center ${
                  isBalanced
                    ? 'bg-[#00D084]/20 text-[#00D084]'
                    : 'bg-rose-500/30 text-rose-200'
                }`}
              >
                {reconciliation.discrepancies?.length || 0} Discrepancies
              </span>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="rounded-2xl bg-[#0D445F] p-4 ring-1 ring-white/10">
                <p className="text-xs font-medium uppercase tracking-wider text-slate-300">
                  Total Materialized Wallet Balances
                </p>
                <p className="mt-1.5 text-2xl font-bold text-white tabular-nums">
                  {formatINR(reconciliation.aggregateWalletFunds, { precise: true })}
                </p>
              </div>
              <div className="rounded-2xl bg-[#0D445F] p-4 ring-1 ring-white/10">
                <p className="text-xs font-medium uppercase tracking-wider text-slate-300">
                  Authoritative Ledger Sum
                </p>
                <p className="mt-1.5 text-2xl font-bold text-white tabular-nums">
                  {formatINR(reconciliation.aggregateLedgerFunds, { precise: true })}
                </p>
              </div>
            </div>

            {!isBalanced && reconciliation.discrepancies?.length > 0 && (
              <div className="overflow-x-auto rounded-2xl border border-rose-400/30 bg-rose-950/30">
                <table className="min-w-full divide-y divide-rose-400/20 text-left text-xs">
                  <thead className="bg-rose-950/40 text-rose-200 uppercase">
                    <tr>
                      <th className="px-4 py-2.5">Wallet Ref</th>
                      <th className="px-4 py-2.5 text-right">Materialized</th>
                      <th className="px-4 py-2.5 text-right">Ledger Derived</th>
                      <th className="px-4 py-2.5 text-right">Delta</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-rose-400/20 text-rose-100">
                    {reconciliation.discrepancies.map((item) => (
                      <tr key={item.walletRef}>
                        <td className="px-4 py-2.5 font-mono">{item.walletRef}</td>
                        <td className="px-4 py-2.5 text-right tabular-nums">
                          {formatINR(item.currentBalance, { precise: true })}
                        </td>
                        <td className="px-4 py-2.5 text-right tabular-nums">
                          {formatINR(item.ledgerDerivedBalance, { precise: true })}
                        </td>
                        <td className="px-4 py-2.5 text-right font-bold tabular-nums">
                          {formatINR(item.delta, { precise: true })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </section>

      {/* User & Wallet Management Table */}
      <section className="overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card">
        <div className="flex flex-col gap-3 border-b border-slate-100 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900">Customer &amp; Wallet Controls</h2>
            <p className="text-xs text-slate-400">
              Search customers and freeze/unfreeze wallets with mandatory audit logging.
            </p>
          </div>

          <form onSubmit={handleUserSearchSubmit} className="flex items-center gap-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={userSearchInput}
                onChange={(e) => setUserSearchInput(e.target.value)}
                placeholder="Name, email, or WLT ref..."
                className="w-56 rounded-full border border-slate-200 bg-slate-50/70 py-2 pl-9 pr-3 text-xs text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none"
              />
            </div>
            <button
              type="submit"
              className="rounded-full bg-[#05334D] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#084566]"
            >
              Find
            </button>
          </form>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-100 text-left text-sm">
            <thead className="bg-slate-50/80 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
              <tr>
                <th className="px-5 py-3.5">Customer</th>
                <th className="px-5 py-3.5">Wallet Reference</th>
                <th className="px-5 py-3.5 text-right">Balance</th>
                <th className="px-5 py-3.5">Status</th>
                <th className="px-5 py-3.5 text-right">Governance Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {usersPageData.content.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-10 text-center text-xs text-slate-400">
                    No customer accounts match your query.
                  </td>
                </tr>
              ) : (
                usersPageData.content.map((row) => {
                  const isFrozen = row.walletStatus === 'FROZEN';
                  const isBusy = mutatingWalletId === row.walletId;
                  const badge = getStatusBadge(row.walletStatus, { preserveCode: true });
                  return (
                    <tr key={row.walletId} className="hover:bg-slate-50/80">
                      <td className="px-5 py-3.5">
                        <p className="font-semibold text-slate-800">{row.fullName}</p>
                        <p className="text-xs text-slate-400">{row.email}</p>
                      </td>
                      <td className="px-5 py-3.5 font-mono text-xs text-slate-600">
                        {row.walletReference}
                      </td>
                      <td className="px-5 py-3.5 text-right font-bold text-slate-800 tabular-nums">
                        {formatINR(row.balance)}
                      </td>
                      <td className="px-5 py-3.5">
                        <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${badge.pill}`}>
                          {badge.label}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        {isFrozen ? (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => handleUnfreezeWallet(row)}
                            className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3.5 py-1.5 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200 transition hover:bg-emerald-100 disabled:opacity-50"
                          >
                            <Unlock className="h-3.5 w-3.5" />
                            Unfreeze
                          </button>
                        ) : (
                          <button
                            type="button"
                            disabled={isBusy || row.walletStatus === 'CLOSED'}
                            onClick={() => openFreezeModal(row)}
                            className="inline-flex items-center gap-1.5 rounded-full bg-orange-50 px-3.5 py-1.5 text-xs font-semibold text-orange-600 ring-1 ring-orange-200 transition hover:bg-orange-100 disabled:opacity-50"
                          >
                            <Lock className="h-3.5 w-3.5" />
                            Freeze
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/70 px-5 py-3 text-xs text-slate-500">
          <span>{usersPageData.totalElements} customers</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={usersPageData.first || userPage === 0}
              onClick={() => setUserPage((p) => Math.max(0, p - 1))}
              className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1 text-slate-700 disabled:opacity-45"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
              Prev
            </button>
            <span>
              Page {usersPageData.totalPages === 0 ? 0 : userPage + 1} of {usersPageData.totalPages}
            </span>
            <button
              type="button"
              disabled={usersPageData.last || userPage + 1 >= usersPageData.totalPages}
              onClick={() => setUserPage((p) => p + 1)}
              className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1 text-slate-700 disabled:opacity-45"
            >
              Next
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </section>

      {/* Audit Log Viewer */}
      <section className="overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card">
        <div className="flex items-center gap-2.5 border-b border-slate-100 px-6 py-4">
          <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-emerald-50 text-[#00C896]">
            <ScrollText className="h-4 w-4" />
          </span>
          <div>
            <h2 className="text-base font-bold text-slate-900">Immutable Audit Log</h2>
            <p className="text-xs text-slate-400">
              Chronological trail of security, transfer, and admin events.
            </p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-100 text-left text-xs">
            <thead className="bg-slate-50/80 font-semibold uppercase tracking-wider text-slate-400">
              <tr>
                <th className="px-5 py-3.5">Timestamp</th>
                <th className="px-5 py-3.5">Actor</th>
                <th className="px-5 py-3.5">Action</th>
                <th className="px-5 py-3.5">Target Resource</th>
                <th className="px-5 py-3.5">Description / Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {auditPageData.content.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-10 text-center text-slate-400">
                    No audit entries found.
                  </td>
                </tr>
              ) : (
                auditPageData.content.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/80">
                    <td className="whitespace-nowrap px-5 py-3 text-slate-400">
                      {formatTimestamp(log.createdAt)}
                    </td>
                    <td className="px-5 py-3 font-medium text-slate-700">
                      {log.actorDisplay || 'System'}
                    </td>
                    <td className="whitespace-nowrap px-5 py-3">
                      <span className="rounded-full bg-sky-50 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-[#05334D] ring-1 ring-sky-200/80">
                        {log.action}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-5 py-3 font-mono text-slate-600">
                      {log.resourceType}: {log.resourceId || '—'}
                    </td>
                    <td className="max-w-md truncate px-5 py-3 text-slate-500" title={log.details || ''}>
                      {formatAuditDetails(log.details)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/70 px-5 py-3 text-xs text-slate-500">
          <span>{auditPageData.totalElements} audit records</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={auditPageData.first || auditPage === 0}
              onClick={() => setAuditPage((p) => Math.max(0, p - 1))}
              className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1 text-slate-700 disabled:opacity-45"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
              Prev
            </button>
            <span>
              Page {auditPageData.totalPages === 0 ? 0 : auditPage + 1} of {auditPageData.totalPages}
            </span>
            <button
              type="button"
              disabled={auditPageData.last || auditPage + 1 >= auditPageData.totalPages}
              onClick={() => setAuditPage((p) => p + 1)}
              className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1 text-slate-700 disabled:opacity-45"
            >
              Next
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </section>

      {/* Freeze Reason Modal */}
      {freezeTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#032539]/60 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-3xl border border-white/80 bg-white shadow-2xl">
            <div className="flex items-start justify-between bg-[#05334D] px-6 py-4 text-white">
              <div>
                <h3 className="text-lg font-bold">Freeze Customer Wallet</h3>
                <p className="mt-0.5 text-xs text-slate-300">
                  {freezeTarget.fullName} •{' '}
                  <span className="font-mono text-emerald-300">{freezeTarget.walletReference}</span>
                </p>
              </div>
              <button
                type="button"
                onClick={() => setFreezeTarget(null)}
                className="text-slate-300 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              {freezeModalError && (
                <div className="flex items-start gap-2 rounded-2xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                  <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-500" />
                  <span>{freezeModalError}</span>
                </div>
              )}

              <form onSubmit={handleConfirmFreeze} className="space-y-4">
                <div>
                  <label
                    htmlFor="freeze-reason"
                    className="block text-xs font-semibold uppercase tracking-wider text-slate-500"
                  >
                    Mandatory Audit Reason
                  </label>
                  <textarea
                    id="freeze-reason"
                    rows={3}
                    value={freezeReason}
                    onChange={(e) => setFreezeReason(e.target.value)}
                    placeholder="e.g. Suspicious velocity spike pending KYC review"
                    className="mt-1.5 w-full rounded-2xl border border-slate-200 bg-slate-50/70 p-3 text-sm text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none"
                  />
                </div>

                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setFreezeTarget(null)}
                    className="rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={mutatingWalletId === freezeTarget.walletId}
                    className="inline-flex items-center gap-1.5 rounded-full bg-orange-500 px-5 py-2 text-xs font-semibold text-white hover:bg-orange-600 disabled:opacity-60"
                  >
                    <Lock className="h-3.5 w-3.5" />
                    {mutatingWalletId === freezeTarget.walletId ? 'Freezing...' : 'Confirm Freeze'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
