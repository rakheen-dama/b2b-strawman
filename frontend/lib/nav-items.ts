import {
  House,
  ListChecks,
  CalendarDays,
  Scale,
  Plus,
  SearchCheck,
  Users,
  Sparkles,
  Contact,
  ShieldCheck,
  Inbox,
  Hourglass,
  Receipt,
  Layers,
  Banknote,
  Wallet,
  BookOpen,
  Landmark,
  ArrowLeftRight,
  Book,
  CheckCheck,
  Percent,
  PiggyBank,
  FileBarChart,
  UsersRound,
  BarChart3,
  ScrollText,
  Settings,
  Bell,
  type LucideIcon,
} from "lucide-react";
import type { CAPABILITIES } from "@/lib/capabilities";

/** Union of all known capability strings */
type CapabilityName = (typeof CAPABILITIES)[keyof typeof CAPABILITIES];

export interface NavItem {
  label: string;
  href: (slug: string) => string;
  icon: LucideIcon;
  /**
   * Active only on an exact pathname match (no `/`-segment prefix match). Within a zone the single
   * active item is the longest matching href (nav-zone.tsx).
   */
  exact?: boolean;
  /**
   * If set, the item is only shown when the user has this capability. An array is an any-of gate:
   * the item shows when the user has at least one of the listed capabilities.
   */
  requiredCapability?: CapabilityName | readonly CapabilityName[];
  /** If set, sidebar only shows this item when the org has this module enabled */
  requiredModule?: string;
  /** If true, the item is only shown to admins and owners (same rule as a SettingsTab, lib/nav-filter.ts) */
  adminOnly?: boolean;
  /** Optional keywords for command palette fuzzy search */
  keywords?: string[];
  /** If true, the sidebar renders an <a target="_blank"> instead of <Link> */
  external?: boolean;
}

export interface NavGroup {
  id: string;
  label: string;
  items: NavItem[];
  defaultExpanded: boolean;
}

