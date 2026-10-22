import { Banknote, Building2, FileText, Plug, Scale, type LucideIcon } from "lucide-react";
import { isNavEntryVisible, type NavFilterAccess } from "@/lib/nav-filter";

/**
 * One settings navigation source (Phase 87 S9, ADR-341, architecture §12.7.3): five families,
 * 31 tabs addressed by `?tab=<id>`. It is the only source: the family pages' tab strips, the
 * settings rail (components/settings/settings-sidebar.tsx) and the command palette all read it, and
 * all gate through the one rule in lib/nav-filter.ts, so a tab cannot exist in one and not the
 * others. Every tab's `adminOnly` / `requiredModule` / `requiredCapability` is pinned by a gate
 * table in lib/__tests__/settings-nav.test.ts, with the orchestrator-ruled differences from the old
 * settings nav marked below. `Security` (comingSoon, no page) is dropped.
 */

export type SettingsFamilyId = "firm" | "matters" | "documents" | "fees" | "connections";

export interface SettingsTab {
  /** `?tab=` value — stable; used by redirects, the palette and Playwright specs. */
  id: string;
  /** Sentence case. */
  label: string;
  /** Command-palette subtitle. */
  description?: string;
  adminOnly?: boolean;
  /** e.g. "trust_accounting", "bulk_billing". */
  requiredModule?: string;
  /** Admins and owners hold every capability (same rule as CapabilityProvider.hasCapability). */
  requiredCapability?: string;
  keywords?: readonly string[];
}

export interface SettingsFamily {
  id: SettingsFamilyId;
  label: string;
  href: (slug: string) => string;
  icon: LucideIcon;
  tabs: readonly SettingsTab[];
}

const familyHref = (id: SettingsFamilyId) => (slug: string) => `/org/${slug}/settings/${id}`;

