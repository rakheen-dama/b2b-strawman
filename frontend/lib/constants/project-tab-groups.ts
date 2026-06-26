/**
 * Tab group configuration for the grouped matter-detail tab bar.
 *
 * Defines the 7 top-level groups (details, overview, work, finance, client,
 * schedule, activity) and their sub-tabs. Labels are canonical English —
 * terminology rewriting is applied by the caller when building the `groups`
 * prop for `GroupedTabBar`.
 *
 * Architecture: Section 11.4.1, ADR-287.
 */

import type { TabGroup } from "./tab-group-types";
import { buildTabIdToGroupMap } from "./tab-group-types";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Backward-compat redirect: `?tab=members` -> staffing sub-tab. */
export const MEMBERS_TAB_REDIRECT = "staffing";

/** Canonical tab group definitions (7 groups, 24 sub-tabs). */
export const TAB_GROUPS: readonly TabGroup[] = [
  {
    id: "details",
    label: "Details",
    tabs: [
      { id: "details", label: "Details" },
      { id: "fields", label: "Fields" },
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
      { id: "generated", label: "Generated Docs" },
      { id: "staffing", label: "Staffing" },
    ],
    visible: true,
  },
  {
    id: "finance",
    label: "Finance",
    tabs: [
      { id: "time", label: "Time" },
      { id: "expenses", label: "Expenses" },
      { id: "disbursements", label: "Disbursements" },
      { id: "budget", label: "Budget" },
      { id: "rates", label: "Rates" },
      { id: "financials", label: "Financials" },
      { id: "statements", label: "Statements" },
      { id: "trust", label: "Trust" },
    ],
    visible: true,
  },
  {
    id: "client",
    label: "Client",
    tabs: [
      { id: "customers", label: "Customers" },
      { id: "requests", label: "Requests" },
      { id: "customer-comments", label: "Client Comments" },
      { id: "adverse-parties", label: "Adverse Parties" },
    ],
    visible: true,
  },
  {
    id: "schedule",
    label: "Schedule",
    tabs: [{ id: "court-dates", label: "Court Dates" }],
    visible: true,
  },
  {
    id: "activity",
    label: "Activity",
    tabs: [
      { id: "activity", label: "Activity" },
      { id: "audit", label: "Audit" },
    ],
    visible: true,
  },
];

/** Reverse lookup: tab ID -> group ID (O(1)). */
export const TAB_ID_TO_GROUP_MAP: Record<string, string> = buildTabIdToGroupMap(TAB_GROUPS);
