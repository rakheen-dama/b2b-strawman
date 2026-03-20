/**
 * Derives the period key from a due date string.
 * Returns the year portion (e.g., "2026" from "2026-08-31").
 * This matches the backend's periodKey format for annual deadlines.
 */
export function derivePeriodKey(dueDate: string): string {
  return dueDate.substring(0, 4);
}
