import type { AuditEventTypeMetadata, AuditSeverity } from "@/lib/api/audit-events";

/**
 * Filter presets for the audit log page (Epic 506B / 506.9).
 *
 * Pure module — no React. The `<AuditLogClient>` calls `resolvePreset()` and
 * mutates the page URL query string with the returned filter deltas.
 */

export type PresetName = "sensitive" | "compliance" | "security" | "financial-approvals";

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

/**
 * Sentinel `eventType` value used when a preset resolves to multiple event
 * types. The backend list endpoint currently accepts a single `eventType`
 * query param only; rather than silently narrowing to the first match (which
 * would mislead the user into thinking they're viewing all preset events),
 * the client sets `eventType` to this impossible value so the result set is
 * empty, and renders a visible banner explaining the situation. Removed once
 * the backend supports multi-value filtering.
 *
 * TODO(506B-followup): replace with proper multi-eventType backend filter.
 */
export const MULTI_EVENT_SENTINEL = "__multi__";

export interface ResolvedPreset {
  from?: string;
  severities?: AuditSeverity[];
  /**
   * Multi-event-type list resolved from metadata or static config. When this
   * has length >= 2, the client uses {@link MULTI_EVENT_SENTINEL} for the
   * `eventType` URL param and surfaces a banner. When length === 1, the single
   * event type is used directly. Length 0 means metadata was unavailable / no
   * matches — also surfaced via the banner (fail-closed).
   */
  eventTypes?: string[];
  /**
   * True when this preset *expects* event-type filtering (Compliance,
   * Security, Financial approvals) but cannot be applied as a single filter.
   * Used by the client to decide whether to show the multi-event banner.
   */
  isGroupPreset?: boolean;
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
        eventTypes: metadata.filter((m) => m.group === "COMPLIANCE").map((m) => m.eventType),
        isGroupPreset: true,
      };
    case "security":
      return {
        from: isoDaysAgo(now, 7),
        eventTypes: metadata.filter((m) => m.group === "SECURITY").map((m) => m.eventType),
        isGroupPreset: true,
      };
    case "financial-approvals":
      return {
        from: isoDaysAgo(now, 30),
        eventTypes: [...FINANCIAL_APPROVAL_EVENTS],
        isGroupPreset: true,
      };
  }
}