/**
 * The attorney's week in six zones (Phase 87 S6a, ADR-339): Today · Matters · Clients · Fees ·
 * Trust · Firm. One level deep — a zone is the only grouping; ≤ 6 items per zone except Trust (7).
 * Labels are sentence case. AI has no zone: AI intake is "New matter", AI reviews sits in Matters.
 * Firm › Audit trail / Settings point at the Firm settings family (616A, ADR-341).
 * Profitability's single entry is Firm › Reports.
 * Source of truth for the desktop sidebar, the mobile sheet and the command palette.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    id: "today",
    label: "Today",
    defaultExpanded: true,
    items: [
      {
        label: "Home",
        href: (slug) => `/org/${slug}/dashboard`,
        icon: House,
        exact: true,
        keywords: ["dashboard"],
      },
      { label: "My work", href: (slug) => `/org/${slug}/my-work`, icon: ListChecks, exact: true },
      {
        // Phase 87 S6b (613D, ADR-339): one Diary replaces the interim Calendar + Court calendar.
        // Ungated: tasks and matter due dates are served to everyone; court-date / prescription
        // kinds are gated server-side (X-Diary-Types — module court_calendar + VIEW_LEGAL).
        label: "Diary",
        href: (slug) => `/org/${slug}/diary`,
        icon: CalendarDays,
        exact: true,
        keywords: ["calendar", "court calendar", "court dates", "prescriptions", "deadlines"],
      },
    ],
  },
  {
    id: "matters",
    label: "Matters",
    defaultExpanded: true,
    items: [
      { label: "Matters", href: (slug) => `/org/${slug}/matters`, icon: Scale },
      {
        // AI intake lives inside this page and self-gates on AI_EXECUTE (ADR-339). Any-of gate:
        // members hold AI_EXECUTE but not PROJECT_MANAGEMENT and must keep their AI-intake entry
        // (AIVERIFY-011).
        label: "New matter",
        href: (slug) => `/org/${slug}/matters/new`,
        icon: Plus,
        exact: true,
        requiredCapability: ["PROJECT_MANAGEMENT", "AI_EXECUTE"],
      },
      {
        label: "Conflict check",
        href: (slug) => `/org/${slug}/conflict-check`,
        icon: SearchCheck,
        exact: true,
        requiredModule: "conflict_check",
      },
      {
        label: "Adverse parties",
        href: (slug) => `/org/${slug}/legal/adverse-parties`,
        icon: Users,
        exact: true,
        requiredModule: "conflict_check",
      },
      {
        // Gate-review queue — the only way to reach /ai/reviews once the AI zone is gone.
        label: "AI reviews",
        href: (slug) => `/org/${slug}/ai/reviews`,
        icon: Sparkles,
        exact: true,
        requiredCapability: "AI_REVIEW",
      },
    ],
  },
  {
    id: "clients",
    label: "Clients",
    defaultExpanded: true,
    items: [
      {
        label: "Clients",
        href: (slug) => `/org/${slug}/clients`,
        icon: Contact,
        requiredCapability: "CUSTOMER_MANAGEMENT",
      },
      {
        label: "FICA & compliance",
        href: (slug) => `/org/${slug}/compliance`,
        icon: ShieldCheck,
        exact: true,
        requiredCapability: "CUSTOMER_MANAGEMENT",
      },
      {
        // /compliance/requests is the POPIA data-subject requests list (DataRequestsPage), not
        // client information requests — so it is labelled for what it is and has no module gate.
        label: "Data requests",
        href: (slug) => `/org/${slug}/compliance/requests`,
        icon: Inbox,
        requiredCapability: "CUSTOMER_MANAGEMENT",
      },
    ],
  },
  {
    id: "fees",
    label: "Fees",
    defaultExpanded: false,
    items: [
      {
        // Phase 87 612B: per-client unbilled time + expenses (GET /api/invoices/unbilled-summary).
        // Longest-match in nav-zone keeps /invoices/unbilled off the "Fee notes" prefix item.
        label: "Unbilled work",
        href: (slug) => `/org/${slug}/invoices/unbilled`,
        icon: Hourglass,
        exact: true,
        requiredCapability: "INVOICING",
        keywords: ["unbilled", "wip", "work in progress"],
      },
      {
        // Prefix match so /invoices/[id] highlights Fee notes; nav-zone's longest-match keeps
        // /invoices/billing-runs/* and /invoices/collections on their own items.
        label: "Fee notes",
        href: (slug) => `/org/${slug}/invoices`,
        icon: Receipt,
        requiredCapability: "INVOICING",
      },
      {
        label: "Fee runs",
        href: (slug) => `/org/${slug}/invoices/billing-runs`,
        icon: Layers,
        requiredCapability: "INVOICING",
        requiredModule: "bulk_billing",
        keywords: ["billing runs"],
      },
      {
        label: "Debtors",
        href: (slug) => `/org/${slug}/invoices/collections`,
        icon: Banknote,
        exact: true,
        requiredCapability: "INVOICING",
        keywords: ["collections"],
      },
      {
        label: "Disbursements",
        href: (slug) => `/org/${slug}/legal/disbursements`,
        icon: Wallet,
        requiredModule: "disbursements",
      },
      {
        label: "Tariffs",
        href: (slug) => `/org/${slug}/legal/tariffs`,
        icon: BookOpen,
        exact: true,
        requiredModule: "lssa_tariff",
      },
    ],
  },
  {
    id: "trust",
    label: "Trust",
    defaultExpanded: false,
    items: [
      {
        label: "Overview",
        href: (slug) => `/org/${slug}/trust-accounting`,
        icon: Landmark,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
        keywords: ["trust accounting"],
      },
      {
        label: "Transactions",
        href: (slug) => `/org/${slug}/trust-accounting/transactions`,
        icon: ArrowLeftRight,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Client ledgers",
        href: (slug) => `/org/${slug}/trust-accounting/client-ledgers`,
        icon: Book,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Reconciliation",
        href: (slug) => `/org/${slug}/trust-accounting/reconciliation`,
        icon: CheckCheck,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Interest",
        href: (slug) => `/org/${slug}/trust-accounting/interest`,
        icon: Percent,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Investments",
        href: (slug) => `/org/${slug}/trust-accounting/investments`,
        icon: PiggyBank,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Trust reports",
        href: (slug) => `/org/${slug}/trust-accounting/reports`,
        icon: FileBarChart,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
    ],
  },
  {
    id: "firm",
    label: "Firm",
    defaultExpanded: false,
    items: [
      { label: "Team", href: (slug) => `/org/${slug}/team`, icon: UsersRound, exact: true },
      {
        // Single entry for profitability too: /reports/profitability (the standalone
        // /profitability route redirects there — lib/legacy-redirects.ts).
        label: "Reports",
        href: (slug) => `/org/${slug}/reports`,
        icon: BarChart3,
        requiredCapability: "FINANCIAL_VISIBILITY",
      },
      {
        // Phase 87 S9 (616A, ADR-341): the Firm settings family's Audit trail tab.
        label: "Audit trail",
        href: (slug) => `/org/${slug}/settings/firm?tab=audit-trail`,
        icon: ScrollText,
        adminOnly: true,
        keywords: ["audit log"],
      },
      {
        // Phase 87 S9 (616A, ADR-341): the Firm settings family (the settings index target).
        label: "Settings",
        href: (slug) => `/org/${slug}/settings/firm`,
        icon: Settings,
      },
    ],
  },
];

/**
 * Utility items pinned to the sidebar footer (SidebarUtilityFooter — desktop and mobile share it).
 * Notifications only: Settings lives in the Firm zone and nowhere else (ADR-339).
 */
export const UTILITY_ITEMS: NavItem[] = [
  {
    label: "Notifications",
    href: (slug) => `/org/${slug}/notifications`,
    icon: Bell,
    exact: true,
  },
];
