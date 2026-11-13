import { useCallback, useEffect, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  Lock,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Unlock,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { formatINR, formatTimestamp, getStatusBadge } from '../utils/formatters';

export default function EscrowPage() {
  const [holds, setHolds] = useState([]);
  const [beneficiaries, setBeneficiaries] = useState([]);
  const [wallet, setWallet] = useState(null);
  const [loading, setLoading] = useState(true);

  const [sellerWalletReference, setSellerWalletReference] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [mutatingRef, setMutatingRef] = useState(null);

  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const [escrowRes, walletRes, benRes] = await Promise.all([
        client.get('/escrow'),
        client.get('/wallets/me'),
        client.get('/beneficiaries'),
      ]);
      setHolds(Array.isArray(escrowRes.data) ? escrowRes.data : []);
      setWallet(walletRes.data);
      setBeneficiaries(Array.isArray(benRes.data) ? benRes.data : []);
    } catch (err) {
      const { message } = extractApiError(err, 'Unable to load escrow holds.');
      setErrorMsg(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleCreateHold = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    const target = sellerWalletReference.trim();
    const numericAmount = Number(amount);
    if (!target) {
      setErrorMsg('Please enter or select a seller wallet reference.');
      return;
    }
    if (!Number.isFinite(numericAmount) || numericAmount < 1) {
      setErrorMsg('Minimum escrow hold amount is ₹1.00.');
      return;
    }

    setSubmitting(true);
    try {
      const { data } = await client.post('/escrow', {
        sellerWalletReference: target,
        amount: numericAmount,
        description: description.trim() || undefined,
      });
      setSuccessMsg(`Locked ${formatINR(data.amount)} in escrow (${data.reference}) for ${data.sellerName}.`);
      setAmount('');
      setDescription('');
      await loadData();
    } catch (err) {
      const { message } = extractApiError(err, 'Could not create escrow hold.');
      setErrorMsg(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleAction = async (reference, action) => {
    setErrorMsg('');
    setSuccessMsg('');
    setMutatingRef(reference);
    try {
      const { data } = await client.post(`/escrow/${encodeURIComponent(reference)}/${action}`);
      setSuccessMsg(
        action === 'release'
          ? `Released ${formatINR(data.amount)} from ${data.reference} to ${data.sellerName}.`
          : `Refunded ${formatINR(data.amount)} from ${data.reference} back to ${data.buyerName}.`
      );
      await loadData();
    } catch (err) {
      const { message } = extractApiError(err, `Failed to ${action} escrow hold.`);
      setErrorMsg(message);
    } finally {
      setMutatingRef(null);
    }
  };

  const activeBuyerHeld = holds
    .filter((h) => h.role === 'BUYER' && h.status === 'HELD')
    .reduce((acc, h) => acc + Number(h.amount || 0), 0);

  const activeSellerIncoming = holds
    .filter((h) => h.role === 'SELLER' && h.status === 'HELD')
    .reduce((acc, h) => acc + Number(h.amount || 0), 0);

  const activeCount = holds.filter((h) => h.status === 'HELD').length;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-emerald-700 ring-1 ring-emerald-200/80">
            GL-SYS-ESCROW-INR-0001
          </span>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900">
            Protected Escrow Holds
          </h1>
          <p className="text-sm text-slate-500">
            Lock funds safely in general-ledger escrow until delivery is confirmed or refunded.
          </p>
        </div>

        <button
          type="button"
          onClick={loadData}
          disabled={loading}
          className="inline-flex items-center gap-2 self-start rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
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

      {/* Summary Hero + Create Hold */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="rounded-3xl bg-[#05334D] p-6 text-white shadow-floating lg:col-span-5 flex flex-col justify-between space-y-6">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-emerald-300">
                Escrow Telemetry
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 text-[#00D084]">
                <ShieldCheck className="h-5 w-5" />
              </span>
            </div>
            <p className="mt-4 text-xs text-slate-300">Available Wallet Balance</p>
            <p className="mt-1 text-3xl font-bold tabular-nums">
              {wallet ? formatINR(wallet.balance) : '—'}
            </p>
          </div>

          <div className="grid grid-cols-3 gap-3 border-t border-white/10 pt-4 text-xs">
            <div>
              <p className="text-slate-300">Locked Out</p>
              <p className="mt-1 font-bold text-white tabular-nums">{formatINR(activeBuyerHeld)}</p>
            </div>
            <div>
              <p className="text-slate-300">Incoming Held</p>
              <p className="mt-1 font-bold text-[#00D084] tabular-nums">{formatINR(activeSellerIncoming)}</p>
            </div>
            <div>
              <p className="text-slate-300">Active Holds</p>
              <p className="mt-1 font-bold text-white tabular-nums">{activeCount}</p>
            </div>
          </div>
        </div>

        <form
          onSubmit={handleCreateHold}
          className="rounded-3xl border border-slate-100 bg-white p-6 shadow-card lg:col-span-7 space-y-4"
        >
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-slate-900">Create New Escrow Hold</h2>
              <p className="text-xs text-slate-400">
                Debits your wallet into escrow; only you can release funds to the seller.
              </p>
            </div>
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50 text-[#00C896]">
              <Lock className="h-4 w-4" />
            </span>
          </div>

          {beneficiaries.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {beneficiaries.slice(0, 5).map((b) => (
                <button
                  key={b.id}
                  type="button"
                  onClick={() => setSellerWalletReference(b.walletReference)}
                  className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                    sellerWalletReference === b.walletReference
                      ? 'bg-[#05334D] text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-emerald-50 hover:text-emerald-700'
                  }`}
                >
                  {b.nickname}
                </button>
              ))}
            </div>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="escrow-seller" className="block text-xs font-semibold text-slate-600">
                Seller Wallet Reference
              </label>
              <input
                id="escrow-seller"
                type="text"
                value={sellerWalletReference}
                onChange={(e) => setSellerWalletReference(e.target.value)}
                placeholder="WLT-IND-10000003"
                className="mt-1.5 w-full rounded-2xl border border-slate-200 bg-slate-50/70 px-3.5 py-2.5 text-sm font-mono text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none"
              />
            </div>

            <div>
              <label htmlFor="escrow-amount" className="block text-xs font-semibold text-slate-600">
                Amount (INR)
              </label>
              <input
                id="escrow-amount"
                type="number"
                step="0.01"
                min="1"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="1500.00"
                className="mt-1.5 w-full rounded-2xl border border-slate-200 bg-slate-50/70 px-3.5 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none"
              />
            </div>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label htmlFor="escrow-desc" className="block text-xs font-semibold text-slate-600">
                Milestone / Order Note
              </label>
              <input
                id="escrow-desc"
                type="text"
                maxLength={255}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="e.g. Design milestone #1 delivery"
                className="mt-1.5 w-full rounded-2xl border border-slate-200 bg-slate-50/70 px-3.5 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center justify-center gap-2 rounded-full bg-[#00D084] px-6 py-2.5 text-sm font-bold text-[#032539] shadow transition hover:bg-[#00C896] disabled:opacity-60"
            >
              <Lock className="h-4 w-4" />
              {submitting ? 'Locking...' : 'Lock in Escrow'}
            </button>
          </div>
        </form>
      </div>

      {/* Escrow Holds Table */}
      <section className="overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card">
        <div className="border-b border-slate-100 px-6 py-4">
          <h2 className="text-base font-bold text-slate-900">Your Escrow Agreements</h2>
          <p className="text-xs text-slate-400">
            Buyers release funds upon fulfillment; sellers can refund active holds back to the buyer.
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-100 text-left text-xs">
            <thead className="bg-slate-50/80 font-semibold uppercase tracking-wider text-slate-400">
              <tr>
                <th className="px-5 py-3.5">Reference</th>
                <th className="px-5 py-3.5">Your Role</th>
                <th className="px-5 py-3.5">Counterparty</th>
                <th className="px-5 py-3.5">Description</th>
                <th className="px-5 py-3.5 text-right">Amount</th>
                <th className="px-5 py-3.5">Status</th>
                <th className="px-5 py-3.5">Created</th>
                <th className="px-5 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {holds.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-6 py-10 text-center text-slate-400">
                    {loading ? 'Loading escrow holds...' : 'No escrow holds yet.'}
                  </td>
                </tr>
              ) : (
                holds.map((hold) => {
                  const badge = getStatusBadge(hold.status);
                  const isBuyer = hold.role === 'BUYER';
                  const counterpartyName = isBuyer ? hold.sellerName : hold.buyerName;
                  const counterpartyWallet = isBuyer ? hold.sellerWalletReference : hold.buyerWalletReference;
                  const isBusy = mutatingRef === hold.reference;
                  return (
                    <tr key={hold.reference} className="hover:bg-slate-50/80">
                      <td className="whitespace-nowrap px-5 py-3.5 font-mono font-semibold text-slate-800">
                        {hold.reference}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                            isBuyer
                              ? 'bg-sky-50 text-[#05334D] ring-1 ring-sky-200/80'
                              : 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200/80'
                          }`}
                        >
                          {hold.role}
                        </span>
                      </td>
                      <td className="px-5 py-3.5">
                        <p className="font-medium text-slate-800">{counterpartyName}</p>
                        <p className="font-mono text-[11px] text-slate-400">{counterpartyWallet}</p>
                      </td>
                      <td className="max-w-xs truncate px-5 py-3.5 text-slate-500">
                        {hold.description || '—'}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5 text-right font-semibold text-slate-900 tabular-nums">
                        {formatINR(hold.amount)}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5">
                        <span className={`inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${badge.pill}`}>
                          {badge.label}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5 text-slate-400">
                        {formatTimestamp(hold.createdAt)}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3.5 text-right">
                        {hold.status === 'HELD' && isBuyer && (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => handleAction(hold.reference, 'release')}
                            className="inline-flex items-center gap-1 rounded-full bg-[#00D084] px-3 py-1.5 text-xs font-bold text-[#032539] transition hover:bg-[#00C896] disabled:opacity-50"
                          >
                            <Unlock className="h-3.5 w-3.5" />
                            Release
                          </button>
                        )}
                        {hold.status === 'HELD' && !isBuyer && (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => handleAction(hold.reference, 'refund')}
                            className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-700 ring-1 ring-amber-200 transition hover:bg-amber-100 disabled:opacity-50"
                          >
                            <RotateCcw className="h-3.5 w-3.5" />
                            Refund
                          </button>
                        )}
                        {hold.status !== 'HELD' && (
                          <span className="text-[11px] text-slate-300">Settled</span>
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
    </div>
  );
}