// `as const` keeps every tab id a literal type, so SettingsTabId<F> is the exact id union a family
// page's loader map must cover (components/settings/settings-family-page.tsx).
const FAMILIES = [
  {
    id: "firm",
    label: "Firm",
    href: familyHref("firm"),
    icon: Building2,
    tabs: [
      {
        id: "general",
        label: "General",
        description: "Update firm name, logo, currency and branding",
        keywords: ["organization", "branding", "currency", "logo"],
      },
      {
        id: "roles",
        label: "Roles",
        description: "Define custom roles and manage team permissions",
        adminOnly: true,
        keywords: ["permissions", "roles & permissions"],
      },
      {
        id: "audit-trail",
        label: "Audit trail",
        description: "Inspect a paginated feed of audit events for the firm",
        // Ruled exception (D5): old nav `adminOnly`. The backend gate is TEAM_OVERSIGHT
        // (AuditEventController) and the old page had no frontend role check, so a member holding
        // TEAM_OVERSIGHT — who sees the Home "Sensitive events" widget linking here — keeps access.
        requiredCapability: "TEAM_OVERSIGHT",
        keywords: ["audit log"],
      },
      {
        id: "data-protection",
        label: "Data protection",
        description:
          "Jurisdiction, information officer, retention, processing register and PAIA manual",
        adminOnly: true,
        keywords: ["popia", "privacy", "paia"],
      },
      {
        id: "data-requests",
        label: "Data requests",
        description: "Track and manage data subject access requests",
        // D7 — no old nav entry; mirrors the former page gate (isAdmin || isOwner).
        adminOnly: true,
        keywords: ["dsar", "data subject"],
      },
      {
        id: "compliance",
        label: "Compliance",
        description:
          "Configure retention policies, dormancy thresholds, and data request deadlines",
        // Ruled exception (D6): old nav had no flag, but the former page denied non-admins — a
        // member-visible tab holding only a denial message is the "disabled tab" §12.7.4 forbids.
        adminOnly: true,
        keywords: ["retention", "dormancy", "compliance packs"],
      },
      {
        id: "features",
        label: "Features",
        description: "Enable additional features for your firm",
        adminOnly: true,
        keywords: ["modules"],
      },
      {
        id: "subscription",
        label: "Subscription",
        description: "View your billing method and status",
        // D6 — old nav: no flag (one-to-one); GET /api/billing/subscription is ungated (ADR-335).
        keywords: ["billing"],
      },
    ],
  },
  {
    id: "matters",
    label: "Matters",
    href: familyHref("matters"),
    icon: Scale,
    tabs: [
      {
        id: "templates",
        label: "Templates",
        description: "Create and manage reusable matter templates",
        keywords: ["matter templates"],
      },
      {
        id: "naming",
        label: "Naming",
        description: "Configure auto-naming patterns for new matters",
        // Ruled exception (D18, 616B — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
        keywords: ["matter naming"],
      },
      {
        id: "custom-fields",
        label: "Custom fields",
        description: "Define custom fields and groups for matters, tasks, and clients",
        // Ruled exception (D18, 616B — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
      },
      {
        id: "tags",
        label: "Tags",
        description: "Manage firm-wide tags for matters, tasks, and clients",
      },
      {
        id: "checklists",
        label: "Checklists",
        description: "Manage checklist templates for client onboarding",
        // Ruled exception (D18, 616B — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
      },
      {
        id: "request-templates",
        label: "Request templates",
        description: "Create and manage reusable information request templates",
        // Ruled exception (D18, 616B — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
      },
      {
        id: "request-settings",
        label: "Request settings",
        description: "Configure the default reminder interval for information requests",
        // Ruled exception (D18, 616B — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
      },
    ],
  },
  {
    id: "documents",
    label: "Documents",
    href: familyHref("documents"),
    icon: FileText,
    tabs: [
      {
        id: "templates",
        label: "Templates",
        description: "Manage document templates and branding",
      },
      {
        id: "clauses",
        label: "Clauses",
        description: "Manage the reusable clause library for document generation",
      },
      {
        id: "acceptance",
        label: "Acceptance",
        description: "Configure the default expiry period for document acceptance requests",
        keywords: ["document acceptance", "engagement letter"],
        // Ruled exception (D19, 616C — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
      },
      {
        id: "email",
        label: "Email",
        description: "View email delivery logs, stats, and rate limits",
        adminOnly: true,
      },
    ],
  },
  {
    id: "fees",
    label: "Fees & Trust",
    href: familyHref("fees"),
    icon: Banknote,
    tabs: [
      {
        id: "tariffs",
        label: "Tariffs",
        description: "Manage billing rates, cost rates, and default currency",
        keywords: ["rates", "rates & currency"],
        // Ungated by ruling (D20, 616C): the family's first tab must stay ungated; the loader keeps
        // the former page's in-page FINANCIAL_VISIBILITY denial.
      },
      {
        id: "tax",
        label: "Tax",
        description: "Configure tax registration, labels, and inclusive pricing",
        keywords: ["vat"],
        // Ruled exception (D19, 616C — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
      },
      {
        id: "time-recording",
        label: "Time recording",
        description: "Configure time reminders and the default markup",
        keywords: ["time tracking"],
        // Ruled exception (D19, 616C — D6 rule): old nav had no flag, but the former page denied
        // non-admins; a member-visible tab holding only a denial message is the "disabled tab"
        // §12.7.4 forbids.
        adminOnly: true,
      },
      {
        id: "fee-runs",
        label: "Fee runs",
        description:
          "Configure async thresholds, email rate limits, and the default billing currency",
        adminOnly: true,
        requiredModule: "bulk_billing",
        keywords: ["batch billing"],
      },
      {
        id: "collections",
        label: "Collections",
        description: "Configure overdue fee-note reminder stages and escalation",
        adminOnly: true,
        keywords: ["debtors"],
      },
      {
        id: "trust-accounting",
        label: "Trust accounting",
        description: "Manage trust accounts, approval settings, and LPFF rates",
        adminOnly: true,
        requiredModule: "trust_accounting",
      },
    ],
  },
  {
    id: "connections",
    label: "Connections",
    href: familyHref("connections"),
    icon: Plug,
    tabs: [
      {
        id: "xero",
        label: "Xero",
        description: "Connect Xero and view its sync status",
        keywords: ["integrations", "accounting"],
      },
      {
        id: "mcp",
        label: "MCP",
        description: "Connect your firm's own Claude to Kazi over MCP",
        // D7 — no old nav entry; mirrors the integrations/mcp page gate (admin or INTEGRATION_MANAGE).
        requiredCapability: "INTEGRATION_MANAGE",
        keywords: ["integrations", "claude"],
      },
      {
        id: "ai",
        label: "AI",
        description: "Configure AI profile, model preference, and usage budget",
        adminOnly: true,
        keywords: ["ai configuration"],
      },
      {
        id: "notifications",
        label: "Notifications",
        description: "Configure notification preferences",
      },
      {
        id: "packs",
        label: "Packs",
        description: "Install and manage practice packs",
        adminOnly: true,
      },
      {
        id: "automations",
        label: "Automations",
        description: "Enable or disable the automation rules installed with your practice packs",
        adminOnly: true,
      },
    ],
  },
] as const satisfies readonly SettingsFamily[];

