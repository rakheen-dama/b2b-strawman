/**
 * Returns the number of whole days between today (UTC midnight) and the given
 * YYYY-MM-DD date string. Negative values are clamped to 0.
 */
export function daysUntil(isoDate: string): number {
  const [year, month, day] = isoDate.split("-").map(Number);
  const endUtc = Date.UTC(year, month - 1, day);
  const now = new Date();
  const todayUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const diffMs = endUtc - todayUtc;
  return Math.max(0, Math.floor(diffMs / (1000 * 60 * 60 * 24)));
}

/**
 * Resolves date range from URL search params, defaulting to current month.
 */
export function resolveDateRange(searchParams: { from?: string; to?: string }): {
  from: string;
  to: string;
} {
  if (searchParams.from && searchParams.to) {
    return { from: searchParams.from, to: searchParams.to };
  }

  // Default to current month boundaries
  const now = new Date();
  const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);

  const formatDate = (d: Date): string => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  };

  return {
    from: searchParams.from || formatDate(firstDay),
    to: searchParams.to || formatDate(lastDay),
  };
}
