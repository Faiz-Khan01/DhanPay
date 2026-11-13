import { useCallback, useEffect, useState } from 'react';
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowUpRight,
  BookOpenCheck,
  ChevronLeft,
  ChevronRight,
  Download,
  FileCheck2,
  Filter,
  Search,
  X,
} from 'lucide-react';
import client, { extractApiError } from '../api/client';
import {
  exportTransactionsCsv,
  formatINR,
  formatTimestamp,
  getStatusBadge,
} from '../utils/formatters';
import StatementModal from '../components/StatementModal';

export default function TransactionHistoryPage() {
  const [items, setItems] = useState([]);
  const [searchInput, setSearchInput] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [directionFilter, setDirectionFilter] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);

  const [meta, setMeta] = useState({
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });

  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [statementModalOpen, setStatementModalOpen] = useState(false);
  const [journalModal, setJournalModal] = useState(null);
  const [loadingJournal, setLoadingJournal] = useState(false);

  const handleInspectJournal = async (reference) => {
    setErrorMsg('');
    setLoadingJournal(true);
    try {
      const { data } = await client.get(`/transfers/${reference}/journal`);
      setJournalModal(data);
    } catch (err) {
      const { message } = extractApiError(err, 'Unable to load ledger journal for this transfer.');
      setErrorMsg(message);
    } finally {
      setLoadingJournal(false);
    }
  };

  const fetchHistory = useCallback(async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const params = {
        page,
        size,
      };
      if (appliedSearch) params.search = appliedSearch;
      if (statusFilter) params.status = statusFilter;
      if (directionFilter) params.direction = directionFilter;

      const { data } = await client.get('/transfers', { params });
      setItems(data?.content || []);
      setMeta({
        totalElements: data?.totalElements ?? 0,
        totalPages: data?.totalPages ?? 0,
        first: Boolean(data?.first ?? page === 0),
        last: Boolean(data?.last ?? true),
      });
    } catch (err) {
      const { message } = extractApiError(err, 'Unable to load transaction history.');
      setErrorMsg(message);
    } finally {
      setLoading(false);
    }
  }, [page, size, appliedSearch, statusFilter, directionFilter]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    setAppliedSearch(searchInput.trim());
  };

  const handleClearFilters = () => {
    setSearchInput('');
    setAppliedSearch('');
    setStatusFilter('');
    setDirectionFilter('');
    setPage(0);
  };

  const hasActiveFilters = Boolean(appliedSearch || statusFilter || directionFilter);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Transaction History</h1>
          <p className="text-sm text-slate-500">
            Search by transfer reference or counterparty wallet, and filter by status or direction.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setStatementModalOpen(true)}
            className="inline-flex items-center gap-2 rounded-full border border-[#05334D] bg-white px-4 py-2 text-xs font-semibold text-[#05334D] transition hover:bg-[#E6FBF5] shadow-sm"
          >
            <FileCheck2 className="h-3.5 w-3.5 text-[#00D084]" />
            Certified Statement
          </button>

          <button
            type="button"
            disabled={items.length === 0}
            onClick={() => exportTransactionsCsv(items, `dhanpay-history-page-${page + 1}.csv`)}
            className="inline-flex items-center gap-2 rounded-full bg-[#05334D] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#084566] disabled:opacity-40"
          >
            <Download className="h-3.5 w-3.5 text-[#00D084]" />
            + Export CSV
          </button>
        </div>
      </div>

      {/* Filters & Search Bar */}
      <section className="rounded-2xl border border-slate-100 bg-slate-50/80 p-4 sm:p-5 space-y-4">
        <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search reference (TXN-...) or counterparty wallet (WLT-IND-...)"
              className="w-full rounded-full border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-900 placeholder-slate-400 focus:border-[#00C896] focus:outline-none focus:ring-2 focus:ring-[#00C896]/25"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <select
              aria-label="Filter by status"
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
              className="rounded-full border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 focus:border-[#00C896] focus:outline-none"
            >
              <option value="">All Statuses</option>
              <option value="SUCCESS">Approved (SUCCESS)</option>
              <option value="INITIATED">Pending (INITIATED)</option>
              <option value="FAILED">Declined (FAILED)</option>
            </select>

            <select
              aria-label="Filter by direction"
              value={directionFilter}
              onChange={(e) => {
                setDirectionFilter(e.target.value);
                setPage(0);
              }}
              className="rounded-full border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 focus:border-[#00C896] focus:outline-none"
            >
              <option value="">All Directions</option>
              <option value="DEBIT">Sent (DEBIT)</option>
              <option value="CREDIT">Received (CREDIT)</option>
            </select>

            <select
              aria-label="Rows per page"
              value={size}
              onChange={(e) => {
                setSize(Number(e.target.value));
                setPage(0);
              }}
              className="rounded-full border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 focus:border-[#00C896] focus:outline-none"
            >
              <option value={10}>10 / page</option>
              <option value={20}>20 / page</option>
              <option value={50}>50 / page</option>
            </select>

            <button
              type="submit"
              className="inline-flex items-center gap-1.5 rounded-full bg-[#00D084] px-4 py-2.5 text-xs font-bold text-[#032539] transition hover:bg-[#00C896]"
            >
              <Filter className="h-3.5 w-3.5" />
              Search
            </button>

            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-600 transition hover:bg-slate-100"
              >
                <X className="h-3.5 w-3.5" />
                Clear
              </button>
            )}
          </div>
        </form>
      </section>

      {errorMsg && (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Results Table */}
      <section className="overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card">
        {loading ? (
          <div className="space-y-3 p-6">
            {[1, 2, 3, 4, 5].map((idx) => (
              <div key={idx} className="h-12 animate-pulse rounded-xl bg-slate-100" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="px-6 py-14 text-center">
            <p className="text-sm font-semibold text-slate-700">No matching transfers found.</p>
            <p className="mt-1 text-xs text-slate-400">
              Try clearing your search query or switching filters.
            </p>
          </div>
        ) : (
          <>
            <div className="hidden md:block overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100 text-left text-sm">
                <thead className="bg-slate-50/80 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                  <tr>
                    <th className="px-5 py-3.5">Direction</th>
                    <th className="px-5 py-3.5">Reference</th>
                    <th className="px-5 py-3.5">Counterparty</th>
                    <th className="px-5 py-3.5">Description</th>
                    <th className="px-5 py-3.5">Date &amp; Time</th>
                    <th className="px-5 py-3.5 text-right">Amount</th>
                    <th className="px-5 py-3.5 text-right">Status</th>
                    <th className="px-5 py-3.5 text-right">Ledger</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {items.map((tx) => {
                    const isCredit = tx.direction === 'CREDIT';
                    const badge = getStatusBadge(tx.status, { preserveCode: true });
                    return (
                      <tr key={tx.reference} className="transition hover:bg-slate-50/80">
                        <td className="px-5 py-4">
                          <span className="inline-flex items-center gap-2 text-xs font-semibold text-slate-700">
                            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-slate-100 text-[#05334D]">
                              {isCredit ? (
                                <ArrowDownLeft className="h-4 w-4 text-[#00C896]" />
                              ) : (
                                <ArrowUpRight className="h-4 w-4 text-slate-600" />
                              )}
                            </span>
                            {isCredit ? 'Received' : 'Sent'}
                          </span>
                        </td>
                        <td className="px-5 py-4 font-mono text-xs text-slate-500">
                          {tx.reference}
                        </td>
                        <td className="px-5 py-4">
                          <p className="font-semibold text-slate-800">{tx.counterpartyName || '—'}</p>
                          <p className="font-mono text-xs text-slate-400">
                            {tx.counterpartyWalletReference}
                          </p>
                        </td>
                        <td className="px-5 py-4 text-xs text-slate-500 max-w-xs truncate">
                          {tx.description || tx.failureReason || '—'}
                        </td>
                        <td className="px-5 py-4 text-xs text-slate-400 whitespace-nowrap">
                          {formatTimestamp(tx.createdAt)}
                        </td>
                        <td
                          className={`px-5 py-4 text-right font-semibold tabular-nums whitespace-nowrap ${
                            isCredit ? 'text-emerald-600' : 'text-slate-800'
                          }`}
                        >
                          {isCredit ? '+' : '-'}
                          {formatINR(tx.amount)}
                        </td>
                        <td className="px-5 py-4 text-right">
                          <span className={`inline-flex rounded-full px-3 py-1 text-[11px] font-semibold ${badge.pill}`}>
                            {badge.label}
                          </span>
                        </td>
                        <td className="px-5 py-4 text-right">
                          <button
                            type="button"
                            disabled={loadingJournal}
                            onClick={() => handleInspectJournal(tx.reference)}
                            className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-3 py-1 text-[11px] font-semibold text-[#05334D] transition hover:bg-[#DDF2F1]"
                          >
                            <BookOpenCheck className="h-3.5 w-3.5 text-[#00C896]" />
                            Journal
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="divide-y divide-slate-100 md:hidden">
              {items.map((tx) => {
                const isCredit = tx.direction === 'CREDIT';
                const badge = getStatusBadge(tx.status, { preserveCode: true });
                return (
                  <div key={tx.reference} className="p-4 space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-sm text-slate-800">
                        {tx.counterpartyName || tx.counterpartyWalletReference}
                      </span>
                      <span
                        className={`text-sm font-bold tabular-nums ${
                          isCredit ? 'text-emerald-600' : 'text-slate-800'
                        }`}
                      >
                        {isCredit ? '+' : '-'}
                        {formatINR(tx.amount)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-xs text-slate-400">
                      <span className="font-mono">{tx.reference}</span>
                      <span className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${badge.pill}`}>
                        {badge.label}
                      </span>
                    </div>
                    <p className="text-xs text-slate-400">{formatTimestamp(tx.createdAt)}</p>
                  </div>
                );
              })}
            </div>

            {/* Pagination Footer */}
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/70 px-5 py-3.5 text-xs text-slate-500">
              <span>
                Showing page <strong className="text-slate-800">{page + 1}</strong> of{' '}
                <strong className="text-slate-800">{Math.max(meta.totalPages, 1)}</strong> (
                {meta.totalElements} total records)
              </span>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={meta.first || loading}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                  Previous
                </button>
                <button
                  type="button"
                  disabled={meta.last || loading}
                  onClick={() => setPage((p) => p + 1)}
                  className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Next
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </>
        )}
      </section>

      {journalModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#032539]/60 p-4 backdrop-blur-sm">
          <div className="w-full max-w-xl overflow-hidden rounded-3xl border border-white/80 bg-white shadow-2xl">
            <div className="flex items-start justify-between bg-[#05334D] px-6 py-4 text-white">
              <div>
                <h3 className="text-base font-bold">Double-Entry Ledger Journal</h3>
                <p className="mt-0.5 font-mono text-xs text-emerald-300">
                  {journalModal.ledgerReference} • {journalModal.transferReference}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setJournalModal(null)}
                className="text-slate-300 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-slate-50 px-4 py-3 text-xs text-slate-600">
                <span>
                  Type: <strong className="text-slate-900">{journalModal.transactionType}</strong>
                </span>
                <span>
                  Status: <strong className="text-emerald-600">{journalModal.ledgerStatus}</strong>
                </span>
                <span className="font-bold text-[#05334D] tabular-nums">
                  {formatINR(journalModal.amount, { precise: true })}
                </span>
              </div>

              <div className="overflow-hidden rounded-2xl border border-slate-100">
                <table className="min-w-full divide-y divide-slate-100 text-left text-xs">
                  <thead className="bg-slate-50 font-semibold uppercase tracking-wider text-slate-400">
                    <tr>
                      <th className="px-4 py-2.5">GL Account</th>
                      <th className="px-4 py-2.5">Wallet Ref</th>
                      <th className="px-4 py-2.5">Posting</th>
                      <th className="px-4 py-2.5 text-right">Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {journalModal.entries?.map((line, idx) => (
                      <tr key={`${line.accountNumber}-${idx}`}>
                        <td className="px-4 py-3 font-mono text-slate-700">{line.accountNumber}</td>
                        <td className="px-4 py-3 font-mono text-slate-500">
                          {line.walletReference || line.accountType}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                              line.direction === 'CREDIT'
                                ? 'bg-emerald-50 text-emerald-700'
                                : 'bg-orange-50 text-orange-600'
                            }`}
                          >
                            {line.direction}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right font-bold text-slate-800 tabular-nums">
                          {formatINR(line.amount, { precise: true })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={() => setJournalModal(null)}
                  className="rounded-full bg-[#05334D] px-5 py-2 text-xs font-semibold text-white hover:bg-[#084566]"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
      {/* Certified Statement Modal */}
      <StatementModal
        isOpen={statementModalOpen}
        onClose={() => setStatementModalOpen(false)}
      />
    </div>
  );
}
