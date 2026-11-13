import { useEffect, useState } from 'react';
import {
  Check,
  Copy,
  Download,
  IndianRupee,
  QrCode,
  RefreshCw,
  Sparkles,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { formatINR } from '../utils/formatters';

export default function ReceiveQrModal({ isOpen, onClose, initialAmount = '', initialNote = '' }) {
  const [mode, setMode] = useState('static'); // 'static' | 'dynamic'
  const [qrData, setQrData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);

  const [customAmount, setCustomAmount] = useState(initialAmount);
  const [customNote, setCustomNote] = useState(initialNote);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    setErrorMsg('');
    setLoading(true);

    client.get('/qr/my-code')
      .then((res) => setQrData(res.data))
      .catch((err) => {
        const { message } = extractApiError(err, 'Unable to load QR code.');
        setErrorMsg(message);
      })
      .finally(() => setLoading(false));
  }, [isOpen]);

  if (!isOpen) return null;

  const handleGenerateDynamic = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    const num = Number(customAmount);
    if (!Number.isFinite(num) || num < 1) {
      setErrorMsg('Please enter a valid amount (minimum ₹1.00).');
      return;
    }

    setGenerating(true);
    try {
      const { data } = await client.post('/qr/generate', {
        amount: num,
        note: customNote.trim() || undefined,
      });
      setQrData(data);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not generate dynamic QR.');
      setErrorMsg(message);
    } finally {
      setGenerating(false);
    }
  };

  const handleCopyUri = async () => {
    const uri = qrData?.upiUri || qrData?.dhanpayUri;
    if (!uri) return;
    try {
      await navigator.clipboard.writeText(uri);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    } catch {
      setCopiedLink(false);
    }
  };

  const handleDownloadSvg = () => {
    if (!qrData?.qrSvg) return;
    const blob = new Blob([qrData.qrSvg], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${qrData.walletReference || 'dhanpay'}-qr.svg`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#032539]/60 p-4 backdrop-blur-sm animate-fade-in">
      <div className="w-full max-w-md overflow-hidden rounded-3xl border border-white/80 bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between bg-[#05334D] px-6 py-4 text-white">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-white/10 text-[#00D084]">
              <QrCode className="h-4 w-4" />
            </span>
            <div>
              <h3 className="text-base font-bold">Receive Funds via QR</h3>
              <p className="text-xs text-slate-300">Scan using any UPI or DhanPay app</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1 text-slate-300 hover:bg-white/10 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="grid grid-cols-2 gap-1 border-b border-slate-100 bg-slate-50/70 p-2">
          <button
            type="button"
            onClick={() => setMode('static')}
            className={`rounded-2xl py-2 text-xs font-semibold transition ${
              mode === 'static'
                ? 'bg-white text-[#05334D] shadow-sm'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            Static Wallet QR
          </button>
          <button
            type="button"
            onClick={() => setMode('dynamic')}
            className={`flex items-center justify-center gap-1.5 rounded-2xl py-2 text-xs font-semibold transition ${
              mode === 'dynamic'
                ? 'bg-white text-[#05334D] shadow-sm'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Sparkles className="h-3.5 w-3.5 text-[#00C896]" />
            Request Specific Amount
          </button>
        </div>

        <div className="p-6 space-y-5">
          {errorMsg && (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-700">
              {errorMsg}
            </div>
          )}

          {mode === 'dynamic' && (
            <form onSubmit={handleGenerateDynamic} className="space-y-3 rounded-2xl bg-slate-50 p-3.5 border border-slate-100">
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label htmlFor="dynamic-amount" className="block text-[11px] font-semibold text-slate-600">
                    Amount (INR)
                  </label>
                  <div className="relative mt-1">
                    <IndianRupee className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
                    <input
                      id="dynamic-amount"
                      type="number"
                      step="0.01"
                      min="1"
                      max="50000"
                      value={customAmount}
                      onChange={(e) => setCustomAmount(e.target.value)}
                      placeholder="500.00"
                      className="w-full rounded-xl border border-slate-200 bg-white py-1.5 pl-8 pr-2.5 text-xs text-slate-900 focus:border-[#00C896] focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="dynamic-note" className="block text-[11px] font-semibold text-slate-600">
                    Note / Purpose
                  </label>
                  <input
                    id="dynamic-note"
                    type="text"
                    maxLength={100}
                    value={customNote}
                    onChange={(e) => setCustomNote(e.target.value)}
                    placeholder="e.g. Dinner share"
                    className="mt-1 w-full rounded-xl border border-slate-200 bg-white py-1.5 px-3 text-xs text-slate-900 focus:border-[#00C896] focus:outline-none"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={generating}
                className="w-full rounded-xl bg-[#05334D] py-2 text-xs font-semibold text-white transition hover:bg-[#074466] disabled:opacity-60"
              >
                {generating ? 'Regenerating QR...' : 'Update QR Code'}
              </button>
            </form>
          )}

          {/* QR Display Card */}
          <div className="flex flex-col items-center justify-center rounded-3xl border border-slate-100 bg-gradient-to-b from-[#DDF2F1]/30 to-white p-6 shadow-inner">
            {loading ? (
              <div className="flex h-52 w-52 items-center justify-center">
                <RefreshCw className="h-8 w-8 animate-spin text-[#00C896]" />
              </div>
            ) : qrData?.qrSvg ? (
              <div className="flex flex-col items-center space-y-3">
                <div
                  className="h-56 w-56 rounded-2xl border-4 border-white bg-white p-2 shadow-card"
                  dangerouslySetInnerHTML={{ __html: qrData.qrSvg }}
                />
                <div className="text-center">
                  <p className="text-sm font-bold text-slate-900">{qrData.accountHolderName}</p>
                  <p className="font-mono text-xs text-slate-500">{qrData.walletReference}</p>
                  {qrData.amount && (
                    <span className="mt-1 inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-bold text-emerald-700 ring-1 ring-emerald-200">
                      Amount: {formatINR(qrData.amount)}
                    </span>
                  )}
                </div>
              </div>
            ) : (
              <p className="text-xs text-slate-400">QR code not available</p>
            )}
          </div>

          {/* Actions */}
          <div className="flex items-center justify-center gap-2">
            <button
              type="button"
              onClick={handleCopyUri}
              className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
            >
              {copiedLink ? <Check className="h-3.5 w-3.5 text-[#00C896]" /> : <Copy className="h-3.5 w-3.5 text-slate-400" />}
              {copiedLink ? 'Copied UPI Link' : 'Copy UPI Link'}
            </button>

            <button
              type="button"
              onClick={handleDownloadSvg}
              className="inline-flex items-center gap-1.5 rounded-full bg-[#00D084] px-4 py-2 text-xs font-bold text-[#032539] shadow hover:bg-[#00C896]"
            >
              <Download className="h-3.5 w-3.5" />
              Download QR
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
