import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowUpRight,
  Check,
  CheckCircle2,
  ChevronRight,
  Copy,
  Download,
  FileText,
  Minus,
  Plus,
  QrCode,
  RefreshCw,
  ShieldCheck,
  UserPlus,
  Wallet as WalletIcon,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { useAuth } from '../context/AuthContext';
import ReceiveQrModal from '../components/ReceiveQrModal';
import StatementModal from '../components/StatementModal';
import {
  exportTransactionsCsv,
  formatINR,
  formatShortTime,
  generateIdempotencyKey,
  getStatusBadge,
} from '../utils/formatters';

const WALLET_REF_REGEX = /^WLT-[A-Z0-9-]+$/i;

const QUICK_RECIPIENTS = [
  { label: 'Priya', ref: 'WLT-IND-10000003' },
  { label: 'Rohan', ref: 'WLT-IND-10000004' },
  { label: 'Aarav', ref: 'WLT-IND-10000002' },
];

function buildSmoothPath(points) {
  if (points.length === 0) return '';
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
  let d = `M ${points[0].x} ${points[0].y}`;
  for (let i = 0; i < points.length - 1; i += 1) {
    const p0 = points[i];
    const p1 = points[i + 1];
    const midX = (p0.x + p1.x) / 2;
    d += ` C ${midX} ${p0.y}, ${midX} ${p1.y}, ${p1.x} ${p1.y}`;
  }
  return d;
}

