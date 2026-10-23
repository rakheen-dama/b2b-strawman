/**
 * Legacy `?tab=` ids → current ids for the matter and client detail pages (Phase 87 §12.6.5, ADR-344).
 *
 * Old deep links (bookmarks, scenario docs, setup-step links) keep resolving after the two
 * noun-bearing ids were renamed and the generic Expenses tab was removed (ADR-340). Applied by the
 * caller BEFORE tab resolution (`tab-group-types.ts` stays entity-agnostic). One shared map: an alias
 * whose target is not a tab on the current page simply falls through to Overview, exactly as the
 * un-aliased id would.
 *
 * `members` → `team` (Phase 88, ADR-346): the old "Team members added" setup-step link and bookmarks
 * open the matter Team tab. On a client page `team` is not a tab, so it still falls to Overview.
 */
export const LEGACY_TAB_IDS = {
  customers: "clients",
  projects: "matters",
  expenses: "disbursements",
  members: "team",
} as const satisfies Readonly<Record<string, string>>;

/** Map a raw `?tab=` value through {@link LEGACY_TAB_IDS}; unknown values pass through unchanged. */
export function aliasLegacyTabId(tabParam: string | null): string | null {
  if (tabParam === null) return null;
  // Own-property check: `?tab=constructor` must not resolve to Object.prototype.constructor.
  return Object.prototype.hasOwnProperty.call(LEGACY_TAB_IDS, tabParam)
    ? LEGACY_TAB_IDS[tabParam as keyof typeof LEGACY_TAB_IDS]
    : tabParam;
}