export const SETTINGS_FAMILIES: readonly SettingsFamily[] = FAMILIES;

/** The literal `?tab=` id union of one family, e.g. SettingsTabId<"firm"> = "general" | "roles" | … */
export type SettingsTabId<F extends SettingsFamilyId> = Extract<
  (typeof FAMILIES)[number],
  { id: F }
>["tabs"][number]["id"];

export function getSettingsFamily(id: SettingsFamilyId): SettingsFamily {
  const family = SETTINGS_FAMILIES.find((f) => f.id === id);
  if (!family) throw new Error(`Unknown settings family: ${id}`);
  return family;
}

/** `/org/{slug}/settings/{family}` plus `?tab={tab}` when a tab is given. */
export function settingsHref(slug: string, family: SettingsFamilyId, tab?: string): string {
  const base = `/org/${slug}/settings/${family}`;
  return tab ? `${base}?tab=${encodeURIComponent(tab)}` : base;
}

/** NavAccess (capability/role facts) plus the org's enabled modules (lib/nav-filter.ts). */
export type SettingsAccess = NavFilterAccess;

/** Build SettingsAccess on the server from GET /api/me/capabilities + the org's enabled modules. */
export function settingsAccessFrom(
  caps: { capabilities: string[]; isAdmin: boolean; isOwner: boolean },
  enabledModules: readonly string[]
): SettingsAccess {
  const admin = caps.isAdmin || caps.isOwner;
  const capSet = new Set(caps.capabilities);
  const moduleSet = new Set(enabledModules);
  return {
    isAdmin: caps.isAdmin,
    isOwner: caps.isOwner,
    hasCapability: (cap) => admin || capSet.has(cap),
    isModuleEnabled: (moduleId) => moduleSet.has(moduleId),
  };
}

/**
 * The one nav access rule (lib/nav-filter.ts): capability + admin-only (admin = isAdmin || isOwner)
 * + module — the same rule the sidebar zones and the command palette apply.
 */
export function canAccessSettingsTab(tab: SettingsTab, access: SettingsAccess): boolean {
  return isNavEntryVisible(tab, access);
}

export function visibleSettingsTabs(family: SettingsFamily, access: SettingsAccess): SettingsTab[] {
  return family.tabs.filter((tab) => canAccessSettingsTab(tab, access));
}

/**
 * Resolve `?tab=` (§12.7.4): a permitted id wins; a missing, unknown or forbidden id falls back to
 * the family's first permitted tab — never a 404. `forbidden` is the known-but-not-permitted tab
 * the user asked for (the page shows a one-line notice), `null` otherwise.
 * Every family's first tab is ungated (pinned by settings-nav.test.ts), so a fallback always exists.
 */
export function resolveSettingsTab(
  family: SettingsFamily,
  requested: string | undefined,
  access: SettingsAccess
): { tab: SettingsTab; forbidden: SettingsTab | null } {
  const visible = visibleSettingsTabs(family, access);
  const match = visible.find((t) => t.id === requested);
  if (match) return { tab: match, forbidden: null };
  const forbidden = requested ? (family.tabs.find((t) => t.id === requested) ?? null) : null;
  return { tab: visible[0], forbidden };
}
