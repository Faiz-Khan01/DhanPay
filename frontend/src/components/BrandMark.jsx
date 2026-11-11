export default function BrandMark({ showTagline = false, compact = false, dark = false }) {
  return (
    <div className="flex items-center gap-3">
      <div
        className={`flex items-center justify-center rounded-full ${
          compact ? 'h-9 w-9' : 'h-11 w-11'
        } ${dark ? 'bg-white/10 ring-1 ring-white/15' : 'bg-[#E6F8F3]'}`}
      >
        <svg
          viewBox="0 0 36 36"
          className={compact ? 'h-6 w-6' : 'h-7 w-7'}
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M18 5.5A12.5 12.5 0 1 1 7.6 25"
            stroke="#00C896"
            strokeWidth="3.5"
            strokeLinecap="round"
          />
          <circle cx="18" cy="18" r="3.5" fill={dark ? '#FFFFFF' : '#05334D'} />
        </svg>
      </div>
      <div>
        <div className="flex items-center gap-1.5">
          <span
            className={`font-bold tracking-tight ${compact ? 'text-lg' : 'text-xl'} ${
              dark ? 'text-white' : 'text-[#0F172A]'
            }`}
          >
            Dhan<span className="text-[#00C896]">Pay</span>
          </span>
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider ${
              dark
                ? 'bg-white/10 text-emerald-300 ring-1 ring-white/15'
                : 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200/80'
            }`}
          >
            INR
          </span>
        </div>
        {showTagline && (
          <p className={`text-xs ${dark ? 'text-slate-300' : 'text-slate-500'}`}>
            Secure Transfers. Smarter Money Management.
          </p>
        )}
      </div>
    </div>
  );
}
