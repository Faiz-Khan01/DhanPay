const inrFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const inrPreciseFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

export function formatINR(value, { precise = false } = {}) {
  const numeric = Number(value ?? 0);
  if (!Number.isFinite(numeric)) {
    return '₹0.00';
  }
  return precise ? inrPreciseFormatter.format(numeric) : inrFormatter.format(numeric);
}

export function formatTimestamp(isoString) {
  if (!isoString) {
    return '—';
  }
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  }).format(date);
}

export function generateIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const rand = (Math.random() * 16) | 0;
    const value = char === 'x' ? rand : (rand & 0x3) | 0x8;
    return value.toString(16);
  });
}

export function getInitials(fullName = '') {
  const parts = String(fullName).trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return 'DP';
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function formatShortTime(isoString) {
  if (!isoString) {
    return '—';
  }
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

const STATUS_STYLE_MAP = {
  SUCCESS: {
    label: 'Approved',
    pill: 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200/70',
  },
  INITIATED: {
    label: 'Pending',
    pill: 'bg-orange-50 text-orange-500 ring-1 ring-orange-200/70',
  },
  PENDING: {
    label: 'Pending',
    pill: 'bg-orange-50 text-orange-500 ring-1 ring-orange-200/70',
  },
  FAILED: {
    label: 'Declined',
    pill: 'bg-rose-50 text-rose-500 ring-1 ring-rose-200/70',
  },
  ACTIVE: {
    label: 'Active',
    pill: 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200/70',
  },
  FROZEN: {
    label: 'Frozen',
    pill: 'bg-rose-50 text-rose-500 ring-1 ring-rose-200/70',
  },
};

export function getStatusBadge(status, { preserveCode = false } = {}) {
  const key = String(status || '').toUpperCase();
  const entry = STATUS_STYLE_MAP[key];
  if (entry) {
    return {
      label: preserveCode ? key : entry.label,
      pill: entry.pill,
    };
  }
  return {
    label: key || 'UNKNOWN',
    pill: 'bg-slate-100 text-slate-600 ring-1 ring-slate-200',
  };
}

export function exportTransactionsCsv(items = [], filename = 'dhanpay-transactions.csv') {
  const headers = [
    'Reference',
    'Direction',
    'Counterparty Name',
    'Counterparty Wallet',
    'Amount (INR)',
    'Currency',
    'Status',
    'Description',
    'Timestamp',
  ];
  const escapeCell = (value) => {
    const str = String(value ?? '');
    return `"${str.replace(/"/g, '""')}"`;
  };
  const rows = items.map((item) =>
    [
      item.reference,
      item.direction,
      item.counterpartyName,
      item.counterpartyWalletReference,
      item.amount,
      item.currency || 'INR',
      item.status,
      item.description || item.failureReason || '',
      item.createdAt,
    ]
      .map(escapeCell)
      .join(',')
  );
  const csvContent = [headers.join(','), ...rows].join('\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
