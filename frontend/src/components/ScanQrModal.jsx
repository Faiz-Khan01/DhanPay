import { useState } from 'react';
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  FileText,
  QrCode,
  Scan,
  UserCheck,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { formatINR } from '../utils/formatters';

export default function ScanQrModal({ isOpen, onClose, onVerifiedCounterparty }) {
  const [rawInput, setRawInput] = useState('');
  const [verifying, setVerifying] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [verifiedResult, setVerifiedResult] = useState(null);

  if (!isOpen) return null;

  const handleVerify = async (payloadToVerify) => {
    const text = (payloadToVerify || rawInput).trim();
    if (!text) {
      setErrorMsg('Please paste a QR code string or UPI URI.');
      return;
    }

    setErrorMsg('');
    setVerifying(true);
    setVerifiedResult(null);

    try {
      const { data } = await client.post('/qr/parse', { payload: text });
      if (data.self) {
        setErrorMsg('Scanned QR code belongs to your own wallet. Transfers to yourself are prohibited.');
        return;
      }
      if (!data.targetActive) {
        setErrorMsg(`Target wallet (${data.targetWalletReference}) is currently inactive.`);
        return;
      }
      setVerifiedResult(data);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not decode or verify QR code.');
      setErrorMsg(message);
    } finally {
      setVerifying(false);
    }
  };

  const handleApply = () => {
    if (!verifiedResult) return;
    onVerifiedCounterparty({
      recipientWalletReference: verifiedResult.targetWalletReference,
      recipientName: verifiedResult.targetFullName,
      amount: verifiedResult.amount != null ? String(verifiedResult.amount) : '',
      description: verifiedResult.description || '',
    });
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#032539]/60 p-4 backdrop-blur-sm animate-fade-in">
      <div className="w-full max-w-md overflow-hidden rounded-3xl border border-white/80 bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between bg-[#05334D] px-6 py-4 text-white">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-white/10 text-[#00D084]">
              <Scan className="h-4 w-4" />
            </span>
            <div>
              <h3 className="text-base font-bold">Scan or Import Payment QR</h3>
              <p className="text-xs text-slate-300">Decode UPI or DhanPay QR codes instantly</p>
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

        <div className="p-6 space-y-4">
          {errorMsg && (
            <div className="flex items-start gap-2 rounded-2xl border border-rose-200 bg-rose-50 px-3 py-2.5 text-xs text-rose-700">
              <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-500" />
              <span>{errorMsg}</span>
            </div>
          )}

          <div>
            <label htmlFor="qr-payload-input" className="block text-xs font-semibold text-slate-700">
              Paste Scanned URI or Wallet Reference
            </label>
            <p className="text-[11px] text-slate-400">
              Supports <code className="font-mono text-slate-600">upi://pay?...</code>, <code className="font-mono text-slate-600">dhanpay://pay?...</code>, or <code className="font-mono text-slate-600">WLT-IND-...</code>
            </p>
            <textarea
              id="qr-payload-input"
              rows={3}
              value={rawInput}
              onChange={(e) => setRawInput(e.target.value)}
              placeholder="e.g. upi://pay?pa=WLT-IND-10000003@dhanpay&pn=Priya%20Patel&am=500.00"
              className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50/70 p-3 text-xs font-mono text-slate-800 focus:border-[#00C896] focus:bg-white focus:outline-none"
            />
          </div>

          {/* Preset test buttons for quick demo */}
          <div className="flex flex-wrap items-center gap-1.5 text-[11px]">
            <span className="text-slate-400">Sample URIs:</span>
            <button
              type="button"
              onClick={() => {
                const sample = 'upi://pay?pa=WLT-IND-10000003@dhanpay&pn=Priya%20Patel&am=500.00&tn=Consulting';
                setRawInput(sample);
                handleVerify(sample);
              }}
              className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600 hover:bg-emerald-50 hover:text-emerald-700"
            >
              Priya (₹500)
            </button>
            <button
              type="button"
              onClick={() => {
                const sample = 'upi://pay?pa=WLT-IND-10000004@dhanpay&pn=Rohan%20Verma&am=1200.00&tn=Freelance';
                setRawInput(sample);
                handleVerify(sample);
              }}
              className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600 hover:bg-emerald-50 hover:text-emerald-700"
            >
              Rohan (₹1200)
            </button>
          </div>

          <button
            type="button"
            disabled={verifying}
            onClick={() => handleVerify()}
            className="w-full rounded-2xl bg-[#05334D] py-2.5 text-xs font-semibold text-white transition hover:bg-[#074466] disabled:opacity-60"
          >
            {verifying ? 'Verifying Counterparty...' : 'Verify Scanned Payload'}
          </button>

          {/* Verified Counterparty Result Card */}
          {verifiedResult && (
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-4 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="flex h-7 w-7 items-center justify-center rounded-xl bg-white text-[#00C896] shadow-sm">
                    <UserCheck className="h-4 w-4" />
                  </span>
                  <div>
                    <p className="text-xs font-bold text-slate-900">{verifiedResult.targetFullName}</p>
                    <p className="font-mono text-[11px] text-slate-500">{verifiedResult.targetWalletReference}</p>
                  </div>
                </div>
                <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-800">
                  Verified Active
                </span>
              </div>

              {(verifiedResult.amount || verifiedResult.description) && (
                <div className="rounded-xl bg-white p-2.5 text-xs text-slate-600 space-y-1 border border-emerald-100">
                  {verifiedResult.amount && (
                    <div className="flex justify-between">
                      <span className="text-slate-400">Suggested Amount:</span>
                      <span className="font-bold text-slate-900 tabular-nums">{formatINR(verifiedResult.amount)}</span>
                    </div>
                  )}
                  {verifiedResult.description && (
                    <div className="flex justify-between">
                      <span className="text-slate-400">Note:</span>
                      <span className="text-slate-700 font-medium">{verifiedResult.description}</span>
                    </div>
                  )}
                </div>
              )}

              <button
                type="button"
                onClick={handleApply}
                className="inline-flex w-full items-center justify-center gap-1.5 rounded-full bg-[#00D084] py-2.5 text-xs font-bold text-[#032539] shadow transition hover:bg-[#00C896]"
              >
                Use Counterparty &amp; Continue
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
