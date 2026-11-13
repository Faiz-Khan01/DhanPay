import { useState } from 'react';
import {
  AlertCircle,
  Calendar,
  CheckCircle2,
  Download,
  FileCheck2,
  FileText,
  Loader2,
  Printer,
  ShieldCheck,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { formatINR, formatTimestamp } from '../utils/formatters';

export default function StatementModal({ isOpen, onClose }) {
  const [period, setPeriod] = useState('CURRENT_MONTH');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [statement, setStatement] = useState(null);

  if (!isOpen) return null;

  const handleGenerate = async () => {
    setErrorMsg('');
    setLoading(true);
    try {
      const params = { period, format: 'json' };
      if (period === 'CUSTOM') {
        if (!startDate || !endDate) {
          setErrorMsg('Please select both start and end dates for a custom range.');
          setLoading(false);
          return;
        }
        params.startDate = startDate;
        params.endDate = endDate;
      }

      const { data } = await client.get('/wallets/statement', { params });
      setStatement(data);
    } catch (err) {
      const { message } = extractApiError(err, 'Failed to generate account statement.');
      setErrorMsg(message);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenPrintable = async () => {
    setErrorMsg('');
    try {
      const params = new URLSearchParams();
      params.set('period', period);
      params.set('format', 'html');
      if (period === 'CUSTOM' && startDate && endDate) {
        params.set('startDate', startDate);
        params.set('endDate', endDate);
      }

      const token = localStorage.getItem('dhanpay_token');
      const response = await fetch(`/api/wallets/statement?${params.toString()}`, {
        headers: {
          Authorization: token ? `Bearer ${token}` : '',
        },
      });

      if (!response.ok) {
        throw new Error('Failed to retrieve certified statement document.');
      }

      const html = await response.text();
      const printWindow = window.open('', '_blank');
      if (printWindow) {
        printWindow.document.open();
        printWindow.document.write(html);
        printWindow.document.close();
      } else {
        setErrorMsg('Pop-up blocked. Please allow pop-ups to view the certified statement.');
      }
    } catch (err) {
      setErrorMsg(err.message || 'Error opening statement');
    }
  };

  const handleDownloadJson = () => {
    if (!statement) return;
    const blob = new Blob([JSON.stringify(statement, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `DhanPay_Statement_${statement.statementReference}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
      <div className="relative w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl bg-white p-6 sm:p-8 shadow-2xl border border-slate-100">
        <button
          onClick={onClose}
          className="absolute right-5 top-5 rounded-full p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          aria-label="Close"
        >
          <X className="h-5 w-5" />
        </button>

        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#E6FBF5] text-[#00875A]">
            <FileCheck2 className="h-6 w-6 text-[#00D084]" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-[#05334D]">Official Account Statement</h2>
            <p className="text-xs text-slate-500">
              Certified double-entry ledger statement with RBI compliant digital seal
            </p>
          </div>
        </div>

        {errorMsg && (
          <div className="mt-4 flex items-center gap-2 rounded-xl bg-rose-50 p-3 text-xs font-medium text-rose-700">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        {/* Period Selection Controls */}
        <div className="mt-6 space-y-4 rounded-2xl bg-slate-50 p-4 border border-slate-100">
          <label className="block text-xs font-semibold text-slate-700">Statement Period</label>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {[
              { id: 'CURRENT_MONTH', label: 'Current Month' },
              { id: 'LAST_MONTH', label: 'Last Month' },
              { id: 'LAST_30_DAYS', label: 'Last 30 Days' },
              { id: 'LAST_90_DAYS', label: 'Last 90 Days' },
              { id: 'FINANCIAL_YEAR', label: 'Financial Year' },
              { id: 'CUSTOM', label: 'Custom Range' },
            ].map((opt) => (
              <button
                key={opt.id}
                type="button"
                onClick={() => setPeriod(opt.id)}
                className={`rounded-xl px-3 py-2 text-xs font-semibold transition text-center ${
                  period === opt.id
                    ? 'bg-[#05334D] text-white shadow-sm'
                    : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-100'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>

          {period === 'CUSTOM' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Start Date</label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">End Date</label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                />
              </div>
            </div>
          )}

          <div className="pt-2 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={handleGenerate}
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-xl bg-[#05334D] px-4 py-2.5 text-xs font-semibold text-white transition hover:bg-[#084566] disabled:opacity-50"
            >
              {loading ? <Loader2 className="h-4 w-4 animate-spin text-[#00D084]" /> : <FileText className="h-4 w-4 text-[#00D084]" />}
              Generate Statement
            </button>

            <button
              type="button"
              onClick={handleOpenPrintable}
              className="inline-flex items-center gap-2 rounded-xl bg-[#00D084] px-4 py-2.5 text-xs font-semibold text-[#05334D] transition hover:bg-[#00B875]"
            >
              <Printer className="h-4 w-4" />
              Print / Save PDF
            </button>
          </div>
        </div>

        {/* Statement Summary Card */}
        {statement && (
          <div className="mt-6 space-y-4">
            <div className="rounded-2xl border border-emerald-100 bg-[#F1F9F8] p-4 text-xs">
              <div className="flex items-center justify-between border-b border-emerald-100 pb-2">
                <span className="font-bold text-[#05334D]">{statement.statementReference}</span>
                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 font-bold text-emerald-800 text-[10px]">
                  <CheckCircle2 className="h-3 w-3" /> VERIFIED
                </span>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-3">
                <div className="bg-white p-2.5 rounded-xl border border-slate-100">
                  <div className="text-[10px] text-slate-500 uppercase">Opening Balance</div>
                  <div className="text-sm font-bold text-slate-800 mt-0.5">{formatINR(statement.summary.openingBalance)}</div>
                </div>
                <div className="bg-white p-2.5 rounded-xl border border-slate-100">
                  <div className="text-[10px] text-emerald-600 uppercase">Total Inflow (+)</div>
                  <div className="text-sm font-bold text-emerald-700 mt-0.5">+{formatINR(statement.summary.totalCredits)}</div>
                </div>
                <div className="bg-white p-2.5 rounded-xl border border-slate-100">
                  <div className="text-[10px] text-rose-600 uppercase">Total Outflow (-)</div>
                  <div className="text-sm font-bold text-rose-700 mt-0.5">-{formatINR(statement.summary.totalDebits)}</div>
                </div>
                <div className="bg-[#05334D] p-2.5 rounded-xl text-white">
                  <div className="text-[10px] text-slate-300 uppercase">Closing Balance</div>
                  <div className="text-sm font-bold mt-0.5">{formatINR(statement.summary.closingBalance)}</div>
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between text-[11px] text-slate-600">
                <span>Transactions: <strong>{statement.summary.totalTransactions}</strong></span>
                <span>Period: <strong>{statement.summary.startDate}</strong> to <strong>{statement.summary.endDate}</strong></span>
              </div>
            </div>

            {/* Cryptographic Digital Seal */}
            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3.5 text-[11px] text-slate-600 space-y-1.5">
              <div className="flex items-center gap-1.5 font-semibold text-[#05334D]">
                <ShieldCheck className="h-4 w-4 text-[#00D084]" />
                Digital Verification Seal (SHA-256)
              </div>
              <p className="text-[10px] text-slate-500">
                Cryptographic checksum derived from append-only general ledger balances.
              </p>
              <div className="font-mono text-[10px] break-all bg-white p-2 rounded-lg border border-slate-200 text-slate-700">
                {statement.digitalVerificationHash}
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={handleDownloadJson}
                className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                <Download className="h-3.5 w-3.5" />
                Download JSON
              </button>
              <button
                type="button"
                onClick={handleOpenPrintable}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[#05334D] px-4 py-2 text-xs font-semibold text-white hover:bg-[#084566]"
              >
                <Printer className="h-3.5 w-3.5 text-[#00D084]" />
                Print / Save PDF
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
