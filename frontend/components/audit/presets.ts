import type {
  AuditEventTypeMetadata,
  AuditSeverity,
} from "@/lib/api/audit-events";

/**
 * Filter presets for the audit log page (Epic 506B / 506.9).
 *
 * Pure module — no React. The `<AuditLogClient>` calls `resolvePreset()` and
 * mutates the page URL query string with the returned filter deltas.
 */

export type PresetName =
  | "sensitive"
  | "compliance"
  | "security"
  | "financial-approvals";

export interface PresetOption {
  value: PresetName;
  label: string;
}

export const PRESET_OPTIONS: PresetOption[] = [
  { value: "sensitive", label: "Sensitive (last 30 days)" },
  { value: "compliance", label: "Compliance (last 90 days)" },
  { value: "security", label: "Security (last 7 days)" },
  { value: "financial-approvals", label: "Financial approvals (last 30 days)" },
];

const DAY_MS = 24 * 60 * 60 * 1000;

function isoDaysAgo(now: Date, days: number): string {
  return new Date(now.getTime() - days * DAY_MS).toISOString();
}

const FINANCIAL_APPROVAL_EVENTS = [
  "trust.transaction.approved",
  "trust.transaction.rejected",
  "invoice.sent",
  "invoice.voided",
];

export interface ResolvedPreset {
  from?: string;
  severities?: AuditSeverity[];
  /**
   * Multi-event-type list. Note: the backend list endpoint currently accepts a
   * single `eventType` query param only — the client narrows to the first
   * member when applying the preset. See TODO in audit-log-client.tsx.
   */
  eventTypes?: string[];
}

export function resolvePreset(
  name: PresetName,
  metadata: AuditEventTypeMetadata[],
  now: Date = new Date()
): ResolvedPreset {
  switch (name) {
    case "sensitive":
      return {
        from: isoDaysAgo(now, 30),
        severities: ["WARNING", "CRITICAL"],
      };
    case "compliance":
      return {
        from: isoDaysAgo(now, 90),
        eventTypes: metadata
          .filter((m) => m.group === "COMPLIANCE")
          .map((m) => m.eventType),
      };
    case "security":
      return {
        from: isoDaysAgo(now, 7),
        eventTypes: metadata
          .filter((m) => m.group === "SECURITY")
          .map((m) => m.eventType),
      };
    case "financial-approvals":
      return {
        from: isoDaysAgo(now, 30),
        eventTypes: [...FINANCIAL_APPROVAL_EVENTS],
      };
  }
}
