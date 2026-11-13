import { useCallback, useEffect, useState } from 'react';
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Code,
  Copy,
  ExternalLink,
  Eye,
  EyeOff,
  Globe,
  Key,
  Layers,
  Loader2,
  Plus,
  RefreshCw,
  Send,
  ShieldCheck,
  Trash2,
  Webhook,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import { formatTimestamp } from '../utils/formatters';

export default function DeveloperPortalPage() {
  const [activeTab, setActiveTab] = useState('keys'); // 'keys' | 'webhooks' | 'docs'
  const [keys, setKeys] = useState([]);
  const [webhooks, setWebhooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Modals
  const [createKeyModal, setCreateKeyModal] = useState(false);
  const [newKeyName, setNewKeyName] = useState('');
  const [newKeyEnv, setNewKeyEnv] = useState('LIVE');
  const [newKeyExpiry, setNewKeyExpiry] = useState('90');
  const [creatingKey, setCreatingKey] = useState(false);
  const [createdKeyResult, setCreatedKeyResult] = useState(null);

  const [createWebhookModal, setCreateWebhookModal] = useState(false);
  const [webhookUrl, setWebhookUrl] = useState('');
  const [webhookDesc, setWebhookDesc] = useState('');
  const [webhookEvents, setWebhookEvents] = useState('all');
  const [creatingWebhook, setCreatingWebhook] = useState(false);

  const [deliveryModal, setDeliveryModal] = useState(null);
  const [deliveries, setDeliveries] = useState([]);
  const [loadingDeliveries, setLoadingDeliveries] = useState(false);

  const [testingWebhookId, setTestingWebhookId] = useState(null);
  const [testResult, setTestResult] = useState(null);

  const [revealedSecrets, setRevealedSecrets] = useState({});
  const [copiedId, setCopiedId] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const [keysRes, webhooksRes] = await Promise.all([
        client.get('/developer/keys'),
        client.get('/developer/webhooks'),
      ]);
      setKeys(keysRes.data || []);
      setWebhooks(webhooksRes.data || []);
    } catch (err) {
      const { message } = extractApiError(err, 'Failed to load developer credentials.');
      setErrorMsg(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const copyToClipboard = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleCreateKey = async (e) => {
    e.preventDefault();
    if (!newKeyName.trim()) return;
    setCreatingKey(true);
    setErrorMsg('');
    try {
      const { data } = await client.post('/developer/keys', {
        name: newKeyName.trim(),
        environment: newKeyEnv,
        expiryDays: newKeyExpiry ? Number(newKeyExpiry) : null,
      });
      setCreatedKeyResult(data);
      setCreateKeyModal(false);
      setNewKeyName('');
      loadData();
    } catch (err) {
      const { message } = extractApiError(err, 'Could not create API key.');
      setErrorMsg(message);
    } finally {
      setCreatingKey(false);
    }
  };

  const handleRevokeKey = async (id) => {
    if (!window.confirm('Are you sure you want to revoke this API key? This will immediately break applications using it.')) {
      return;
    }
    try {
      await client.delete(`/developer/keys/${id}`);
      setSuccessMsg('API key revoked.');
      setTimeout(() => setSuccessMsg(''), 3000);
      loadData();
    } catch (err) {
      const { message } = extractApiError(err, 'Failed to revoke API key.');
      setErrorMsg(message);
    }
  };

  const handleCreateWebhook = async (e) => {
    e.preventDefault();
    if (!webhookUrl.trim()) return;
    setCreatingWebhook(true);
    setErrorMsg('');
    try {
      await client.post('/developer/webhooks', {
        url: webhookUrl.trim(),
        description: webhookDesc.trim() || undefined,
        events: webhookEvents,
      });
      setCreateWebhookModal(false);
      setWebhookUrl('');
      setWebhookDesc('');
      setSuccessMsg('Webhook endpoint registered.');
      setTimeout(() => setSuccessMsg(''), 3000);
      loadData();
    } catch (err) {
      const { message } = extractApiError(err, 'Could not register webhook.');
      setErrorMsg(message);
    } finally {
      setCreatingWebhook(false);
    }
  };

  const handleDeleteWebhook = async (id) => {
    if (!window.confirm('Delete this webhook endpoint subscription?')) return;
    try {
      await client.delete(`/developer/webhooks/${id}`);
      setSuccessMsg('Webhook endpoint deleted.');
      setTimeout(() => setSuccessMsg(''), 3000);
      loadData();
    } catch (err) {
      const { message } = extractApiError(err, 'Failed to delete webhook.');
      setErrorMsg(message);
    }
  };

  const handleTestWebhook = async (webhook) => {
    setTestingWebhookId(webhook.id);
    setTestResult(null);
    try {
      const { data } = await client.post(`/developer/webhooks/${webhook.id}/test`);
      setTestResult({ webhookId: webhook.id, ...data });
    } catch (err) {
      const { message } = extractApiError(err, 'Webhook test dispatch failed.');
      setTestResult({ webhookId: webhook.id, success: false, errorMessage: message });
    } finally {
      setTestingWebhookId(null);
    }
  };

  const handleViewDeliveries = async (webhook) => {
    setDeliveryModal(webhook);
    setLoadingDeliveries(true);
    try {
      const { data } = await client.get(`/developer/webhooks/${webhook.id}/deliveries`);
      setDeliveries(data || []);
    } catch (err) {
      const { message } = extractApiError(err, 'Could not fetch delivery history.');
      setErrorMsg(message);
    } finally {
      setLoadingDeliveries(false);
    }
  };

  const toggleRevealSecret = (id) => {
    setRevealedSecrets((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-full bg-[#E6FBF5] px-2.5 py-0.5 text-[11px] font-bold text-[#00875A]">
              <Code className="h-3 w-3" /> DEVELOPER & MERCHANT SUITE
            </span>
          </div>
          <h1 className="mt-1 text-2xl sm:text-3xl font-extrabold text-[#05334D]">
            Developer Platform
          </h1>
          <p className="text-xs sm:text-sm text-slate-500">
            Programmatic API keys, signed webhook notifications, and integration documentation.
          </p>
        </div>

        <button
          type="button"
          onClick={loadData}
          disabled={loading}
          className="inline-flex items-center gap-1.5 self-start rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:border-[#00C896] hover:text-[#05334D] disabled:opacity-50"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin text-[#00C896]' : ''}`} />
          Refresh
        </button>
      </div>

      {errorMsg && (
        <div className="flex items-center gap-2 rounded-2xl bg-rose-50 p-4 text-xs font-medium text-rose-700 border border-rose-100">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {successMsg && (
        <div className="flex items-center gap-2 rounded-2xl bg-emerald-50 p-4 text-xs font-medium text-emerald-800 border border-emerald-100">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-[#00D084]" />
          <span>{successMsg}</span>
        </div>
      )}

      {/* Tabs */}
      <div className="flex border-b border-slate-200 text-xs font-semibold">
        {[
          { id: 'keys', label: 'API Keys', icon: Key },
          { id: 'webhooks', label: 'Webhooks & Subscriptions', icon: Webhook },
          { id: 'docs', label: 'Integration Guide', icon: Code },
        ].map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 border-b-2 px-5 py-3 transition ${
                activeTab === tab.id
                  ? 'border-[#00D084] text-[#05334D] font-bold'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              <Icon className="h-4 w-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* TAB 1: API KEYS */}
      {activeTab === 'keys' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-[#05334D]">Secret API Keys</h2>
              <p className="text-xs text-slate-500">
                Authenticate server-to-server requests via <code className="bg-slate-100 px-1 py-0.5 rounded text-[11px]">X-DhanPay-Api-Key</code>
              </p>
            </div>
            <button
              type="button"
              onClick={() => setCreateKeyModal(true)}
              className="inline-flex items-center gap-1.5 rounded-full bg-[#05334D] px-4 py-2 text-xs font-bold text-white transition hover:bg-[#084566] shadow-sm"
            >
              <Plus className="h-3.5 w-3.5 text-[#00D084]" />
              New API Key
            </button>
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                <tr>
                  <th className="px-4 py-3">Label</th>
                  <th className="px-4 py-3">Environment</th>
                  <th className="px-4 py-3">Key Prefix</th>
                  <th className="px-4 py-3">Created</th>
                  <th className="px-4 py-3">Last Used</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {keys.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="py-12 text-center text-slate-400 font-medium">
                      No API keys generated yet. Click &quot;New API Key&quot; to create one.
                    </td>
                  </tr>
                ) : (
                  keys.map((k) => (
                    <tr key={k.id} className="hover:bg-slate-50/70 transition">
                      <td className="px-4 py-3 font-semibold text-slate-800">{k.name}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                            k.environment === 'LIVE'
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : 'bg-amber-50 text-amber-700 border border-amber-200'
                          }`}
                        >
                          {k.environment}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-[11px] text-slate-600">{k.keyPrefix}</td>
                      <td className="px-4 py-3 text-slate-500">{formatTimestamp(k.createdAt)}</td>
                      <td className="px-4 py-3 text-slate-500">
                        {k.lastUsedAt ? formatTimestamp(k.lastUsedAt) : 'Never'}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`rounded-full px-2.5 py-0.5 text-[10px] font-bold ${
                            k.status === 'ACTIVE'
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-slate-100 text-slate-500 line-through'
                          }`}
                        >
                          {k.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {k.status === 'ACTIVE' ? (
                          <button
                            type="button"
                            onClick={() => handleRevokeKey(k.id)}
                            className="rounded-lg px-2.5 py-1 text-[11px] font-semibold text-rose-600 hover:bg-rose-50 transition"
                          >
                            Revoke
                          </button>
                        ) : (
                          <span className="text-[11px] text-slate-400">Revoked</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 2: WEBHOOKS */}
      {activeTab === 'webhooks' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-[#05334D]">Webhook Subscriptions</h2>
              <p className="text-xs text-slate-500">
                Receive signed HMAC-SHA256 event notifications in real-time.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setCreateWebhookModal(true)}
              className="inline-flex items-center gap-1.5 rounded-full bg-[#05334D] px-4 py-2 text-xs font-bold text-white transition hover:bg-[#084566] shadow-sm"
            >
              <Plus className="h-3.5 w-3.5 text-[#00D084]" />
              Add Webhook
            </button>
          </div>

          {webhooks.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 p-12 text-center bg-white">
              <Webhook className="mx-auto h-8 w-8 text-slate-300 mb-2" />
              <p className="text-xs font-semibold text-slate-600">No webhook endpoints registered</p>
              <p className="text-[11px] text-slate-400 mt-1">Register an endpoint to receive signed transfer and escrow notifications.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4">
              {webhooks.map((w) => {
                const isSecretRevealed = !!revealedSecrets[w.id];
                const isTesting = testingWebhookId === w.id;
                const hasTestResult = testResult && testResult.webhookId === w.id;

                return (
                  <div key={w.id} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm space-y-3">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
                        <span className="font-mono text-xs font-bold text-[#05334D]">{w.url}</span>
                        {w.description && (
                          <span className="text-xs text-slate-400">({w.description})</span>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => handleTestWebhook(w)}
                          disabled={isTesting}
                          className="inline-flex items-center gap-1 rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                        >
                          {isTesting ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5 text-[#00D084]" />}
                          Test Ping
                        </button>
                        <button
                          type="button"
                          onClick={() => handleViewDeliveries(w)}
                          className="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                        >
                          Logs
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDeleteWebhook(w.id)}
                          className="rounded-xl p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition"
                          title="Delete endpoint"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>

                    {/* Secret Key box */}
                    <div className="flex items-center justify-between rounded-xl bg-slate-50 p-2.5 text-xs border border-slate-100">
                      <div className="flex items-center gap-2 font-mono">
                        <span className="text-slate-400 font-sans text-[11px]">Signing Secret:</span>
                        <span className="text-slate-800 font-semibold">
                          {isSecretRevealed ? w.secretKey : `${w.secretKey.substring(0, 10)}••••••••••••••••`}
                        </span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => toggleRevealSecret(w.id)}
                          className="p-1 text-slate-500 hover:text-slate-800"
                        >
                          {isSecretRevealed ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                        </button>
                        <button
                          type="button"
                          onClick={() => copyToClipboard(w.secretKey, w.id)}
                          className="inline-flex items-center gap-1 text-[11px] font-semibold text-[#05334D] hover:underline"
                        >
                          {copiedId === w.id ? <Check className="h-3.5 w-3.5 text-emerald-600" /> : <Copy className="h-3.5 w-3.5" />}
                          {copiedId === w.id ? 'Copied' : 'Copy'}
                        </button>
                      </div>
                    </div>

                    <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
                      <div>Events: <strong className="text-slate-700">{w.events}</strong></div>
                      <div>Registered: {formatTimestamp(w.createdAt)}</div>
                    </div>

                    {/* Test result banner */}
                    {hasTestResult && (
                      <div
                        className={`mt-2 rounded-xl p-3 text-xs flex items-center justify-between ${
                          testResult.success
                            ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                            : 'bg-rose-50 text-rose-700 border border-rose-200'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          {testResult.success ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : <AlertCircle className="h-4 w-4 text-rose-600" />}
                          <span>
                            {testResult.success
                              ? `Ping delivered successfully (HTTP ${testResult.httpStatus || 200})`
                              : `Ping failed: ${testResult.errorMessage || 'No response'}`}
                          </span>
                        </div>
                        <button onClick={() => setTestResult(null)} className="text-slate-400 hover:text-slate-600">
                          <X className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: INTEGRATION GUIDE */}
      {activeTab === 'docs' && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm space-y-6 text-xs text-slate-700">
          <div>
            <h2 className="text-lg font-bold text-[#05334D]">DhanPay API Quickstart</h2>
            <p className="text-slate-500 mt-1">
              Authenticate all server requests using your secret key in the request headers:
            </p>
          </div>

          <div className="space-y-2">
            <span className="font-bold text-[#05334D] text-xs">1. Authenticated API Call (cURL)</span>
            <pre className="rounded-xl bg-[#05334D] p-4 font-mono text-[11px] text-emerald-300 overflow-x-auto">
{`curl -X POST https://api.dhanpay.in/api/transfers \\
  -H "X-DhanPay-Api-Key: dp_live_YOUR_SECRET_KEY" \\
  -H "Idempotency-Key: e82e2c04-f58c-4a31-9252-09bbcf71a2e9" \\
  -H "Content-Type: application/json" \\
  -d '{
    "recipientWalletReference": "WLT-IND-10000003",
    "amount": 2500.00,
    "description": "Invoice #8492 payout"
  }'`}
            </pre>
          </div>

          <div className="space-y-2">
            <span className="font-bold text-[#05334D] text-xs">2. Webhook Signature Verification (Node.js)</span>
            <pre className="rounded-xl bg-[#05334D] p-4 font-mono text-[11px] text-emerald-300 overflow-x-auto">
{`const crypto = require('crypto');

function verifyDhanPayWebhook(payload, signatureHeader, secret) {
  // header format: t=1794500000,v1=6a8b...
  const parts = Object.fromEntries(signatureHeader.split(',').map(s => s.split('=')));
  const timestamp = parts.t;
  const signature = parts.v1;

  const expected = crypto
    .createHmac('sha256', secret)
    .update(\`\${timestamp}.\${payload}\`)
    .digest('hex');

  return crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected));
}`}
            </pre>
          </div>
        </div>
      )}

      {/* Modal: Created Key Display (Shown once!) */}
      {createdKeyResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-lg rounded-3xl bg-white p-6 sm:p-8 shadow-2xl border border-slate-100 space-y-4">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-700">
                <Key className="h-6 w-6 text-[#00D084]" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-[#05334D]">API Key Generated</h3>
                <p className="text-xs text-slate-500">Copy this key now. It will never be shown again.</p>
              </div>
            </div>

            <div className="rounded-2xl bg-amber-50 p-3 text-xs text-amber-800 border border-amber-200">
              ⚠️ <strong>Warning:</strong> {createdKeyResult.warning}
            </div>

            <div className="rounded-2xl bg-slate-900 p-4 font-mono text-xs text-emerald-300 break-all flex items-center justify-between gap-2">
              <span>{createdKeyResult.secretKey}</span>
              <button
                type="button"
                onClick={() => copyToClipboard(createdKeyResult.secretKey, 'new-key')}
                className="shrink-0 inline-flex items-center gap-1 rounded-xl bg-white/10 px-3 py-1.5 text-xs text-white hover:bg-white/20 transition"
              >
                {copiedId === 'new-key' ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                {copiedId === 'new-key' ? 'Copied' : 'Copy'}
              </button>
            </div>

            <div className="flex justify-end pt-2">
              <button
                type="button"
                onClick={() => setCreatedKeyResult(null)}
                className="rounded-full bg-[#05334D] px-6 py-2 text-xs font-bold text-white hover:bg-[#084566]"
              >
                I Have Saved My Key
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Create Key Form */}
      {createKeyModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-md rounded-3xl bg-white p-6 sm:p-8 shadow-2xl border border-slate-100">
            <button
              onClick={() => setCreateKeyModal(false)}
              className="absolute right-5 top-5 rounded-full p-2 text-slate-400 hover:bg-slate-100"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-lg font-bold text-[#05334D]">Generate API Key</h3>
            <p className="text-xs text-slate-500 mt-0.5">Create a programmatic credential for your application.</p>

            <form onSubmit={handleCreateKey} className="mt-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Key Label / Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Production Shopify Backend"
                  value={newKeyName}
                  onChange={(e) => setNewKeyName(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Environment</label>
                <select
                  value={newKeyEnv}
                  onChange={(e) => setNewKeyEnv(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                >
                  <option value="LIVE">LIVE (Real Funds)</option>
                  <option value="TEST">TEST (Sandbox Mode)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Expires In</label>
                <select
                  value={newKeyExpiry}
                  onChange={(e) => setNewKeyExpiry(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                >
                  <option value="30">30 Days</option>
                  <option value="90">90 Days</option>
                  <option value="365">1 Year</option>
                  <option value="">Never Expires</option>
                </select>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setCreateKeyModal(false)}
                  className="rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creatingKey}
                  className="inline-flex items-center gap-1.5 rounded-full bg-[#05334D] px-5 py-2 text-xs font-bold text-white hover:bg-[#084566] disabled:opacity-50"
                >
                  {creatingKey ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Key className="h-3.5 w-3.5 text-[#00D084]" />}
                  Create Key
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Create Webhook Form */}
      {createWebhookModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-md rounded-3xl bg-white p-6 sm:p-8 shadow-2xl border border-slate-100">
            <button
              onClick={() => setCreateWebhookModal(false)}
              className="absolute right-5 top-5 rounded-full p-2 text-slate-400 hover:bg-slate-100"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-lg font-bold text-[#05334D]">Register Webhook Endpoint</h3>
            <p className="text-xs text-slate-500 mt-0.5">Subscribe to signed event dispatches.</p>

            <form onSubmit={handleCreateWebhook} className="mt-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Destination URL</label>
                <input
                  type="url"
                  required
                  placeholder="https://api.yourdomain.com/dhanpay/webhook"
                  value={webhookUrl}
                  onChange={(e) => setWebhookUrl(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Description (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Order management sync"
                  value={webhookDesc}
                  onChange={(e) => setWebhookDesc(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Event Subscriptions</label>
                <select
                  value={webhookEvents}
                  onChange={(e) => setWebhookEvents(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-800 focus:border-[#00C896] focus:outline-none"
                >
                  <option value="all">All Events (*)</option>
                  <option value="transfer.completed,transfer.failed">Transfers Only (Completed & Failed)</option>
                  <option value="escrow.held,escrow.released">Escrow Events Only</option>
                  <option value="payment_request.paid">Payment Requests Only</option>
                </select>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setCreateWebhookModal(false)}
                  className="rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creatingWebhook}
                  className="inline-flex items-center gap-1.5 rounded-full bg-[#05334D] px-5 py-2 text-xs font-bold text-white hover:bg-[#084566] disabled:opacity-50"
                >
                  {creatingWebhook ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Webhook className="h-3.5 w-3.5 text-[#00D084]" />}
                  Register Endpoint
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Webhook Delivery Log */}
      {deliveryModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-2xl max-h-[85vh] overflow-y-auto rounded-3xl bg-white p-6 sm:p-8 shadow-2xl border border-slate-100">
            <button
              onClick={() => setDeliveryModal(null)}
              className="absolute right-5 top-5 rounded-full p-2 text-slate-400 hover:bg-slate-100"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-lg font-bold text-[#05334D]">Delivery History</h3>
            <p className="text-xs text-slate-500 font-mono mt-0.5">{deliveryModal.url}</p>

            <div className="mt-5 space-y-2">
              {loadingDeliveries ? (
                <div className="py-12 text-center text-xs text-slate-400">Loading delivery logs...</div>
              ) : deliveries.length === 0 ? (
                <div className="py-12 text-center text-xs text-slate-400">No events dispatched yet. Click &quot;Test Ping&quot; to test.</div>
              ) : (
                deliveries.map((d) => (
                  <div key={d.id} className="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs">
                    <div>
                      <div className="font-semibold text-slate-800">{d.eventType}</div>
                      <div className="font-mono text-[10px] text-slate-400">{d.eventId}</div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          d.status === 'SUCCESS'
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-rose-100 text-rose-700'
                        }`}
                      >
                        {d.httpStatus ? `HTTP ${d.httpStatus}` : d.status}
                      </span>
                      <span className="text-[10px] text-slate-400">{formatTimestamp(d.dispatchedAt)}</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
