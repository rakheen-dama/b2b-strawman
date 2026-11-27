import { formatLocalDate } from "@/lib/format";

/**
 * K-65 (founder-approved copy): a fee run made in the app has no name (the configure step never
 * sends one), so its title falls back to its period in the page's existing period format:
 * "Fee run · 01 Nov 2026 – 30 Nov 2026".
 */
export function billingRunTitle(run: {
  name: string | null;
  periodFrom: string;
  periodTo: string;
}): string {
  if (run.name && run.name.trim()) return run.name;
  return `Fee run · ${formatLocalDate(run.periodFrom)} – ${formatLocalDate(run.periodTo)}`;
}
