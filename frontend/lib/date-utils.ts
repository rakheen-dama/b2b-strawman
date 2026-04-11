/**
 * Returns the Monday of the current week.
 */
export function getCurrentMonday(): Date {
  const now = new Date();
  const day = now.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + diff);
}

/**
 * Formats a Date as an ISO date string (YYYY-MM-DD) using en-CA locale.
 */
export function formatDate(date: Date): string {
  return date.toLocaleDateString("en-CA");
}

/**
 * Returns a new Date offset by a number of weeks.
 */
export function addWeeks(date: Date, weeks: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + weeks * 7);
  return result;
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
