import { useCallback, useEffect, useState } from 'react';
import {
  AlertCircle,
  Calendar,
  CheckCircle2,
  Clock,
  Pause,
  Play,
  Plus,
  RefreshCw,
  Repeat,
  Trash2,
  User,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { formatINR, formatTimestamp, getStatusBadge } from '../utils/formatters';

export default function RecurringTransfersPage() {
  const [rules, setRules] = useState([]);
  const [beneficiaries, setBeneficiaries] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modal create state
  const [modalOpen, setModalOpen] = useState(false);
  const [recipientRef, setRecipientRef] = useState('');
  const [amount, setAmount] = useState('');
  const [frequency, setFrequency] = useState('MONTHLY');
  const [description, setDescription] = useState('');
  const [maxExecutions, setMaxExecutions] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [mutatingRef, setMutatingRef] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const [recRes, benRes] = await Promise.all([
        client.get('/recurring'),
        client.get('/beneficiaries').catch(() => ({ data: [] })),
      ]);
      setRules(Array.isArray(recRes.data) ? recRes.data : []);
      setBeneficiaries(Array.isArray(benRes.data) ? benRes.data : []);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not load standing orders.');
      setErrorMsg(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleCreateRule = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    const target = recipientRef.trim();
    const num = Number(amount);
    if (!target) {
      setErrorMsg('Please specify a recipient wallet reference.');
      return;
    }
    if (!Number.isFinite(num) || num < 1) {
      setErrorMsg('Minimum recurring transfer amount is ₹1.00.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        recipientWalletReference: target,
        amount: num,
        frequency,
        description: description.trim() || undefined,
        maxExecutions: maxExecutions ? parseInt(maxExecutions, 10) : undefined,
      };

      const { data } = await client.post('/recurring', payload);
      setSuccessMsg(`Standing order scheduled: ${formatINR(data.amount)} (${data.frequency}) to ${data.recipientName}.`);
      setModalOpen(false);
      setRecipientRef('');
      setAmount('');
      setDescription('');
      setMaxExecutions('');
      await loadData();
    } catch (err) {
      const { message } = extractApiError(err, 'Failed to create standing order.');
      setErrorMsg(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleTogglePause = async (rule) => {
    setErrorMsg('');
    setSuccessMsg('');
    setMutatingRef(rule.reference);
    const isPaused = rule.status === 'PAUSED';
    const action = isPaused ? 'resume' : 'pause';
    try {
      await client.patch(`/recurring/${encodeURIComponent(rule.reference)}/${action}`);
      setSuccessMsg(`Standing order ${rule.reference} ${isPaused ? 'resumed' : 'paused'}.`);
      await loadData();
    } catch (err) {
      const { message } = extractApiError(err, `Could not ${action} standing order.`);
      setErrorMsg(message);
    } finally {
      setMutatingRef(null);
    }
  };

  const handleCancel = async (reference) => {
    if (!window.confirm('Are you sure you want to cancel this recurring standing order?')) {
      return;
    }
    setErrorMsg('');
    setSuccessMsg('');
    setMutatingRef(reference);
    try {
      await client.delete(`/recurring/${encodeURIComponent(reference)}`);
      setSuccessMsg(`Standing order ${reference} cancelled.`);
      await loadData();
    } catch (err) {
      const { message } = extractApiError(err, 'Could not cancel standing order.');
      setErrorMsg(message);
    } finally {
      setMutatingRef(null);
    }
  };

  const activeRules = rules.filter((r) => r.status === 'ACTIVE' && r.role === 'SENDER');
  const totalMonthlyCommitment = activeRules.reduce((acc, r) => {
    const val = Number(r.amount || 0);
    if (r.frequency === 'DAILY') return acc + val * 30;
    if (r.frequency === 'WEEKLY') return acc + val * 4;
    return acc + val;
  }, 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-emerald-700 ring-1 ring-emerald-200/80">
            Automated Standing Orders
          </span>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900">
            Recurring Transfers &amp; SIPs
          </h1>
          <p className="text-sm text-slate-500">
            Schedule automated daily, weekly, or monthly payments with idempotent execution.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={loadData}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            type="button"
            onClick={() => setModalOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-full bg-[#00D084] px-5 py-2 text-xs font-bold text-[#032539] shadow transition hover:bg-[#00C896]"
          >
            <Plus className="h-4 w-4" />
            New Standing Order
          </button>
        </div>
      </div>

      {errorMsg && (
        <div className="flex items-start justify-between gap-2 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          <div className="flex items-start gap-2">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
            <span>{errorMsg}</span>
          </div>
          <button type="button" onClick={() => setErrorMsg('')} className="text-rose-500 hover:text-rose-800">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {successMsg && (
        <div className="flex items-center justify-between gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00C896]" />
            <span>{successMsg}</span>
          </div>
          <button type="button" onClick={() => setSuccessMsg('')} className="text-emerald-600 hover:text-emerald-900">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Summary Stat Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Active Standing Orders</p>
          <p className="mt-2 text-3xl font-bold text-slate-900 tabular-nums">{activeRules.length}</p>
          <p className="mt-1 text-xs text-slate-400">Scheduled auto-debits</p>
        </div>

        <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Est. Monthly Outflow</p>
          <p className="mt-2 text-3xl font-bold text-slate-900 tabular-nums">{formatINR(totalMonthlyCommitment)}</p>
          <p className="mt-1 text-xs text-slate-400">Aggregated monthly cadence</p>
        </div>

        <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Total Executed Transfers</p>
          <p className="mt-2 text-3xl font-bold text-[#00C896] tabular-nums">
            {rules.reduce((acc, r) => acc + (r.totalExecutions || 0), 0)}
          </p>
          <p className="mt-1 text-xs text-slate-400">Lifetime auto-settlements</p>
        </div>
      </div>

      {/* Standing Orders Table */}
      <section className="overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card">
        <div className="border-b border-slate-100 px-6 py-4">
          <h2 className="text-base font-bold text-slate-900">Your Scheduled Rules</h2>
          <p className="text-xs text-slate-400">
            Automatic standing orders executed with deadlock-free settlement.
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-100 text-left text-xs">
            <thead className="bg-slate-50/80 font-semibold uppercase tracking-wider text-slate-400">
              <tr>
                <th className="px-5 py-3.5">Reference</th>
                <th className="px-5 py-3.5">Counterparty</th>
                <th className="px-5 py-3.5">Cadence</th>
                <th className="px-5 py-3.5 text-right">Amount</th>
                <th className="px-5 py-3.5">Next Execution</th>
                <th className="px-5 py-3.5">Cycles</th>
                <th className="px-5 py-3.5">Status</th>
                <th className="px-5 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rules.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-6 py-10 text-center text-slate-400">
                    {loading ? 'Loading standing orders...' : 'No recurring transfers scheduled yet.'}
                  </td>
                </tr>
              ) : (
                rules.map((rule) => {
                  const isSender = rule.role === 'SENDER';
                  const counterpartyName = isSender ? rule.recipientName : rule.senderName;
                  const counterpartyWallet = isSender ? rule.recipientWalletReference : rule.senderWalletReference;
                  const isBusy = mutatingRef === rule.reference;
                  return (
                    <tr key={rule.reference} className="hover:bg-slate-50/80">
                      <td className="whitespace-nowrap px-5 py-3.5 font-mono font-semibold text-slate-800">
                        {rule.reference}
                      </td>
                      <td className="px-5 py-3.5">
                        <p className="font-medium text-slate-800">{counterpartyName}</p>
                        <p className="font-mono text-[11px] text-slate-400">{counterpartyWallet}</p>
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5">
                        <span className="inline-flex items-center gap-1 rounded-full bg-sky-50 px-2.5 py-0.5 text-[11px] font-semibold text-[#05334D] ring-1 ring-sky-200/80">
                          <Repeat className="h-3 w-3" />
                          {rule.frequency}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5 text-right font-semibold text-slate-900 tabular-nums">
                        {formatINR(rule.amount)}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5 text-slate-500">
                        {rule.status === 'ACTIVE' ? formatTimestamp(rule.nextExecutionAt) : '—'}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5 text-slate-600">
                        {rule.totalExecutions}
                        {rule.maxExecutions ? ` / ${rule.maxExecutions}` : ''}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                            rule.status === 'ACTIVE'
                              ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200'
                              : rule.status === 'PAUSED'
                              ? 'bg-amber-50 text-amber-700 ring-1 ring-amber-200'
                              : 'bg-slate-100 text-slate-600'
                          }`}
                        >
                          {rule.status}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5 text-right">
                        {isSender && rule.status !== 'CANCELLED' && rule.status !== 'COMPLETED' ? (
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              type="button"
                              disabled={isBusy}
                              onClick={() => handleTogglePause(rule)}
                              title={rule.status === 'PAUSED' ? 'Resume Rule' : 'Pause Rule'}
                              className="rounded-full p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900 disabled:opacity-50"
                            >
                              {rule.status === 'PAUSED' ? (
                                <Play className="h-4 w-4 text-[#00C896]" />
                              ) : (
                                <Pause className="h-4 w-4" />
                              )}
                            </button>
                            <button
                              type="button"
                              disabled={isBusy}
                              onClick={() => handleCancel(rule.reference)}
                              title="Cancel Rule"
                              className="rounded-full p-1.5 text-slate-500 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-50"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* New Standing Order Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#032539]/60 p-4 backdrop-blur-sm animate-fade-in">
          <div className="w-full max-w-md overflow-hidden rounded-3xl border border-white/80 bg-white shadow-2xl">
            <div className="flex items-center justify-between bg-[#05334D] px-6 py-4 text-white">
              <div className="flex items-center gap-2.5">
                <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-white/10 text-[#00D084]">
                  <Repeat className="h-4 w-4" />
                </span>
                <div>
                  <h3 className="text-base font-bold">New Standing Order</h3>
                  <p className="text-xs text-slate-300">Set up an automated recurring transfer</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="rounded-full p-1 text-slate-300 hover:bg-white/10 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleCreateRule} className="p-6 space-y-4">
              <div>
                <label htmlFor="recip-ref" className="block text-xs font-semibold text-slate-600">
                  Recipient Wallet Reference
                </label>
                <input
                  id="recip-ref"
                  type="text"
                  value={recipientRef}
                  onChange={(e) => setRecipientRef(e.target.value)}
                  placeholder="WLT-IND-10000003"
                  className="mt-1 w-full rounded-2xl border border-slate-200 bg-slate-50/70 p-2.5 font-mono text-xs text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                />

                {beneficiaries.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1">
                    {beneficiaries.slice(0, 4).map((b) => (
                      <button
                        key={b.id}
                        type="button"
                        onClick={() => setRecipientRef(b.walletReference)}
                        className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] text-slate-600 hover:bg-emerald-50 hover:text-emerald-700"
                      >
                        {b.nickname}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label htmlFor="rec-amount" className="block text-xs font-semibold text-slate-600">
                    Amount (INR)
                  </label>
                  <input
                    id="rec-amount"
                    type="number"
                    step="0.01"
                    min="1"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="1000.00"
                    className="mt-1 w-full rounded-2xl border border-slate-200 bg-slate-50/70 p-2.5 text-xs text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                  />
                </div>

                <div>
                  <label htmlFor="rec-freq" className="block text-xs font-semibold text-slate-600">
                    Frequency
                  </label>
                  <select
                    id="rec-freq"
                    value={frequency}
                    onChange={(e) => setFrequency(e.target.value)}
                    className="mt-1 w-full rounded-2xl border border-slate-200 bg-slate-50/70 p-2.5 text-xs text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                  >
                    <option value="DAILY">Daily</option>
                    <option value="WEEKLY">Weekly</option>
                    <option value="MONTHLY">Monthly</option>
                  </select>
                </div>
              </div>

              <div>
                <label htmlFor="rec-desc" className="block text-xs font-semibold text-slate-600">
                  Description / Purpose (Optional)
                </label>
                <input
                  id="rec-desc"
                  type="text"
                  maxLength={100}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="e.g. Monthly Rent or SIP Investment"
                  className="mt-1 w-full rounded-2xl border border-slate-200 bg-slate-50/70 p-2.5 text-xs text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                />
              </div>

              <div>
                <label htmlFor="rec-cycles" className="block text-xs font-semibold text-slate-600">
                  Max Installments (Optional)
                </label>
                <input
                  id="rec-cycles"
                  type="number"
                  min="1"
                  max="120"
                  value={maxExecutions}
                  onChange={(e) => setMaxExecutions(e.target.value)}
                  placeholder="Leave empty for ongoing"
                  className="mt-1 w-full rounded-2xl border border-slate-200 bg-slate-50/70 p-2.5 text-xs text-slate-900 focus:border-[#00C896] focus:bg-white focus:outline-none"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-full bg-[#00D084] px-5 py-2 text-xs font-bold text-[#032539] hover:bg-[#00C896] disabled:opacity-50"
                >
                  {submitting ? 'Scheduling...' : 'Schedule Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
