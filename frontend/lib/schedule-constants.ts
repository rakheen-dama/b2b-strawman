export type ScheduleStatus = "ACTIVE" | "PAUSED" | "COMPLETED";

export type RecurrenceFrequency =
  | "WEEKLY"
  | "FORTNIGHTLY"
  | "MONTHLY"
  | "QUARTERLY"
  | "SEMI_ANNUALLY"
  | "ANNUALLY";

export const FREQUENCY_LABELS: Record<RecurrenceFrequency, string> = {
  WEEKLY: "Weekly",
  FORTNIGHTLY: "Fortnightly",
  MONTHLY: "Monthly",
  QUARTERLY: "Quarterly",
  SEMI_ANNUALLY: "Semi-Annually",
  ANNUALLY: "Annually",
};

export const FREQUENCY_OPTIONS: { value: RecurrenceFrequency; label: string }[] = Object.entries(
  FREQUENCY_LABELS,
).map(([value, label]) => ({ value: value as RecurrenceFrequency, label }));
