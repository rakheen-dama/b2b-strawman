import { AlertTriangle } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

/** §16.3.10 / ADR-382 wording — a warning, never a refusal (650D). */
export const LOCKED_MONTH_WARNING =
  "This date falls in a month whose list is locked. The entry will be recorded and shown as a later adjustment.";

/** True when `date` (yyyy-MM-dd) is on or before the account's latest locked month end. */
export function isOnOrBeforeLock(date: string, lockedThrough: string | null): boolean {
  // ISO yyyy-MM-dd strings compare correctly as strings
  return lockedThrough !== null && /^\d{4}-\d{2}-\d{2}$/.test(date) && date <= lockedThrough;
}

interface LockedMonthWarningProps {
  /** The posting date the dialog will send (the fee transfer passes `value || todayInSA()`). */
  date: string;
  /** `TrustAccount.lockedThrough`: the latest locked list period end, or null. */
  lockedThrough: string | null;
}

/**
 * Phase 92 650D: shown under a posting dialog's date when the date falls in a month whose Rule
 * 54.15 list is locked. The posting is still recorded and shown against the list as an entry made
 * after the lock — this never blocks submission (ADR-382).
 */
export function LockedMonthWarning({ date, lockedThrough }: LockedMonthWarningProps) {
  if (!isOnOrBeforeLock(date, lockedThrough)) return null;
  return (
    // A warning, not an error: role="status" (the Alert primitive defaults to "alert")
    <Alert variant="warning" role="status" data-testid="locked-month-warning">
      <AlertTriangle />
      <AlertDescription>{LOCKED_MONTH_WARNING}</AlertDescription>
    </Alert>
  );
}
