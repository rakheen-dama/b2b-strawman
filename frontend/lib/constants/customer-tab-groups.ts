/**
 * Tab group configuration for the customer detail page.
 *
 * Defines 6 top-level groups and their sub-tabs for the Phase 77 customer
 * detail redesign. Labels are canonical English — terminology rewriting is
 * applied by the caller.
 *
 * Architecture: Section 77.4.1, ADR-298.
 */

import type { TabGroup } from "./tab-group-types";
import { buildTabIdToGroupMap } from "./tab-group-types";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Customer tab group definitions (6 groups, 15 sub-tabs). */
export const CUSTOMER_TAB_GROUPS: readonly TabGroup[] = [
  {
    id: "details",
    label: "Details",
    tabs: [
      { id: "details", label: "Details" },
      { id: "fields", label: "Fields" },
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
    label: "Work",
    tabs: [
      { id: "projects", label: "Projects" },
      { id: "documents", label: "Documents" },
      { id: "generated", label: "Generated Docs" },
    ],
    visible: true,
  },
  {
    id: "finance",
    label: "Finance",
    tabs: [
      { id: "invoices", label: "Invoices" },
      { id: "rates", label: "Rates" },
      { id: "retainer", label: "Retainer" },
      { id: "financials", label: "Financials" },
      { id: "trust", label: "Trust" },
    ],
    visible: true,
  },
  {
    id: "compliance",
    label: "Compliance",
    tabs: [
      { id: "onboarding", label: "Onboarding" },
      { id: "requests", label: "Requests" },
    ],
    visible: true,
  },
  {
    id: "activity",
    label: "Activity",
    tabs: [{ id: "audit", label: "Audit" }],
    visible: true,
  },
];

/** Reverse lookup: tab ID -> group ID (O(1)). */
export const CUSTOMER_TAB_ID_TO_GROUP_MAP: Record<string, string> =
  buildTabIdToGroupMap(CUSTOMER_TAB_GROUPS);
