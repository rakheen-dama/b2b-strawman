/**
 * One access rule for every navigation surface (Phase 87 S9, 616E, ADR-341 §12.7.3): the sidebar
 * zones (components/nav-zone.tsx), the command palette, the settings rail and, through
 * lib/settings-nav.ts, the settings family pages' tab strips. A nav entry and a settings tab
 * carry the same three gates, so they cannot disagree. Pure TypeScript (no React, no "use client"):
 * the server family-page seam (components/settings/settings-family-page.tsx) uses it too.
 */

/** The capability/role facts an entry's gates are evaluated against. */
export interface NavAccess {
  hasCapability: (cap: string) => boolean;
  isAdmin: boolean;
  isOwner: boolean;
}

/** NavAccess plus the org's enabled modules. */
export interface NavFilterAccess extends NavAccess {
  isModuleEnabled: (moduleId: string) => boolean;
}

/** The gates a NavItem (lib/nav-items.ts) and a SettingsTab (lib/settings-nav.ts) share. */
export interface NavGate {
  /** One capability, or an any-of list. Admins and owners hold every capability. */
  requiredCapability?: string | readonly string[];
  /** Admins and owners only (admin = isAdmin || isOwner, same rule as the settings layout). */
  adminOnly?: boolean;
  /** Shown only when the org has this module enabled. */
  requiredModule?: string;
}

/**
 * The capability gate (any-of when an array) and the admin-only flag, without the module gate.
 * Moved verbatim from lib/nav-items.ts (616E).
 */
export function canAccessNavItem(
  item: Pick<NavGate, "requiredCapability" | "adminOnly">,
  { hasCapability, isAdmin, isOwner }: NavAccess
): boolean {
  const required = item.requiredCapability;
  const capabilityOk =
    !required ||
    (typeof required === "string" ? hasCapability(required) : required.some(hasCapability));
  return capabilityOk && (!item.adminOnly || isAdmin || isOwner);
}

/** Whether an entry is shown: capability + admin-only + module. */
export function isNavEntryVisible(item: NavGate, access: NavFilterAccess): boolean {
  return (
    canAccessNavItem(item, access) &&
    (!item.requiredModule || access.isModuleEnabled(item.requiredModule))
  );
}
