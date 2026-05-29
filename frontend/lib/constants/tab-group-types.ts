/**
 * Shared tab-group types and utility functions.
 *
 * Entity-agnostic — no project/customer/matter-specific logic belongs here.
 * Entity-specific constants live in `project-tab-groups.ts`, `customer-tab-groups.ts`, etc.
 *
 * Architecture: Section 77.3.3, ADR-298.
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
// Utilities
// ---------------------------------------------------------------------------

/** Build a reverse lookup map: tab ID -> group ID. */
export function buildTabIdToGroupMap(groups: readonly TabGroup[]): Record<string, string> {
  return Object.fromEntries(
    groups.flatMap((g) => g.tabs.map((t) => [t.id, g.id]))
  );
}

// ---------------------------------------------------------------------------
// URL state resolution
// ---------------------------------------------------------------------------

export const DEFAULT_RESOLUTION = { groupId: "overview", tabId: "overview" } as const;

/**
 * Resolve a `?tab=` search-param value to its group + tab pair.
 *
 * Priority order:
 * 1. `null` -> overview default
 * 2. Known tab ID -> its owning group
 * 3. Value is itself a group ID -> first visible sub-tab
 * 4. Fallback -> overview default
 *
 * NOTE: Entity-specific redirects (e.g. `members -> staffing` for projects)
 * must be applied by the caller **before** invoking this function.
 */
export function resolveTabFromUrl(
  tabParam: string | null,
  groups: readonly TabGroup[]
): { groupId: string; tabId: string } {
  // 1. null -> default
  if (tabParam === null) return DEFAULT_RESOLUTION;

  // 2. Known tab ID -> find its group
  for (const group of groups) {
    if (group.tabs.some((t) => t.id === tabParam)) {
      return { groupId: group.id, tabId: tabParam };
    }
  }

  // 3. Value is a group ID -> first visible sub-tab
  const matchedGroup = groups.find((g) => g.id === tabParam);
  if (matchedGroup && matchedGroup.tabs.length > 0) {
    const firstVisible = matchedGroup.tabs.find((t) => t.visible !== false);
    if (firstVisible) {
      return { groupId: matchedGroup.id, tabId: firstVisible.id };
    }
    // All tabs invisible — fall through to default
  }

  // 4. Fallback
  return DEFAULT_RESOLUTION;
}

/** O(1) lookup: which group owns this tab? Returns `null` if unknown. */
export function getGroupForTab(tabId: string, groupMap: Record<string, string>): string | null {
  return groupMap[tabId] ?? null;
}
