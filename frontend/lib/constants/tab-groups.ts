/**
 * Tab group configuration for the grouped matter-detail tab bar.
 *
 * Defines the 6 top-level groups (overview, work, finance, client, schedule,
 * activity) and their sub-tabs. Labels are canonical English — terminology
 * rewriting is applied by the caller when building the `groups` prop for
 * `GroupedTabBar`.
 *
 * Architecture: Section 11.4.1, ADR-287.
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface TabDefinition {
  readonly id: string;
  readonly label: string;
  readonly visible?: boolean; // defaults to true when omitted
}

export interface TabGroup {
  readonly id: string;
  readonly label: string;
  readonly tabs: readonly TabDefinition[];
  visible: boolean; // mutable — callers spread-override this
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Backward-compat redirect: `?tab=members` → staffing sub-tab. */
export const MEMBERS_TAB_REDIRECT = "staffing";

/** Canonical tab group definitions (6 groups, 21 sub-tabs). */
export const TAB_GROUPS: readonly TabGroup[] = [
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

/** Reverse lookup: tab ID → group ID (O(1)). */
export const TAB_ID_TO_GROUP_MAP: Record<string, string> = Object.fromEntries(
  TAB_GROUPS.flatMap((g) => g.tabs.map((t) => [t.id, g.id]))
);

// ---------------------------------------------------------------------------
// URL state resolution
// ---------------------------------------------------------------------------

const DEFAULT_RESOLUTION = { groupId: "overview", tabId: "overview" } as const;

/**
 * Resolve a `?tab=` search-param value to its group + tab pair.
 *
 * Priority order:
 * 1. `null` → overview default
 * 2. `"members"` → redirect to staffing (MEMBERS_TAB_REDIRECT)
 * 3. Known tab ID → its owning group
 * 4. Value is itself a group ID → first sub-tab
 * 5. Fallback → overview default
 */
export function resolveTabFromUrl(
  tabParam: string | null,
  groups: readonly TabGroup[]
): { groupId: string; tabId: string } {
  // 1. null → default
  if (tabParam === null) return DEFAULT_RESOLUTION;

  // 2. members redirect
  const resolved = tabParam === "members" ? MEMBERS_TAB_REDIRECT : tabParam;

  // 3. Known tab ID → find its group
  for (const group of groups) {
    if (group.tabs.some((t) => t.id === resolved)) {
      return { groupId: group.id, tabId: resolved };
    }
  }

  // 4. Value is a group ID → first visible sub-tab
  const matchedGroup = groups.find((g) => g.id === resolved);
  if (matchedGroup && matchedGroup.tabs.length > 0) {
    const firstVisible = matchedGroup.tabs.find((t) => t.visible !== false);
    if (firstVisible) {
      return { groupId: matchedGroup.id, tabId: firstVisible.id };
    }
    // All tabs invisible — fall through to default
  }

  // 5. Fallback
  return DEFAULT_RESOLUTION;
}

/** O(1) lookup: which group owns this tab? Returns `null` if unknown. */
export function getGroupForTab(tabId: string): string | null {
  return TAB_ID_TO_GROUP_MAP[tabId] ?? null;
}
