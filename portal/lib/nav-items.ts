import {
  Home,
  FolderOpen,
  Scale,
  CalendarClock,
  Receipt,
  MessageSquare,
  Activity,
  type LucideIcon,
} from "lucide-react";

export interface PortalNavItem {
  id: string;
  href: string;
  labelKey: string;
  icon: LucideIcon;
  /**
   * If set, every module in this array must be present in `enabledModules` for the
   * item to be shown. If unset/empty, the item does not require any module.
   */
  modules?: string[];
}

// Phase 87 S10 (617A, arch §12.8): legal labels; Matters lives at /matters (the /projects
// segment redirects permanently, next.config.ts). The `acceptance` item is dropped — there is no
// /acceptance page; each pending engagement letter links to its own /accept/{token}. The
// `profiles: ["legal-za"]` gate is gone (legal-za is the only profile); module gates stay.
export const PORTAL_NAV_ITEMS: PortalNavItem[] = [
  { id: "home", href: "/home", labelKey: "portal.nav.home", icon: Home },
  {
    id: "matters",
    href: "/matters",
    labelKey: "portal.nav.matters",
    icon: FolderOpen,
  },
  {
    id: "trust",
    href: "/trust",
    labelKey: "portal.nav.trust",
    icon: Scale,
    modules: ["trust_accounting"],
  },
  {
    // Path kept: `deadline` is not one of the two nouns (backend MODULE_ID = "deadlines").
    id: "deadlines",
    href: "/deadlines",
    labelKey: "portal.nav.deadlines",
    icon: CalendarClock,
    modules: ["deadlines"],
  },
  {
    id: "invoices",
    href: "/invoices",
    labelKey: "portal.nav.invoices",
    icon: Receipt,
  },
  {
    id: "requests",
    href: "/requests",
    labelKey: "portal.nav.requests",
    icon: MessageSquare,
    modules: ["information_requests"],
  },
  {
    id: "activity",
    href: "/activity",
    labelKey: "portal.nav.activity",
    icon: Activity,
  },
  // GAP-P-07: "Documents" entry removed — the /documents route is not yet
  // implemented. Re-introduce this nav item only once the corresponding
  // page is available, ideally behind a feature flag / enabled module.
];

/**
 * Human-readable label map. This is a placeholder until the portal gets a
 * real i18n layer — callers should look up `PORTAL_NAV_LABELS[item.labelKey]`
 * and fall back to the key itself if missing.
 */
export const PORTAL_NAV_LABELS: Record<string, string> = {
  "portal.nav.home": "Home",
  "portal.nav.matters": "Matters",
  "portal.nav.trust": "Trust statement",
  "portal.nav.deadlines": "Diary dates",
  "portal.nav.invoices": "Fee notes",
  "portal.nav.requests": "Documents & requests",
  "portal.nav.activity": "Activity",
};

/**
 * Pure filter: returns the subset of `items` that should be visible given the
 * enabled modules. If an item declares `modules`, every entry in that list must
 * appear in `ctx.enabledModules`; items without a module gate are always visible.
 */
export function filterNavItems(
  items: PortalNavItem[],
  ctx: { enabledModules: string[] },
): PortalNavItem[] {
  return items.filter(
    (item) =>
      !item.modules ||
      item.modules.length === 0 ||
      item.modules.every((m) => ctx.enabledModules.includes(m)),
  );
}
