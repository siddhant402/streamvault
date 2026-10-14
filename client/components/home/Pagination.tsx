'use client';

function pageList(current: number, total: number): (number | '…')[] {
  const wanted = new Set([0, total - 1, current - 1, current, current + 1]);
  const nums = [...wanted].filter(n => n >= 0 && n < total).sort((a, b) => a - b);
  const out: (number | '…')[] = [];
  nums.forEach((n, i) => {
    if (i > 0 && n - nums[i - 1] > 1) out.push('…');
    out.push(n);
  });
  return out;
}

export default function Pagination({ page, totalPages, total, pageSize, onPage }: {
  page: number;
  totalPages: number;
  total: number;
  pageSize: number;
  onPage: (p: number) => void;
}) {
  if (totalPages <= 1) return null;
  const from = page * pageSize + 1;
  const to = Math.min((page + 1) * pageSize, total);

  return (
    <nav className="sv-pager" aria-label="Pagination">
      <span className="sv-pager-info">Showing {from}–{to} of {total}</span>
      <div className="sv-pages">
        <button className="sv-page-btn" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Previous page">‹</button>
        {pageList(page, totalPages).map((p, i) =>
          p === '…' ? (
            <span key={`gap-${i}`} className="sv-page-gap" aria-hidden="true">…</span>
          ) : (
            <button key={p} className="sv-page-btn" aria-current={p === page ? 'page' : undefined}
                    aria-label={`Page ${p + 1}`} onClick={() => onPage(p)}>
              {p + 1}
            </button>
          )
        )}
        <button className="sv-page-btn" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)} aria-label="Next page">›</button>
      </div>
    </nav>
  );
}
