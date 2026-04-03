/**
 * Formats a date string for display in billing components.
 * Uses en-ZA locale (day month year) with short month names.
 *
 * @param dateStr - ISO date string or null
 * @param fallback - Text to display when dateStr is null (default: "N/A")
 */
export function formatDate(
  dateStr: string | null,
  fallback: string = "N/A",
): string {
  if (!dateStr) return fallback;
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}
