export type RecurrenceFrequency = "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";

export interface ParsedRecurrence {
  frequency: RecurrenceFrequency;
  interval: number;
}

/**
 * Formats a frequency and interval into an RRULE string.
 * Returns null if frequency is null/undefined (no recurrence).
 */
export function formatRecurrenceRule(
  frequency: RecurrenceFrequency | null,
  interval: number
): string | null {
  if (!frequency) return null;
  return `FREQ=${frequency};INTERVAL=${interval}`;
}

/**
 * Parses an RRULE string back into frequency and interval.
 * Returns null if the rule is null/empty or malformed.
 */
export function parseRecurrenceRule(rule: string | null): ParsedRecurrence | null {
  if (!rule) return null;
  const parts = Object.fromEntries(
    rule.split(";").map((p) => {
      const [k, v] = p.split("=");
      return [k, v];
    })
  );
  const frequency = parts.FREQ as RecurrenceFrequency | undefined;
  const interval = parts.INTERVAL ? parseInt(parts.INTERVAL, 10) : 1;
  if (!frequency) return null;
  return { frequency, interval };
}

/**
 * Returns a human-readable description of a recurrence rule.
 * e.g. "Every 2 weeks", "Daily", "Monthly"
 */
export function describeRecurrence(rule: string | null): string {
  const parsed = parseRecurrenceRule(rule);
  if (!parsed) return "None";

  const labels: Record<RecurrenceFrequency, { singular: string; plural: string }> = {
    DAILY: { singular: "Daily", plural: "days" },
    WEEKLY: { singular: "Weekly", plural: "weeks" },
    MONTHLY: { singular: "Monthly", plural: "months" },
    YEARLY: { singular: "Yearly", plural: "years" },
  };

  const label = labels[parsed.frequency];
  if (parsed.interval === 1) return label.singular;
  return `Every ${parsed.interval} ${label.plural}`;
}
