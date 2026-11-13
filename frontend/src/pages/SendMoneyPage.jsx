import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Download,
  KeyRound,
  Printer,
  QrCode,
  Scan,
  Send,
  ShieldCheck,
  Wallet as WalletIcon,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { useAuth } from '../context/AuthContext';
import ScanQrModal from '../components/ScanQrModal';
import {
  formatINR,
  formatTimestamp,
  generateIdempotencyKey,
  getStatusBadge,
} from '../utils/formatters';

const DEMO_RECIPIENTS = [
  { name: 'Aarav Sharma', walletReference: 'WLT-IND-10000002' },
  { name: 'Priya Patel', walletReference: 'WLT-IND-10000003' },
  { name: 'Rohan Verma', walletReference: 'WLT-IND-10000004' },
];

const STEPS = [
  { id: 1, label: 'Details' },
  { id: 2, label: 'Review & Confirm' },
  { id: 3, label: 'Receipt' },
];

export default function SendMoneyPage() {
  const { user, updateUserWallet } = useAuth();

  const [step, setStep] = useState(1);
  const [walletInfo, setWalletInfo] = useState({
    walletReference: user?.walletReference || '',
    balance: Number(user?.balance ?? 0),
    currency: user?.currency || 'INR',
    status: 'ACTIVE',
  });
  const [loadingWallet, setLoadingWallet] = useState(true);

  const [recipientRef, setRecipientRef] = useState('');
  const [amountInput, setAmountInput] = useState('');
  const [description, setDescription] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');

  const [validationError, setValidationError] = useState('');
  const [apiError, setApiError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [receipt, setReceipt] = useState(null);
  const [beneficiaries, setBeneficiaries] = useState([]);
  const [limits, setLimits] = useState(null);
  const [scanQrOpen, setScanQrOpen] = useState(false);
  const [totpCode, setTotpCode] = useState('');

  useEffect(() => {
    let active = true;
    async function fetchWallet() {
      setLoadingWallet(true);
      try {
        const [{ data }, beneficiariesRes, limitsRes] = await Promise.all([
          client.get('/wallets/me'),
          client.get('/beneficiaries').catch(() => ({ data: [] })),
          client.get('/wallets/limits').catch(() => ({ data: null })),
        ]);
        if (!active) return;
        setWalletInfo({
          walletReference: data.walletReference,
          balance: Number(data.balance ?? 0),
          currency: data.currency || 'INR',
          status: data.status || 'ACTIVE',
        });
        setBeneficiaries(Array.isArray(beneficiariesRes.data) ? beneficiariesRes.data : []);
        setLimits(limitsRes.data || null);
        updateUserWallet({
          walletReference: data.walletReference,
          balance: data.balance,
          currency: data.currency,
        });
      } catch (err) {
        if (!active) return;
        const { message } = extractApiError(err, 'Could not fetch live wallet balance.');
        setApiError(message);
      } finally {
        if (active) setLoadingWallet(false);
      }
    }
    fetchWallet();
    return () => {
      active = false;
    };
  }, [updateUserWallet]);

  const validateInput = () => {
    const trimmedRecipient = recipientRef.trim();
    if (!trimmedRecipient) {
      return 'Recipient wallet reference is required.';
    }
    if (
      walletInfo.walletReference &&
      trimmedRecipient.toUpperCase() === walletInfo.walletReference.trim().toUpperCase()
    ) {
      return 'You cannot transfer funds to your own wallet reference.';
    }

    const rawAmount = amountInput.trim();
    if (!rawAmount) {
      return 'Transfer amount is required.';
    }
    const numericAmount = Number(rawAmount);
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      return 'Transfer amount must be greater than zero.';
    }
    if (numericAmount < 1) {
      return 'Minimum transfer amount is ₹1.00.';
    }
    if (numericAmount > 50000) {
      return 'Single transfer limit is ₹50,000.00.';
    }
    if (!/^\d+(\.\d{1,4})?$/.test(rawAmount)) {
      return 'Amount can have at most 4 decimal places.';
    }
    if (numericAmount > walletInfo.balance) {
      return `Amount exceeds your available balance of ${formatINR(walletInfo.balance)}.`;
    }
    if (limits && numericAmount > Number(limits.dailyRemaining ?? limits.dailyLimit)) {
      return `Amount exceeds your remaining 24-hour transfer limit of ${formatINR(limits.dailyRemaining)}.`;
    }
    if (walletInfo.status !== 'ACTIVE') {
      return `Your wallet is currently ${walletInfo.status} and cannot initiate transfers.`;
    }
    return '';
  };

  const handleProceedToReview = (e) => {
    e.preventDefault();
    setApiError('');
    const err = validateInput();
    if (err) {
      setValidationError(err);
      return;
    }
    setValidationError('');
    setIdempotencyKey(generateIdempotencyKey());
    setStep(2);
  };

  const handleConfirmTransfer = async () => {
    setApiError('');
    setSubmitting(true);

    const requestIdempotencyKey = idempotencyKey || generateIdempotencyKey();
    if (!idempotencyKey) {
      setIdempotencyKey(requestIdempotencyKey);
    }

    try {
      const payload = {
        recipientWalletReference: recipientRef.trim(),
        amount: Number(amountInput.trim()).toFixed(4),
        description: description.trim() || 'P2P Wallet Transfer',
        totpCode: totpCode.trim() || undefined,
      };

      const { data } = await client.post('/transfers', payload, {
        headers: {
          'Idempotency-Key': requestIdempotencyKey,
        },
      });

      const updatedBalance = Number(data.senderRemainingBalance ?? walletInfo.balance);
      setWalletInfo((prev) => ({ ...prev, balance: updatedBalance }));
      updateUserWallet({ balance: data.senderRemainingBalance });
      setReceipt({ ...data, idempotencyKey: requestIdempotencyKey });
      setStep(3);
    } catch (err) {
      const { message } = extractApiError(err, 'Transfer could not be completed.');
      setApiError(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDownloadReceipt = () => {
    if (!receipt) return;
    const lines = [
      '========================================',
      '           DHANPAY RECEIPT',
      '  Secure Transfers. Smarter Money Management.',
      '========================================',
      `Transaction Reference : ${receipt.transferReference}`,
      `Status                : ${receipt.status}`,
      `Timestamp             : ${formatTimestamp(receipt.createdAt)}`,
      `Sender Wallet         : ${receipt.senderWalletReference}`,
      `Recipient Wallet      : ${receipt.recipientWalletReference}`,
      `Amount                : ${formatINR(receipt.amount, { precise: true })}`,
      'Fee                   : ₹0.00',
      `Remaining Balance     : ${formatINR(receipt.senderRemainingBalance, { precise: true })}`,
      `Description           : ${receipt.description || '—'}`,
      `Idempotency Key       : ${receipt.idempotencyKey}`,
      '========================================',
    ].join('\n');

    const blob = new Blob([lines], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${receipt.transferReference || 'dhanpay-receipt'}.txt`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const handleReset = () => {
    setStep(1);
    setRecipientRef('');
    setAmountInput('');
    setDescription('');
    setIdempotencyKey('');
    setReceipt(null);
    setValidationError('');
    setApiError('');
  };

  const otherRecipients = DEMO_RECIPIENTS.filter(
    (r) => r.walletReference.toUpperCase() !== (walletInfo.walletReference || '').toUpperCase()
  );

  const numericAmount = Number(amountInput) || 0;
  const projectedBalance = Math.max(0, walletInfo.balance - numericAmount);
  const receiptBadge = receipt ? getStatusBadge(receipt.status, { preserveCode: true }) : null;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="no-print flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Send Money</h1>
          <p className="text-sm text-slate-500">
            Instant atomic P2P INR transfers backed by idempotent double-entry settlement.
          </p>
        </div>

        <ol className="flex items-center gap-2">
          {STEPS.map((s) => {
            const active = step === s.id;
            const completed = step > s.id;
            return (
              <li
                key={s.id}
                className={`inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-semibold transition ${
                  active
                    ? 'bg-[#05334D] text-white shadow-sm'
                    : completed
                    ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200'
                    : 'bg-slate-100 text-slate-400'
                }`}
              >
                <span>{s.id}.</span>
                <span>{s.label}</span>
              </li>
            );
          })}
        </ol>
      </div>

      {/* Balance Bar */}
      <div className="no-print flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-[#05334D] px-5 py-4 text-white shadow-sm">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10">
            <WalletIcon className="h-5 w-5 text-[#00D084]" />
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-300">
              Source Wallet ({walletInfo.walletReference || '—'})
            </p>
            <p className="text-xl font-bold tabular-nums text-white">
              {loadingWallet ? 'Loading balance...' : formatINR(walletInfo.balance)}
            </p>
          </div>
        </div>
        <span
          className={`rounded-full px-3 py-1 text-xs font-semibold ${
            walletInfo.status === 'ACTIVE'
              ? 'bg-emerald-400/20 text-emerald-200'
              : 'bg-amber-400/20 text-amber-200'
          }`}
        >
          {walletInfo.status}
        </span>
      </div>

      {limits && (
        <div className="no-print rounded-2xl border border-slate-100 bg-white px-5 py-3.5 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
            <span className="font-semibold text-slate-600">
              Rolling 24h Outflow Limit
            </span>
            <span className="font-semibold text-[#05334D] tabular-nums">
              {formatINR(limits.dailyUsed)} used of {formatINR(limits.dailyLimit)}
              <span className="ml-2 text-[#00C896]">
                ({formatINR(limits.dailyRemaining)} left)
              </span>
            </span>
          </div>
          <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-gradient-to-r from-[#05334D] to-[#00C896] transition-all"
              style={{
                width: `${Math.min(
                  100,
                  Math.round((Number(limits.dailyUsed || 0) / Math.max(1, Number(limits.dailyLimit || 100000))) * 100)
                )}%`,
              }}
            />
          </div>
        </div>
      )}

      {(validationError || apiError) && (
        <div
          role="alert"
          className="no-print flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
          <span>{validationError || apiError}</span>
        </div>
      )}

      {/* Step 1: Details */}
      {step === 1 && (
        <form
          onSubmit={handleProceedToReview}
          className="rounded-3xl border border-slate-100 bg-white p-6 shadow-card sm:p-7 space-y-5"
          noValidate
        >
          <div>
            <div className="flex items-center justify-between">
              <label
                htmlFor="recipient-ref"
                className="block text-xs font-semibold uppercase tracking-wider text-slate-500"
              >
                Recipient Wallet Reference
              </label>
              <button
                type="button"
                onClick={() => setScanQrOpen(true)}
                className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200/80 transition hover:bg-emerald-100"
              >
                <Scan className="h-3.5 w-3.5 text-[#00C896]" />
                Scan or Import QR
              </button>
            </div>
            <input
              id="recipient-ref"
              type="text"
              value={recipientRef}
              onChange={(e) => setRecipientRef(e.target.value)}
              placeholder="WLT-IND-10000003"
              className="mt-1.5 w-full rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-2.5 font-mono text-sm uppercase text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]/25"
            />
            {(beneficiaries.length > 0 || otherRecipients.length > 0) && (
              <div className="mt-2.5 flex flex-wrap items-center gap-2">
                <span className="text-xs text-slate-400">
                  {beneficiaries.length > 0 ? 'Saved contacts:' : 'Quick select:'}
                </span>
                {(beneficiaries.length > 0
                  ? beneficiaries.map((b) => ({ name: b.nickname, walletReference: b.walletReference }))
                  : otherRecipients
                ).map((item) => (
                  <button
                    key={item.walletReference}
                    type="button"
                    onClick={() => setRecipientRef(item.walletReference)}
                    className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-700 transition hover:border-[#00C896] hover:bg-emerald-50 hover:text-[#05334D]"
                  >
                    {item.name} ({item.walletReference})
                  </button>
                ))}
              </div>
            )}
          </div>

          <div>
            <label
              htmlFor="transfer-amount"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-500"
            >
              Amount (INR)
            </label>
            <div className="relative mt-1.5">
              <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-base font-bold text-slate-400">
                ₹
              </span>
              <input
                id="transfer-amount"
                type="number"
                step="0.01"
                min="1"
                max="50000"
                value={amountInput}
                onChange={(e) => setAmountInput(e.target.value)}
                placeholder="1000.00"
                className="w-full rounded-2xl border border-slate-200 bg-slate-50/70 py-2.5 pl-9 pr-4 text-base font-semibold text-slate-900 tabular-nums placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]/25"
              />
            </div>
            <p className="mt-1 text-xs text-slate-400">
              Minimum ₹1.00 • Maximum ₹50,000.00 per transfer • Zero fees
            </p>
          </div>

          <div>
            <label
              htmlFor="transfer-desc"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-500"
            >
              Description / Note (Optional)
            </label>
            <input
              id="transfer-desc"
              type="text"
              maxLength={255}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g., Monthly rent or dinner split"
              className="mt-1.5 w-full rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]/25"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <Link
              to="/dashboard"
              className="rounded-full border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
            >
              Cancel
            </Link>
            <button
              type="submit"
              className="inline-flex items-center gap-2 rounded-full bg-[#00D084] px-6 py-2.5 text-sm font-bold text-[#032539] shadow-md shadow-emerald-500/20 transition hover:bg-[#00C896]"
            >
              Review Transfer
              <ArrowRight className="h-4 w-4" />
            </button>
          </div>
        </form>
      )}

      {/* Step 2: Review & Confirm */}
      {step === 2 && (
        <div className="overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card">
          <div className="bg-[#05334D] px-6 py-5 text-white">
            <h2 className="text-lg font-bold">Review &amp; Confirm Transfer</h2>
            <p className="mt-1 text-xs text-slate-300">
              Verify recipient and INR amount carefully. Atomic ledger transfers cannot be reversed once settled.
            </p>
          </div>

          <div className="p-6 sm:p-7 space-y-5">
            <div className="divide-y divide-slate-100 rounded-2xl border border-slate-100 bg-slate-50/70">
              <div className="flex items-center justify-between px-4 py-3 text-sm">
                <span className="text-slate-500">From Wallet</span>
                <span className="font-mono font-semibold text-slate-900">{walletInfo.walletReference}</span>
              </div>
              <div className="flex items-center justify-between px-4 py-3 text-sm">
                <span className="text-slate-500">To Recipient Wallet</span>
                <span className="font-mono font-semibold text-[#05334D]">{recipientRef.trim()}</span>
              </div>
              <div className="flex items-center justify-between px-4 py-3 text-sm">
                <span className="text-slate-500">Transfer Amount</span>
                <span className="text-xl font-bold text-slate-900 tabular-nums">
                  {formatINR(numericAmount, { precise: true })}
                </span>
              </div>
              <div className="flex items-center justify-between px-4 py-3 text-sm">
                <span className="text-slate-500">Ledger Fee</span>
                <span className="font-semibold text-emerald-600">₹0.00</span>
              </div>
              <div className="flex items-center justify-between px-4 py-3 text-sm">
                <span className="text-slate-500">Projected Remaining Balance</span>
                <span className="font-semibold text-slate-800 tabular-nums">{formatINR(projectedBalance)}</span>
              </div>
              <div className="flex items-center justify-between px-4 py-3 text-sm">
                <span className="text-slate-500">Note</span>
                <span className="text-slate-700">{description.trim() || 'P2P Wallet Transfer'}</span>
              </div>
            </div>

            <div className="flex items-start gap-2.5 rounded-2xl border border-emerald-200/80 bg-emerald-50/70 px-4 py-3 text-xs text-emerald-900">
              <KeyRound className="mt-0.5 h-4 w-4 shrink-0 text-[#00C896]" />
              <div>
                <p className="font-semibold">Idempotent Protection Enabled</p>
                <p className="mt-0.5 font-mono text-[11px] text-emerald-700 break-all">
                  Idempotency-Key: {idempotencyKey}
                </p>
              </div>
            </div>

            <div className="flex items-start gap-2 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
              <span>Double-entry debit and credit entries commit atomically inside PostgreSQL/MySQL.</span>
            </div>

            {numericAmount > 10000 && (
              <div className="rounded-2xl border border-emerald-300 bg-emerald-50/70 p-4 space-y-2">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="h-4 w-4 text-[#00C896]" />
                  <span className="text-xs font-bold text-slate-800">
                    2FA Step-Up Protection Required (Transfers &gt; ₹10,000)
                  </span>
                </div>
                <p className="text-[11px] text-slate-500">
                  If 2FA is active on your account, enter your 6-digit authenticator code (or backup code):
                </p>
                <input
                  type="text"
                  maxLength={10}
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value)}
                  placeholder="e.g. 123456"
                  className="w-full rounded-xl border border-slate-200 bg-white p-2.5 font-mono text-center text-sm font-bold tracking-widest text-slate-900 focus:border-[#00C896] focus:outline-none"
                />
              </div>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
              <button
                type="button"
                disabled={submitting}
                onClick={() => setStep(1)}
                className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50"
              >
                <ArrowLeft className="h-4 w-4" />
                Back to Edit
              </button>

              <button
                type="button"
                disabled={submitting}
                onClick={handleConfirmTransfer}
                className="inline-flex items-center gap-2 rounded-full bg-[#00D084] px-6 py-2.5 text-sm font-bold text-[#032539] shadow-md shadow-emerald-500/20 transition hover:bg-[#00C896] disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Send className="h-4 w-4" />
                {submitting ? 'Settling Transfer...' : 'Confirm & Send Now'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Step 3: Receipt */}
      {step === 3 && receipt && (
        <div className="rounded-3xl border border-emerald-200 bg-white p-6 shadow-card sm:p-8 space-y-6">
          <div className="flex flex-col items-center text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-[#00C896] ring-1 ring-emerald-200">
              <CheckCircle2 className="h-8 w-8" />
            </div>
            <h2 className="mt-4 text-2xl font-bold text-slate-900">Transfer Successful</h2>
            <p className="mt-1 text-sm text-slate-500">
              Settled on DhanPay Double-Entry Ledger on {formatTimestamp(receipt.createdAt)}
            </p>
            <p className="mt-3 text-4xl font-bold text-slate-900 tabular-nums">
              {formatINR(receipt.amount)}
            </p>
          </div>

          <div className="divide-y divide-slate-100 rounded-2xl border border-slate-100 bg-slate-50/70">
            <div className="flex items-center justify-between px-4 py-3 text-sm">
              <span className="text-slate-500">Transaction Reference</span>
              <span className="font-mono font-semibold text-slate-900">{receipt.transferReference}</span>
            </div>
            <div className="flex items-center justify-between px-4 py-3 text-sm">
              <span className="text-slate-500">Status</span>
              {receiptBadge && (
                <span className={`inline-flex items-center gap-1 rounded-full px-3 py-0.5 text-xs font-semibold ${receiptBadge.pill}`}>
                  <ShieldCheck className="h-3.5 w-3.5" />
                  {receiptBadge.label}
                </span>
              )}
            </div>
            <div className="flex items-center justify-between px-4 py-3 text-sm">
              <span className="text-slate-500">Sender Wallet</span>
              <span className="font-mono text-slate-800">{receipt.senderWalletReference}</span>
            </div>
            <div className="flex items-center justify-between px-4 py-3 text-sm">
              <span className="text-slate-500">Recipient Wallet</span>
              <span className="font-mono text-[#05334D] font-semibold">{receipt.recipientWalletReference}</span>
            </div>
            <div className="flex items-center justify-between px-4 py-3 text-sm">
              <span className="text-slate-500">Remaining Balance</span>
              <span className="font-semibold text-slate-900 tabular-nums">
                {formatINR(receipt.senderRemainingBalance)}
              </span>
            </div>
            <div className="flex items-center justify-between px-4 py-3 text-sm">
              <span className="text-slate-500">Description</span>
              <span className="text-slate-700">{receipt.description || '—'}</span>
            </div>
          </div>

          <div className="no-print flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => window.print()}
                className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
              >
                <Printer className="h-4 w-4" />
                Print Receipt
              </button>
              <button
                type="button"
                onClick={handleDownloadReceipt}
                className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
              >
                <Download className="h-4 w-4" />
                Download Receipt
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleReset}
                className="rounded-full bg-[#05334D] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#084566]"
              >
                Send Another
              </button>
              <Link
                to="/dashboard"
                className="rounded-full bg-[#00D084] px-4 py-2 text-xs font-bold text-[#032539] transition hover:bg-[#00C896]"
              >
                Back to Dashboard
              </Link>
            </div>
          </div>
        </div>
      )}

      <ScanQrModal
        isOpen={scanQrOpen}
        onClose={() => setScanQrOpen(false)}
        onVerifiedCounterparty={(data) => {
          setRecipientRef(data.recipientWalletReference);
          if (data.amount) setAmountInput(data.amount);
          if (data.description) setDescription(data.description);
        }}
      />
    </div>
  );
}
