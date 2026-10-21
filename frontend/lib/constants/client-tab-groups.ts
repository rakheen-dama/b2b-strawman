/**
 * Tab group configuration for the client detail page.
 *
 * 6 top-level groups / 14 sub-tabs (Phase 77 client detail; relabelled in Phase 87 §12.6.5).
 * Labels are the legal words (ADR-338). Group ids are stable (`tab-group-{groupId}` in specs);
 * the renamed tab id (`projects` → `matters`) is aliased in `legacy-tab-ids.ts`.
 *
 * Architecture: Section 77.4.1, Section 12.6.5, ADR-298.
 */

import type { TabGroup } from "./tab-group-types";
import { buildTabIdToGroupMap } from "./tab-group-types";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Client tab group definitions (6 groups, 14 sub-tabs). */
export const CLIENT_TAB_GROUPS: readonly TabGroup[] = [
  {
    id: "details",
    label: "Details",
    tabs: [
      { id: "details", label: "Details" },
      { id: "fields", label: "Custom fields" },
      { id: "tags", label: "Tags" },
    ],
    visible: true,
  },
  {
    id: "overview",
    label: "Overview",
    tabs: [{ id: "overview", label: "Overview" }],
    visible: true,
  },
  {
    id: "work",
    label: "Matters",
    tabs: [
      { id: "matters", label: "Matters" },
      { id: "documents", label: "Documents" },
      { id: "generated", label: "Generated documents" },
    ],
    visible: true,
  },
  {
    id: "finance",
    label: "Fees",
    tabs: [
      { id: "invoices", label: "Fee notes" },
      { id: "rates", label: "Tariffs" },
      { id: "financials", label: "Financials" },
      { id: "trust", label: "Trust" },
    ],
    visible: true,
  },
  {
    id: "compliance",
    label: "FICA & compliance",
    tabs: [
      { id: "onboarding", label: "FICA onboarding" },
      { id: "requests", label: "Information requests" },
    ],
    visible: true,
  },
  {
    id: "activity",
    label: "Activity",
    tabs: [{ id: "audit", label: "Audit trail" }],
    visible: true,
  },
];

/** Reverse lookup: tab ID -> group ID (O(1)). */
export const CLIENT_TAB_ID_TO_GROUP_MAP: Record<string, string> =
  buildTabIdToGroupMap(CLIENT_TAB_GROUPS);
