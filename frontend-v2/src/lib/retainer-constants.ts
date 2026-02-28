import type {
  RetainerType,
  RetainerFrequency,
  RolloverPolicy,
} from "@/lib/api/retainers";

export const FREQUENCY_LABELS: Record<RetainerFrequency, string> = {
  WEEKLY: "Weekly",
  FORTNIGHTLY: "Fortnightly",
  MONTHLY: "Monthly",
  QUARTERLY: "Quarterly",
  SEMI_ANNUALLY: "Semi-annually",
  ANNUALLY: "Annually",
};

export const TYPE_LABELS: Record<RetainerType, string> = {
  HOUR_BANK: "Hour Bank",
  FIXED_FEE: "Fixed Fee",
};

export const ROLLOVER_LABELS: Record<RolloverPolicy, string> = {
  FORFEIT: "Forfeit unused hours",
  CARRY_FORWARD: "Carry forward (unlimited)",
  CARRY_CAPPED: "Carry forward (capped)",
};