function LedgerCashFlowChart({ transactions, range }) {
  const series = useMemo(() => {
    const now = Date.now();
    const cutoffDays = range === '7d' ? 7 : range === '1m' ? 30 : null;
    const filtered = transactions.filter((tx) => {
      if (!cutoffDays || !tx.createdAt) return true;
      const ageDays = (now - new Date(tx.createdAt).getTime()) / (1000 * 60 * 60 * 24);
      return ageDays <= cutoffDays;
    });

    const bucketsMap = new Map();
    const sorted = [...filtered].sort(
      (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    );

    sorted.forEach((tx) => {
      const dateObj = tx.createdAt ? new Date(tx.createdAt) : new Date();
      const key = Number.isNaN(dateObj.getTime())
        ? 'Recent'
        : new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short' }).format(dateObj);
      if (!bucketsMap.has(key)) {
        bucketsMap.set(key, { label: key, credit: 0, debit: 0 });
      }
      const entry = bucketsMap.get(key);
      const amt = Number(tx.amount || 0);
      if (tx.direction === 'CREDIT') {
        entry.credit += amt;
      } else {
        entry.debit += amt;
      }
    });

    const list = Array.from(bucketsMap.values()).slice(-7);
    if (list.length === 1) {
      return [{ label: 'Prior', credit: 0, debit: 0 }, ...list];
    }
    return list;
  }, [transactions, range]);

  const width = 560;
  const height = 180;
  const padLeft = 48;
  const padRight = 18;
  const padTop = 26;
  const padBottom = 26;
  const plotW = width - padLeft - padRight;
  const plotH = height - padTop - padBottom;

  if (series.length === 0) {
    return (
      <div className="flex h-44 items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 text-xs text-slate-400">
        Activity curve populates automatically after your first INR transfer.
      </div>
    );
  }

  const maxVal = Math.max(
    100,
    ...series.map((s) => Math.max(s.credit, s.debit))
  );

  const creditPts = series.map((item, idx) => ({
    x: padLeft + (series.length === 1 ? plotW / 2 : (idx / (series.length - 1)) * plotW),
    y: padTop + plotH - (item.credit / maxVal) * plotH,
    value: item.credit,
    label: item.label,
  }));

  const debitPts = series.map((item, idx) => ({
    x: padLeft + (series.length === 1 ? plotW / 2 : (idx / (series.length - 1)) * plotW),
    y: padTop + plotH - (item.debit / maxVal) * plotH,
    value: item.debit,
    label: item.label,
  }));

  const creditPath = buildSmoothPath(creditPts);
  const debitPath = buildSmoothPath(debitPts);
  const creditArea = `${creditPath} L ${creditPts[creditPts.length - 1].x} ${padTop + plotH} L ${creditPts[0].x} ${padTop + plotH} Z`;

  const peakCredit = creditPts.reduce(
    (best, pt) => (pt.value >= best.value ? pt : best),
    creditPts[0]
  );

  const yTicks = [0, 0.5, 1].map((ratio) => ({
    y: padTop + plotH - ratio * plotH,
    text: ratio === 0 ? '0' : `${Math.round((maxVal * ratio) / 100) / 10}k`,
  }));

  return (
    <div className="w-full overflow-hidden">
      <svg viewBox={`0 0 ${width} ${height}`} className="h-48 w-full">
        <defs>
          <linearGradient id="dhanpayMintArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#00C896" stopOpacity="0.28" />
            <stop offset="100%" stopColor="#00C896" stopOpacity="0.02" />
          </linearGradient>
        </defs>

        {yTicks.map((tick) => (
          <g key={tick.y}>
            <line
              x1={padLeft}
              y1={tick.y}
              x2={width - padRight}
              y2={tick.y}
              stroke="#E2E8F0"
              strokeDasharray="3 3"
              strokeWidth="1"
            />
            <text
              x={padLeft - 8}
              y={tick.y + 4}
              textAnchor="end"
              className="fill-slate-400 text-[10px]"
            >
              ₹{tick.text}
            </text>
          </g>
        ))}

        {series.map((item, idx) => {
          const x = creditPts[idx].x;
          return (
            <g key={`${item.label}-${idx}`}>
              <line
                x1={x}
                y1={padTop}
                x2={x}
                y2={padTop + plotH}
                stroke="#F1F5F9"
                strokeWidth="1"
              />
              <text
                x={x}
                y={height - 6}
                textAnchor="middle"
                className="fill-slate-400 text-[10px]"
              >
                {item.label}
              </text>
            </g>
          );
        })}

        <path d={creditArea} fill="url(#dhanpayMintArea)" />
        <path d={debitPath} fill="none" stroke="#05334D" strokeWidth="2.2" strokeLinecap="round" />
        <path d={creditPath} fill="none" stroke="#00C896" strokeWidth="2.5" strokeLinecap="round" />

        {peakCredit && (
          <g>
            <circle cx={peakCredit.x} cy={peakCredit.y} r="4.5" fill="#00C896" stroke="#FFFFFF" strokeWidth="2" />
            <rect
              x={Math.min(width - 94, Math.max(padLeft, peakCredit.x - 42))}
              y={Math.max(2, peakCredit.y - 24)}
              width="84"
              height="19"
              rx="6"
              fill="#05334D"
            />
            <text
              x={Math.min(width - 52, Math.max(padLeft + 42, peakCredit.x))}
              y={Math.max(14.5, peakCredit.y - 11.5)}
              textAnchor="middle"
              className="fill-white text-[9.5px] font-semibold"
            >
              {formatINR(peakCredit.value)}
            </text>
          </g>
        )}
      </svg>
    </div>
  );
}

export default function DashboardPage() {
  const { user, updateUserWallet } = useAuth();

  const [wallet, setWallet] = useState(() => ({
    walletReference: user?.walletReference || '',
    balance: user?.balance ?? 0,
    currency: user?.currency || 'INR',
    status: 'ACTIVE',
    ledgerAccountNumber: user?.ledgerAccountNumber || '',
  }));
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [copied, setCopied] = useState(false);

  // SwiftPay-format controls
  const [chartRange, setChartRange] = useState('All');
  const [quickMode, setQuickMode] = useState('WALLET'); // 'WALLET' | 'RECEIVE'
  const [recipientRef, setRecipientRef] = useState('');
  const [amountInput, setAmountInput] = useState('');
  const [noteInput, setNoteInput] = useState('');
  const [quickSending, setQuickSending] = useState(false);
  const [quickError, setQuickError] = useState('');
  const [quickSuccess, setQuickSuccess] = useState('');

  // Top-up modal state
  const [fundModalOpen, setFundModalOpen] = useState(false);
  const [fundAmount, setFundAmount] = useState('5000');
  const [fundSource, setFundSource] = useState('UPI Instant');
  const [funding, setFunding] = useState(false);
  const [fundError, setFundError] = useState('');
  const [fundBanner, setFundBanner] = useState('');
  const [receiveQrOpen, setReceiveQrOpen] = useState(false);
  const [statementModalOpen, setStatementModalOpen] = useState(false);

  // Saved beneficiaries state
  const [beneficiaries, setBeneficiaries] = useState([]);
  const [showAddContact, setShowAddContact] = useState(false);
  const [contactNickname, setContactNickname] = useState('');
  const [savingContact, setSavingContact] = useState(false);

  // Payment requests state
  const [paymentRequests, setPaymentRequests] = useState([]);
  const [requestPayerRef, setRequestPayerRef] = useState('');
  const [requestAmount, setRequestAmount] = useState('');
  const [requestNote, setRequestNote] = useState('');
  const [requestingMoney, setRequestingMoney] = useState(false);
  const [requestBusyRef, setRequestBusyRef] = useState('');

  const loadDashboard = useCallback(
    async ({ silent = false } = {}) => {
      if (silent) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setErrorMsg('');

      try {
        const [walletRes, historyRes, beneficiariesRes, requestsRes] = await Promise.all([
          client.get('/wallets/me'),
          client.get('/transfers', { params: { page: 0, size: 50 } }),
          client.get('/beneficiaries').catch(() => ({ data: [] })),
          client.get('/requests').catch(() => ({ data: [] })),
        ]);

        const walletData = walletRes.data;
        setWallet(walletData);
        updateUserWallet({
          walletReference: walletData.walletReference,
          balance: walletData.balance,
          currency: walletData.currency,
          ledgerAccountNumber: walletData.ledgerAccountNumber,
        });

        setTransactions(historyRes.data?.content || []);
        setBeneficiaries(Array.isArray(beneficiariesRes.data) ? beneficiariesRes.data : []);
        setPaymentRequests(Array.isArray(requestsRes.data) ? requestsRes.data : []);
      } catch (err) {
        const { message } = extractApiError(err, 'Unable to load wallet dashboard.');
        setErrorMsg(message);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [updateUserWallet]
  );

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    const handleLiveNotification = () => {
      loadDashboard({ silent: true });
    };
    window.addEventListener('dhanpay:notification', handleLiveNotification);
    return () => window.removeEventListener('dhanpay:notification', handleLiveNotification);
  }, [loadDashboard]);

  const handleCopyWalletRef = async () => {
    if (!wallet.walletReference) return;
    try {
      await navigator.clipboard.writeText(wallet.walletReference);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  const handleQuickSend = async (e) => {
    e.preventDefault();
    setQuickError('');
    setQuickSuccess('');

    const normalizedRef = recipientRef.trim().toUpperCase();
    const numericAmount = Number(amountInput);

    if (!normalizedRef) {
      setQuickError('Enter recipient wallet reference.');
      return;
    }
    if (!WALLET_REF_REGEX.test(normalizedRef)) {
      setQuickError('Format must look like WLT-IND-10000003.');
      return;
    }
    if (wallet.walletReference && normalizedRef === wallet.walletReference.toUpperCase()) {
      setQuickError('Cannot transfer to your own wallet.');
      return;
    }
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setQuickError('Enter an amount greater than ₹0.');
      return;
    }
    if (numericAmount > Number(wallet.balance ?? 0)) {
      setQuickError('Insufficient INR wallet balance.');
      return;
    }

    setQuickSending(true);
    try {
      const response = await client.post(
        '/transfers',
        {
          recipientWalletReference: normalizedRef,
          amount: Number(numericAmount.toFixed(2)),
          description: noteInput.trim() || 'Quick Wallet Transfer',
        },
        {
          headers: { 'Idempotency-Key': generateIdempotencyKey() },
        }
      );

      const receipt = response.data;
      setQuickSuccess(`Sent ${formatINR(receipt.amount)} (${receipt.transferReference})`);
      setRecipientRef('');
      setAmountInput('');
      setNoteInput('');
      await loadDashboard({ silent: true });
    } catch (err) {
      const { message } = extractApiError(err, 'Quick transfer failed.');
      setQuickError(message);
    } finally {
      setQuickSending(false);
    }
  };

  const handleFundWallet = async (e) => {
    e.preventDefault();
    setFundError('');
    const numericAmount = Number(fundAmount);
    if (!Number.isFinite(numericAmount) || numericAmount < 1) {
      setFundError('Enter a funding amount of at least ₹1.00.');
      return;
    }
    if (numericAmount > 100000) {
      setFundError('Single funding limit is ₹1,00,000.00.');
      return;
    }

    setFunding(true);
    try {
      const { data } = await client.post(
        '/wallets/topup',
        {
          amount: Number(numericAmount.toFixed(2)),
          sourceLabel: fundSource,
        },
        {
          headers: { 'Idempotency-Key': generateIdempotencyKey() },
        }
      );
      setFundBanner(`Added ${formatINR(data.amount)} via ${data.sourceLabel} (${data.topUpReference}).`);
      setFundModalOpen(false);
      await loadDashboard({ silent: true });
    } catch (err) {
      const { message } = extractApiError(err, 'Wallet funding failed.');
      setFundError(message);
    } finally {
      setFunding(false);
    }
  };

  const handleSaveBeneficiary = async () => {
    setQuickError('');
    setQuickSuccess('');
    const normalizedRef = recipientRef.trim().toUpperCase();
    const nickname = contactNickname.trim();
    if (!WALLET_REF_REGEX.test(normalizedRef)) {
      setQuickError('Enter a valid WLT-IND reference to save.');
      return;
    }
    if (nickname.length < 2) {
      setQuickError('Nickname must be at least 2 characters.');
      return;
    }

    setSavingContact(true);
    try {
      const { data } = await client.post('/beneficiaries', {
        walletReference: normalizedRef,
        nickname,
      });
      setBeneficiaries((prev) => [data, ...prev]);
      setContactNickname('');
      setShowAddContact(false);
      setQuickSuccess(`Saved ${data.nickname} to contacts.`);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not save beneficiary.');
      setQuickError(message);
    } finally {
      setSavingContact(false);
    }
  };

  const handleDeleteBeneficiary = async (id) => {
    try {
      await client.delete(`/beneficiaries/${id}`);
      setBeneficiaries((prev) => prev.filter((b) => b.id !== id));
    } catch (err) {
      const { message } = extractApiError(err, 'Could not remove contact.');
      setQuickError(message);
    }
  };

  const handleCreatePaymentRequest = async (e) => {
    e.preventDefault();
    setQuickError('');
    setQuickSuccess('');
    const normalizedRef = requestPayerRef.trim().toUpperCase();
    const numericAmount = Number(requestAmount);

    if (!WALLET_REF_REGEX.test(normalizedRef)) {
      setQuickError('Enter payer wallet reference (e.g. WLT-IND-10000003).');
      return;
    }
    if (wallet.walletReference && normalizedRef === wallet.walletReference.toUpperCase()) {
      setQuickError('Cannot request money from your own wallet.');
      return;
    }
    if (!Number.isFinite(numericAmount) || numericAmount < 1) {
      setQuickError('Enter a request amount of at least ₹1.00.');
      return;
    }

    setRequestingMoney(true);
    try {
      const { data } = await client.post('/requests', {
        payerWalletReference: normalizedRef,
        amount: Number(numericAmount.toFixed(2)),
        note: requestNote.trim() || undefined,
      });
      setPaymentRequests((prev) => [data, ...prev]);
      setQuickSuccess(`Requested ${formatINR(data.amount)} (${data.reference}).`);
      setRequestPayerRef('');
      setRequestAmount('');
      setRequestNote('');
    } catch (err) {
      const { message } = extractApiError(err, 'Could not create payment request.');
      setQuickError(message);
    } finally {
      setRequestingMoney(false);
    }
  };

  const handlePayRequest = async (reference) => {
    setErrorMsg('');
    setRequestBusyRef(reference);
    try {
      const { data } = await client.post(
        `/requests/${reference}/pay`,
        {},
        { headers: { 'Idempotency-Key': generateIdempotencyKey() } }
      );
      setFundBanner(`Paid request ${reference} (${formatINR(data.amount)} settled).`);
      await loadDashboard({ silent: true });
    } catch (err) {
      const { message } = extractApiError(err, 'Unable to settle payment request.');
      setErrorMsg(message);
    } finally {
      setRequestBusyRef('');
    }
  };

  const handleDeclineRequest = async (reference) => {
    setErrorMsg('');
    setRequestBusyRef(reference);
    try {
      const { data } = await client.post(`/requests/${reference}/decline`);
      setPaymentRequests((prev) => prev.map((r) => (r.reference === reference ? data : r)));
    } catch (err) {
      const { message } = extractApiError(err, 'Unable to update payment request.');
      setErrorMsg(message);
    } finally {
      setRequestBusyRef('');
    }
  };

  const summary = useMemo(() => {
    const now = Date.now();
    let totalIncome = 0;
    let totalOutcome = 0;
    let weekVolume = 0;
    let monthVolume = 0;

    transactions.forEach((tx) => {
      if (tx.status === 'FAILED') return;
      const amt = Number(tx.amount || 0);
      if (tx.direction === 'CREDIT') {
        totalIncome += amt;
      } else {
        totalOutcome += amt;
      }
      if (tx.createdAt) {
        const ageDays = (now - new Date(tx.createdAt).getTime()) / (1000 * 60 * 60 * 24);
        if (ageDays <= 7) weekVolume += amt;
        if (ageDays <= 30) monthVolume += amt;
      }
    });

    return { totalIncome, totalOutcome, weekVolume, monthVolume };
  }, [transactions]);

  const walletBadge = getStatusBadge(wallet.status || 'ACTIVE');
  const recentFive = transactions.slice(0, 4);

  return (
    <div className="space-y-6">
      {errorMsg && (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
          <span>{errorMsg}</span>
        </div>
      )}

      {fundBanner && (
        <div className="flex items-center justify-between gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00C896]" />
            <span>{fundBanner}</span>
          </div>
          <button
            type="button"
            onClick={() => setFundBanner('')}
            className="text-emerald-700 hover:text-emerald-950"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-12 lg:items-start">
        {/* Left / Primary Column (Balance + Cash Flow Chart + Transactions) */}
        <div className="space-y-5 lg:col-span-8">
          {/* 1. YOUR BALANCE Card */}
          <section className="relative overflow-hidden rounded-3xl border border-slate-100 bg-white p-6 shadow-card">
            <div className="pointer-events-none absolute -bottom-10 right-12 h-36 w-56 rounded-full bg-[#DDF2F1]/70 blur-2xl" />

            <div className="relative flex flex-wrap items-center justify-between gap-4">
              <div>
                <div className="flex items-center gap-2.5">
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                    Your Balance
                  </span>
                  <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${walletBadge.pill}`}>
                    <span className="h-1.5 w-1.5 rounded-full bg-current" />
                    {walletBadge.label}
                  </span>
                </div>

                <p className="mt-2 text-3xl font-bold tracking-tight text-[#0F172A] tabular-nums sm:text-4xl">
                  {loading ? '₹—' : formatINR(wallet.balance)}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  Base currency <span className="font-semibold text-[#00C896]">{wallet.currency || 'INR'}</span>
                  {wallet.ledgerAccountNumber ? ` • GL ${wallet.ledgerAccountNumber}` : ''}
                </p>
              </div>

              <div className="flex items-center gap-2.5">
                <button
                  type="button"
                  onClick={() => loadDashboard({ silent: true })}
                  disabled={refreshing || loading}
                  title="Refresh balance"
                  className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 transition hover:border-[#00C896] hover:text-[#05334D] disabled:opacity-50"
                >
                  <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin text-[#00C896]' : ''}`} />
                </button>
                <button
                  type="button"
                  onClick={() => setReceiveQrOpen(true)}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-sm transition hover:border-[#00C896] hover:bg-emerald-50 hover:text-[#05334D]"
                >
                  <QrCode className="h-4 w-4 text-[#00C896]" />
                  My QR
                </button>
                <button
                  type="button"
                  onClick={() => setStatementModalOpen(true)}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-sm transition hover:border-[#00C896] hover:bg-emerald-50 hover:text-[#05334D]"
                >
                  <FileText className="h-4 w-4 text-[#00C896]" />
                  Statement
                </button>
                <button
                  type="button"
                  disabled={wallet.status !== 'ACTIVE'}
                  onClick={() => {
                    setFundError('');
                    setFundModalOpen(true);
                  }}
                  className="inline-flex items-center gap-2 rounded-xl bg-[#05334D] px-5 py-2.5 text-xs font-semibold text-white shadow-sm transition hover:bg-[#084566] disabled:opacity-50"
                >
                  Fund Account
                </button>
              </div>
            </div>
          </section>

          {/* 2. Activity Spline Chart Card */}
          <section className="rounded-3xl border border-slate-100 bg-white p-5 sm:p-6 shadow-card space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-4 text-xs">
                <span className="inline-flex items-center gap-1.5 font-semibold text-slate-700">
                  <span className="h-2.5 w-2.5 rounded-full bg-[#00C896]" />
                  Credits
                </span>
                <span className="inline-flex items-center gap-1.5 font-semibold text-slate-700">
                  <span className="h-2.5 w-2.5 rounded-full bg-[#05334D]" />
                  Debits
                </span>
              </div>

              <span className="text-[11px] font-medium text-slate-400">
                Real-time INR ledger flow
              </span>
            </div>

            <LedgerCashFlowChart transactions={transactions} range={chartRange} />

            <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
              {[
                { key: '7d', label: '7d' },
                { key: '1m', label: '1m' },
                { key: 'All', label: 'All' },
              ].map((pill) => (
                <button
                  key={pill.key}
                  type="button"
                  onClick={() => setChartRange(pill.key)}
                  className={`rounded-lg px-4 py-1.5 text-xs font-semibold transition ${
                    chartRange === pill.key
                      ? 'bg-[#00D084] text-[#032539] shadow-sm'
                      : 'bg-slate-100 text-slate-500 hover:bg-slate-200/70 hover:text-slate-700'
                  }`}
                >
                  {pill.label}
                </button>
              ))}
            </div>
          </section>

          {/* 3. Pending Payment Requests (when present) */}
          {paymentRequests.filter((r) => r.status === 'PENDING').length > 0 && (
            <section className="rounded-3xl border border-emerald-200/80 bg-white p-5 shadow-card space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-bold uppercase tracking-wider text-[#05334D]">
                  Pending Payment Requests
                </h2>
                <span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-700">
                  {paymentRequests.filter((r) => r.status === 'PENDING').length} active
                </span>
              </div>
              <div className="divide-y divide-slate-100">
                {paymentRequests
                  .filter((r) => r.status === 'PENDING')
                  .slice(0, 4)
                  .map((req) => {
                    const isIncoming = req.direction === 'INCOMING';
                    const isBusy = requestBusyRef === req.reference;
                    return (
                      <div
                        key={req.reference}
                        className="flex flex-wrap items-center justify-between gap-3 py-2.5 first:pt-0 last:pb-0"
                      >
                        <div className="min-w-0">
                          <p className="text-sm font-semibold text-slate-800">
                            {isIncoming ? 'Request from ' : 'Requested from '}
                            {req.counterpartyName} ({req.counterpartyWalletReference})
                          </p>
                          <p className="text-xs text-slate-400">
                            {req.note || req.reference} • {formatShortTime(req.createdAt)}
                          </p>
                        </div>

                        <div className="flex items-center gap-2 ml-auto">
                          <span className="text-sm font-bold text-[#05334D] tabular-nums mr-1">
                            {formatINR(req.amount)}
                          </span>
                          {isIncoming && (
                            <button
                              type="button"
                              disabled={isBusy}
                              onClick={() => handlePayRequest(req.reference)}
                              className="rounded-full bg-[#00D084] px-3.5 py-1.5 text-xs font-bold text-[#032539] transition hover:bg-[#00C896] disabled:opacity-50"
                            >
                              {isBusy ? '...' : 'Pay Now'}
                            </button>
                          )}
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => handleDeclineRequest(req.reference)}
                            className="rounded-full border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:bg-rose-50 hover:text-rose-600 disabled:opacity-50"
                          >
                            {isIncoming ? 'Decline' : 'Cancel'}
                          </button>
                        </div>
                      </div>
                    );
                  })}
              </div>
            </section>
          )}

          {/* 4. Bottom Transactions Card */}
          <section className="rounded-3xl border border-slate-100 bg-white p-6 shadow-card space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-lg font-bold text-[#0F172A]">Transactions</h2>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={transactions.length === 0}
                  onClick={() => exportTransactionsCsv(transactions)}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:bg-slate-200/70 disabled:opacity-40"
                >
                  <Download className="h-3.5 w-3.5" />
                  Export CSV
                </button>
                <Link
                  to="/history"
                  className="inline-flex items-center gap-1 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-semibold text-[#05334D] transition hover:bg-[#DDF2F1]"
                >
                  See All
                  <ChevronRight className="h-3.5 w-3.5" />
                </Link>
              </div>
            </div>

            {loading ? (
              <div className="space-y-3">
                {[1, 2, 3].map((idx) => (
                  <div key={idx} className="h-14 animate-pulse rounded-2xl bg-slate-100" />
                ))}
              </div>
            ) : recentFive.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 py-8 text-center text-xs text-slate-400">
                No ledger transfers recorded yet. Use Quick Transfer on the right to begin.
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {recentFive.map((tx) => {
                  const isCredit = tx.direction === 'CREDIT';
                  const badge = getStatusBadge(tx.status);

                  return (
                    <div
                      key={tx.reference}
                      className="flex items-center justify-between gap-3 py-3 first:pt-1 last:pb-1"
                    >
                      <div className="flex items-center gap-3.5 min-w-0">
                        <div
                          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${
                            isCredit
                              ? 'bg-emerald-50 text-[#00C896]'
                              : 'bg-slate-100 text-[#05334D]'
                          }`}
                        >
                          {isCredit ? <Plus className="h-4 w-4" /> : <Minus className="h-4 w-4" />}
                        </div>

                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-slate-800">
                            {isCredit ? 'Received from ' : 'Transfer to '}
                            {tx.counterpartyName || tx.counterpartyWalletReference}
                          </p>
                          <p className="truncate text-xs text-slate-400">
                            {tx.description || tx.reference}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3 shrink-0">
                        <span className={`hidden sm:inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${badge.pill}`}>
                          {badge.label}
                        </span>
                        <div className="text-right">
                          <p className="text-sm font-bold text-slate-900 tabular-nums">
                            {formatINR(tx.amount)}
                          </p>
                          <p className="text-[11px] text-slate-400">
                            {formatShortTime(tx.createdAt)}
                          </p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </div>

        {/* Right Column (Quick Transfer + Wallet Summary) */}
        <div className="space-y-6 lg:col-span-4">
          {/* 1. Quick Transfer Card */}
          <section className="rounded-3xl border border-slate-100 bg-white p-6 shadow-card space-y-5">
            <h2 className="text-lg font-bold text-[#0F172A]">Quick Transfer</h2>

            <div className="grid grid-cols-2 gap-2.5">
              <button
                type="button"
                onClick={() => {
                  setQuickMode('WALLET');
                  setQuickError('');
                  setQuickSuccess('');
                }}
                className={`rounded-xl px-3 py-2 text-xs font-semibold transition ${
                  quickMode === 'WALLET'
                    ? 'border border-[#05334D] bg-white text-[#05334D] shadow-sm'
                    : 'bg-slate-100 text-slate-500 hover:text-slate-700'
                }`}
              >
                Via Wallet Ref
              </button>
              <button
                type="button"
                onClick={() => {
                  setQuickMode('RECEIVE');
                  setQuickError('');
                  setQuickSuccess('');
                }}
                className={`rounded-xl px-3 py-2 text-xs font-semibold transition ${
                  quickMode === 'RECEIVE'
                    ? 'border border-[#05334D] bg-white text-[#05334D] shadow-sm'
                    : 'bg-slate-100 text-slate-500 hover:text-slate-700'
                }`}
              >
                Receive ID
              </button>
            </div>

            {quickMode === 'WALLET' ? (
              <form onSubmit={handleQuickSend} className="space-y-3" noValidate>
                {quickError && (
                  <div className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-600">
                    {quickError}
                  </div>
                )}

                {quickSuccess && (
                  <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                    <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00C896]" />
                    <span>{quickSuccess}</span>
                  </div>
                )}

                <input
                  type="text"
                  value={recipientRef}
                  onChange={(e) => setRecipientRef(e.target.value)}
                  placeholder="Enter Wallet Ref (WLT-IND-...)"
                  className="w-full rounded-xl bg-slate-100/90 px-4 py-3 font-mono text-xs uppercase text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]"
                />

                <div className="space-y-2">
                  <div className="flex flex-wrap items-center gap-1.5">
                    {beneficiaries.map((b) => (
                      <span
                        key={b.id}
                        className="inline-flex items-center gap-1 rounded-full bg-emerald-50 pl-2.5 pr-1.5 py-1 text-[11px] font-semibold text-[#05334D] ring-1 ring-emerald-200/80"
                      >
                        <button
                          type="button"
                          onClick={() => setRecipientRef(b.walletReference)}
                          title={`${b.fullName} (${b.walletReference})`}
                          className="hover:text-[#00C896]"
                        >
                          {b.nickname}
                        </button>
                        <button
                          type="button"
                          aria-label={`Remove ${b.nickname}`}
                          onClick={() => handleDeleteBeneficiary(b.id)}
                          className="rounded-full p-0.5 text-slate-400 hover:bg-rose-100 hover:text-rose-600"
                        >
                          <X className="h-2.5 w-2.5" />
                        </button>
                      </span>
                    ))}

                    {beneficiaries.length === 0 &&
                      QUICK_RECIPIENTS.filter(
                        (r) => r.ref.toUpperCase() !== (wallet.walletReference || '').toUpperCase()
                      ).map((item) => (
                        <button
                          key={item.ref}
                          type="button"
                          onClick={() => setRecipientRef(item.ref)}
                          className="rounded-full bg-[#DDF2F1]/70 px-2.5 py-1 text-[11px] font-semibold text-[#05334D] transition hover:bg-[#00D084]/30"
                        >
                          {item.label}
                        </button>
                      ))}

                    <button
                      type="button"
                      onClick={() => setShowAddContact((v) => !v)}
                      className="inline-flex items-center gap-1 rounded-full border border-dashed border-slate-300 px-2.5 py-1 text-[11px] font-semibold text-slate-500 transition hover:border-[#00C896] hover:text-[#05334D]"
                    >
                      <UserPlus className="h-3 w-3" />
                      {showAddContact ? 'Cancel' : 'Save Ref'}
                    </button>
                  </div>

                  {showAddContact && (
                    <div className="flex items-center gap-2 rounded-xl bg-slate-50 p-2 ring-1 ring-slate-200/80">
                      <input
                        type="text"
                        maxLength={80}
                        value={contactNickname}
                        onChange={(e) => setContactNickname(e.target.value)}
                        placeholder="Nickname (e.g. Priya)"
                        className="min-w-0 flex-1 rounded-lg bg-white px-2.5 py-1.5 text-xs text-slate-800 ring-1 ring-slate-200 focus:outline-none focus:ring-[#00C896]"
                      />
                      <button
                        type="button"
                        disabled={savingContact}
                        onClick={handleSaveBeneficiary}
                        className="shrink-0 rounded-lg bg-[#00D084] px-3 py-1.5 text-[11px] font-bold text-[#032539] hover:bg-[#00C896] disabled:opacity-50"
                      >
                        {savingContact ? '...' : 'Save'}
                      </button>
                    </div>
                  )}
                </div>

                <div className="flex items-center rounded-xl bg-slate-100/90 px-4 py-2.5 focus-within:bg-white focus-within:ring-2 focus-within:ring-[#00C896]">
                  <span className="mr-2 text-sm font-bold text-slate-400">₹</span>
                  <input
                    type="number"
                    step="0.01"
                    min="1"
                    value={amountInput}
                    onChange={(e) => setAmountInput(e.target.value)}
                    placeholder="Amount in INR"
                    className="w-full bg-transparent text-sm font-semibold text-slate-800 placeholder-slate-400 focus:outline-none tabular-nums"
                  />
                </div>

                <input
                  type="text"
                  maxLength={140}
                  value={noteInput}
                  onChange={(e) => setNoteInput(e.target.value)}
                  placeholder="Optional transfer note"
                  className="w-full rounded-xl bg-slate-100/90 px-4 py-2.5 text-xs text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]"
                />

                <button
                  type="submit"
                  disabled={quickSending || wallet.status !== 'ACTIVE'}
                  className="w-full rounded-xl bg-[#05334D] px-4 py-3 text-xs font-bold text-white shadow-sm transition hover:bg-[#084566] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {quickSending ? 'Settling...' : 'Make Transfer'}
                </button>
              </form>
            ) : (
              <div className="space-y-3">
                <div className="rounded-2xl bg-[#05334D] p-4 text-white">
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-300">
                    Your Wallet Reference
                  </p>
                  <div className="mt-1.5 flex items-center justify-between gap-2">
                    <span className="font-mono text-sm font-bold">
                      {wallet.walletReference || '—'}
                    </span>
                    <button
                      type="button"
                      onClick={handleCopyWalletRef}
                      className="inline-flex items-center gap-1 rounded-full bg-[#00D084] px-3 py-1 text-[11px] font-bold text-[#032539]"
                    >
                      {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                  <div className="mt-3 pt-3 border-t border-white/10 flex items-center justify-between">
                    <span className="text-[11px] text-slate-300">Accept QR payments</span>
                    <button
                      type="button"
                      onClick={() => setReceiveQrOpen(true)}
                      className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-[11px] font-semibold text-[#00D084] hover:bg-white/20 transition"
                    >
                      <QrCode className="h-3.5 w-3.5" />
                      View Full QR
                    </button>
                  </div>
                </div>

                {wallet.ledgerAccountNumber && (
                  <div className="flex items-center justify-between rounded-xl bg-slate-100 px-3.5 py-2.5 text-xs text-slate-600">
                    <span className="inline-flex items-center gap-1.5 font-medium">
                      <ShieldCheck className="h-3.5 w-3.5 text-[#00C896]" />
                      GL Account
                    </span>
                    <span className="font-mono font-semibold text-[#05334D]">
                      {wallet.ledgerAccountNumber}
                    </span>
                  </div>
                )}

                <form onSubmit={handleCreatePaymentRequest} className="space-y-2.5 border-t border-slate-100 pt-3" noValidate>
                  <p className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Request INR From Peer
                  </p>
                  {quickError && (
                    <div className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-600">
                      {quickError}
                    </div>
                  )}
                  {quickSuccess && (
                    <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                      <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00C896]" />
                      <span>{quickSuccess}</span>
                    </div>
                  )}
                  <input
                    type="text"
                    value={requestPayerRef}
                    onChange={(e) => setRequestPayerRef(e.target.value)}
                    placeholder="Payer Wallet Ref (WLT-IND-...)"
                    className="w-full rounded-xl bg-slate-100/90 px-3.5 py-2.5 font-mono text-xs uppercase text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]"
                  />
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      step="0.01"
                      min="1"
                      value={requestAmount}
                      onChange={(e) => setRequestAmount(e.target.value)}
                      placeholder="₹ Amount"
                      className="w-1/2 rounded-xl bg-slate-100/90 px-3 py-2 text-xs font-semibold text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]"
                    />
                    <input
                      type="text"
                      maxLength={100}
                      value={requestNote}
                      onChange={(e) => setRequestNote(e.target.value)}
                      placeholder="Note"
                      className="w-1/2 rounded-xl bg-slate-100/90 px-3 py-2 text-xs text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#00C896]"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={requestingMoney || wallet.status !== 'ACTIVE'}
                    className="w-full rounded-xl bg-[#00D084] px-4 py-2.5 text-xs font-bold text-[#032539] shadow-sm transition hover:bg-[#00C896] disabled:opacity-50"
                  >
                    {requestingMoney ? 'Sending Request...' : 'Send Payment Request'}
                  </button>
                </form>
              </div>
            )}
          </section>

          {/* 2. Wallet Summary Section */}
          <section className="space-y-3.5">
            <div className="flex items-center justify-between px-1">
              <h2 className="text-base font-bold text-[#0F172A]">Wallet Summary</h2>
              <Link to="/history" className="text-slate-400 hover:text-[#05334D]">
                <ChevronRight className="h-4 w-4" />
              </Link>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="flex items-center gap-3 rounded-2xl border border-slate-100 bg-white p-3.5 shadow-card">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-[#05334D]/25 border-t-[#05334D] bg-slate-50 text-[#05334D]">
                  <ArrowUpRight className="h-4 w-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-[11px] font-medium text-slate-400">Outcome</p>
                  <p className="truncate text-sm font-bold text-[#0F172A] tabular-nums">
                    {formatINR(summary.totalOutcome)}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 rounded-2xl border border-slate-100 bg-white p-3.5 shadow-card">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-[#00C896]/25 border-t-[#00C896] bg-emerald-50/50 text-[#00C896]">
                  <ArrowDownLeft className="h-4 w-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-[11px] font-medium text-slate-400">Income</p>
                  <p className="truncate text-sm font-bold text-[#0F172A] tabular-nums">
                    {formatINR(summary.totalIncome)}
                  </p>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-2xl bg-gradient-to-br from-[#DDF2F1] via-[#CBEAEA] to-[#9BE8CE] p-4 text-[#05334D] shadow-sm">
                <p className="text-xs font-medium text-[#05334D]/80">This Week</p>
                <p className="mt-2 text-lg font-bold tracking-tight tabular-nums">
                  {formatINR(summary.weekVolume)}
                </p>
                <p className="mt-1 text-[11px] font-semibold text-emerald-700">
                  7-day volume &uarr;
                </p>
              </div>

              <div className="rounded-2xl bg-gradient-to-br from-[#0D445F] to-[#05334D] p-4 text-white shadow-sm">
                <p className="text-xs font-medium text-slate-300">This Month</p>
                <p className="mt-2 text-lg font-bold tracking-tight tabular-nums">
                  {formatINR(summary.monthVolume)}
                </p>
                <p className="mt-1 text-[11px] font-semibold text-[#00D084]">
                  30-day volume
                </p>
              </div>
            </div>
          </section>
        </div>
      </div>

      {/* Fund Wallet Modal */}
      {fundModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#032539]/60 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-3xl border border-white/80 bg-white shadow-2xl">
            <div className="flex items-start justify-between bg-[#05334D] px-6 py-4 text-white">
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10">
                  <WalletIcon className="h-5 w-5 text-[#00D084]" />
                </div>
                <div>
                  <h3 className="text-base font-bold">Fund Your INR Wallet</h3>
                  <p className="text-xs text-slate-300 font-mono">
                    {wallet.walletReference || 'Primary Wallet'}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setFundModalOpen(false)}
                className="text-slate-300 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleFundWallet} className="p-6 space-y-4">
              {fundError && (
                <div className="flex items-start gap-2 rounded-2xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
                  <span>{fundError}</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Select Preset or Enter Amount (INR)
                </label>
                <div className="mt-2 grid grid-cols-4 gap-2">
                  {['1000', '5000', '10000', '25000'].map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => setFundAmount(preset)}
                      className={`rounded-xl py-2 text-xs font-semibold transition ${
                        fundAmount === preset
                          ? 'bg-[#05334D] text-white'
                          : 'bg-slate-100 text-slate-600 hover:bg-[#DDF2F1]'
                      }`}
                    >
                      ₹{Number(preset).toLocaleString('en-IN')}
                    </button>
                  ))}
                </div>
                <div className="mt-3 flex items-center rounded-xl border border-slate-200 bg-slate-50/80 px-4 py-2.5 focus-within:border-[#00C896] focus-within:bg-white">
                  <span className="mr-2 text-base font-bold text-slate-400">₹</span>
                  <input
                    type="number"
                    step="0.01"
                    min="1"
                    max="100000"
                    value={fundAmount}
                    onChange={(e) => setFundAmount(e.target.value)}
                    className="w-full bg-transparent text-base font-bold text-slate-900 focus:outline-none tabular-nums"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Funding Source
                </label>
                <select
                  value={fundSource}
                  onChange={(e) => setFundSource(e.target.value)}
                  className="mt-1.5 w-full rounded-xl border border-slate-200 bg-slate-50/80 px-4 py-2.5 text-xs font-semibold text-slate-700 focus:border-[#00C896] focus:bg-white focus:outline-none"
                >
                  <option value="UPI Instant">UPI Instant</option>
                  <option value="NetBanking (IMPS)">NetBanking (IMPS)</option>
                  <option value="HDFC Bank Virtual A/C">HDFC Bank Virtual A/C</option>
                </select>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setFundModalOpen(false)}
                  className="rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={funding}
                  className="rounded-full bg-[#00D084] px-5 py-2 text-xs font-bold text-[#032539] shadow-sm transition hover:bg-[#00C896] disabled:opacity-50"
                >
                  {funding ? 'Crediting Wallet...' : 'Add Funds Now'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <ReceiveQrModal
        isOpen={receiveQrOpen}
        onClose={() => setReceiveQrOpen(false)}
      />

      <StatementModal
        isOpen={statementModalOpen}
        onClose={() => setStatementModalOpen(false)}
      />
    </div>
  );
}
