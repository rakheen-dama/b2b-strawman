import {
  Home,
  FolderOpen,
  Scale,
  FileText,
  CalendarClock,
  Receipt,
  ClipboardList,
  MessageSquare,
  FileCheck,
  type LucideIcon,
} from "lucide-react";

export interface PortalNavItem {
  id: string;
  href: string;
  labelKey: string;
  icon: LucideIcon;
  /**
   * If set, the item is only shown when `tenantProfile` is one of the listed values.
   * If unset/empty, the item is profile-agnostic.
   */
  profiles?: string[];
  /**
   * If set, every module in this array must be present in `enabledModules` for the
   * item to be shown. If unset/empty, the item does not require any module.
   */
  modules?: string[];
}

export const PORTAL_NAV_ITEMS: PortalNavItem[] = [
  { id: "home", href: "/home", labelKey: "portal.nav.home", icon: Home },
  {
    id: "projects",
    href: "/projects",
    labelKey: "portal.nav.matters",
    icon: FolderOpen,
  },
  {
    id: "trust",
    href: "/trust",
    labelKey: "portal.nav.trust",
    icon: Scale,
    profiles: ["legal-za"],
    modules: ["trust_accounting"],
  },
  {
    id: "retainer",
    href: "/retainer",
    labelKey: "portal.nav.retainer",
    icon: FileText,
    profiles: ["legal-za", "consulting-za"],
    modules: ["retainer_agreements"],
  },
  {
    id: "deadlines",
    href: "/deadlines",
    labelKey: "portal.nav.deadlines",
    icon: CalendarClock,
    profiles: ["accounting-za", "legal-za"],
    modules: ["deadlines"],
  },
  {
    id: "invoices",
    href: "/invoices",
    labelKey: "portal.nav.invoices",
    icon: Receipt,
  },
  {
    id: "proposals",
    href: "/proposals",
    labelKey: "portal.nav.proposals",
    icon: ClipboardList,
  },
  {
    id: "requests",
    href: "/requests",
    labelKey: "portal.nav.requests",
    icon: MessageSquare,
    modules: ["information_requests"],
  },
  {
    id: "acceptance",
    href: "/acceptance",
    labelKey: "portal.nav.acceptance",
    icon: FileCheck,
    modules: ["document_acceptance"],
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
  "portal.nav.trust": "Trust",
  "portal.nav.retainer": "Retainer",
  "portal.nav.deadlines": "Deadlines",
  "portal.nav.invoices": "Invoices",
  "portal.nav.proposals": "Proposals",
  "portal.nav.requests": "Requests",
  "portal.nav.acceptance": "Acceptance",
};

/**
 * Pure filter: returns the subset of `items` that should be visible given the
 * current tenant profile and enabled modules.
 *
 * - If an item declares `profiles`, the ctx `tenantProfile` must be defined and
 *   match one of them.
 * - If an item declares `modules`, every entry in that list must also appear in
 *   `ctx.enabledModules`.
 * - Items without any gates are always visible.
 */
export function filterNavItems(
  items: PortalNavItem[],
  ctx: { tenantProfile?: string; enabledModules: string[] },
): PortalNavItem[] {
  return items.filter((item) => {
    if (item.profiles && item.profiles.length > 0) {
      if (!ctx.tenantProfile || !item.profiles.includes(ctx.tenantProfile)) {
        return false;
      }
    }
    if (item.modules && item.modules.length > 0) {
      if (!item.modules.every((m) => ctx.enabledModules.includes(m))) {
        return false;
      }
    }
    return true;
  });
}
