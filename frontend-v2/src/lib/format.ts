export function formatDate(date: string | Date): string {
  return new Date(date).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function formatDateShort(date: string | Date): string {
  return new Date(date).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const value = bytes / Math.pow(k, i);
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`;
}

/**
 * Formats a duration in minutes as "Xh Ym", "Xh", or "Ym".
 * Returns "0m" for zero or negative values.
 */
export function formatDuration(minutes: number): string {
  if (minutes <= 0) return "0m";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h > 0 && m > 0) return `${h}h ${m}m`;
  if (h > 0) return `${h}h`;
  return `${m}m`;
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 365 * 24 * 60 * 60],
  ["month", 30 * 24 * 60 * 60],
  ["week", 7 * 24 * 60 * 60],
  ["day", 24 * 60 * 60],
  ["hour", 60 * 60],
  ["minute", 60],
];

const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

/**
 * Formats a number as currency (e.g. "$125.00").
 */
export function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/**
 * Null-safe currency formatter. Returns "N/A" if amount or currency is missing.
 */
export function formatCurrencySafe(
  amount: number | null | undefined,
  currency: string | null | undefined,
): string {
  if (amount == null || !currency) return "N/A";
  return formatCurrency(amount, currency);
}

export function formatRelativeDate(date: string | Date): string {
  const seconds = Math.round((new Date(date).getTime() - Date.now()) / 1000);
  for (const [unit, threshold] of UNITS) {
    if (Math.abs(seconds) >= threshold) {
      return rtf.format(Math.round(seconds / threshold), unit);
    }
  }
  return rtf.format(seconds, "second");
}

/**
 * Formats a "YYYY-MM-DD" date string using local calendar arithmetic
 * (avoids UTC-midnight off-by-one issues).
 */
export function formatLocalDate(yyyyMmDd: string): string {
  const [year, month, day] = yyyyMmDd.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Returns true if the given "YYYY-MM-DD" deadline is strictly before today.
 */
export function isOverdue(deadline: string): boolean {
  const today = new Date().toLocaleDateString("en-CA"); // "YYYY-MM-DD" in local timezone
  return deadline < today;
}

/**
 * Formats an ISO timestamp as a short date (en-ZA locale: "19 Feb 2026").
 */
export function formatComplianceDate(isoString: string): string {
  return new Date(isoString).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Formats an ISO timestamp with date and time (en-ZA locale).
 */
export function formatComplianceDateWithTime(isoString: string): string {
  return new Date(isoString).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
