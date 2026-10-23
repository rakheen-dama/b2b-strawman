/**
 * Tab group configuration for the grouped matter-detail tab bar.
 *
 * 7 top-level groups / 22 sub-tabs (Phase 87 §12.6.5; Team added Phase 88 §13.3.4, ADR-346).
 * Labels are the legal words — there is no runtime vocabulary layer (ADR-338). Group ids and deep-linked tab ids (`court-dates`,
 * `disbursements`, `statements`, `trust`, `time`, `documents`) are stable: specs and scenario docs
 * address them as `tab-group-{groupId}` / `tab-item-{tabId}` / `?tab=`. The renamed id
 * (`customers` → `clients`) and the old setup-step id (`members` → `team`) are aliased in
 * `legacy-tab-ids.ts`.
 *
 * Architecture: Section 11.4.1, Section 12.6.5, ADR-287, ADR-340 (no Expenses tab).
 */

import type { TabGroup } from "./tab-group-types";
import { buildTabIdToGroupMap } from "./tab-group-types";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Matter tab group definitions (7 groups, 22 sub-tabs). */
export const MATTER_TAB_GROUPS: readonly TabGroup[] = [
  {
    id: "details",
    label: "Details",
    tabs: [
      { id: "details", label: "Details" },
      { id: "fields", label: "Custom fields" },
      { id: "team", label: "Team" },
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
    label: "Work",
    tabs: [
      { id: "tasks", label: "Tasks" },
      { id: "documents", label: "Documents" },
      { id: "correspondence", label: "Correspondence" },
      { id: "generated", label: "Generated documents" },
    ],
    visible: true,
  },
  {
    id: "finance",
    label: "Fees",
    tabs: [
      { id: "time", label: "Time recording" },
      { id: "disbursements", label: "Disbursements" },
      { id: "budget", label: "Fee estimate" },
      { id: "rates", label: "Tariffs" },
      { id: "financials", label: "Financials" },
      { id: "statements", label: "Statements of account" },
      { id: "trust", label: "Trust" },
    ],
    visible: true,
  },
  {
    id: "client",
    label: "Client",
    tabs: [
      { id: "clients", label: "Clients" },
      { id: "requests", label: "Information requests" },
      { id: "customer-comments", label: "Client comments" },
      { id: "adverse-parties", label: "Adverse parties" },
    ],
    visible: true,
  },
  {
    id: "schedule",
    label: "Diary",
    tabs: [{ id: "court-dates", label: "Court dates" }],
    visible: true,
  },
  {
    id: "activity",
    label: "Activity",
    tabs: [
      { id: "activity", label: "Activity" },
      { id: "audit", label: "Audit trail" },
    ],
    visible: true,
  },
];

/** Reverse lookup: tab ID -> group ID (O(1)). */
export const MATTER_TAB_ID_TO_GROUP_MAP: Record<string, string> =
  buildTabIdToGroupMap(MATTER_TAB_GROUPS);
