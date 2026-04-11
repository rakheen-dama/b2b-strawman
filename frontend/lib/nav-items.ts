import {
  LayoutDashboard,
  ClipboardList,
  CalendarDays,
  FolderOpen,
  FileText,
  Users,
  UserRound,
  Bell,
  TrendingUp,
  BarChart3,
  Receipt,
  CalendarClock,
  ShieldCheck,
  Settings,
  Scale,
  Gavel,
  ShieldAlert,
  FileSpreadsheet,
  BookOpen,
  ArrowLeftRight,
  Calculator,
  Landmark,
  PiggyBank,
  FileBarChart,
  Layers,
  type LucideIcon,
} from "lucide-react";
import type { CAPABILITIES } from "@/lib/capabilities";
import { docsLink } from "@/lib/docs";

/** Union of all known capability strings */
type CapabilityName = (typeof CAPABILITIES)[keyof typeof CAPABILITIES];

export interface NavItem {
  label: string;
  href: (slug: string) => string;
  icon: LucideIcon;
  /** Use exact match instead of startsWith for active detection */
  exact?: boolean;
  /** If set, sidebar only shows this item when the user has this capability */
  requiredCapability?: CapabilityName;
  /** If set, sidebar only shows this item when the org has this module enabled */
  requiredModule?: string;
  /** Optional keywords for future command palette fuzzy search */
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

export interface SettingsItem {
  title: string;
  description: string;
  href: (slug: string) => string;
  adminOnly?: boolean;
  comingSoon?: boolean;
  /** If set, settings item only shows when the org has this module enabled */
  requiredModule?: string;
}

/**
 * Grouped navigation items organized into 5 logical zones.
 * This is the source of truth for sidebar navigation structure.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    id: "work",
    label: "Work",
    defaultExpanded: true,
    items: [
      {
        label: "Dashboard",
        href: (slug) => `/org/${slug}/dashboard`,
        icon: LayoutDashboard,
        exact: true,
      },
      {
        label: "My Work",
        href: (slug) => `/org/${slug}/my-work`,
        icon: ClipboardList,
        exact: true,
      },
      {
        label: "Calendar",
        href: (slug) => `/org/${slug}/calendar`,
        icon: CalendarDays,
        exact: true,
      },
      {
        label: "Court Calendar",
        href: (slug) => `/org/${slug}/court-calendar`,
        icon: Gavel,
        exact: true,
        requiredModule: "court_calendar",
      },
    ],
  },
  {
    id: "projects",
    label: "Projects",
    defaultExpanded: true,
    items: [
      {
        label: "Projects",
        href: (slug) => `/org/${slug}/projects`,
        icon: FolderOpen,
      },
      {
        label: "Recurring Schedules",
        href: (slug) => `/org/${slug}/schedules`,
        icon: CalendarClock,
        requiredCapability: "PROJECT_MANAGEMENT",
      },
    ],
  },
  {
    id: "clients",
    label: "Clients",
    defaultExpanded: false,
    items: [
      {
        label: "Customers",
        href: (slug) => `/org/${slug}/customers`,
        icon: UserRound,
        requiredCapability: "CUSTOMER_MANAGEMENT",
      },
      {
        label: "Proposals",
        href: (slug) => `/org/${slug}/proposals`,
        icon: FileText,
        requiredCapability: "INVOICING",
      },
      {
        label: "Retainers",
        href: (slug) => `/org/${slug}/retainers`,
        icon: FileText,
        requiredCapability: "INVOICING",
      },
      {
        label: "Compliance",
        href: (slug) => `/org/${slug}/compliance`,
        icon: ShieldCheck,
        requiredCapability: "CUSTOMER_MANAGEMENT",
      },
      {
        label: "Deadlines",
        href: (slug) => `/org/${slug}/deadlines`,
        icon: CalendarClock,
        exact: true,
        requiredCapability: "CUSTOMER_MANAGEMENT",
        requiredModule: "regulatory_deadlines",
      },
      {
        label: "Conflict Check",
        href: (slug) => `/org/${slug}/conflict-check`,
        icon: ShieldAlert,
        exact: true,
        requiredModule: "conflict_check",
      },
      {
        label: "Adverse Parties",
        href: (slug) => `/org/${slug}/legal/adverse-parties`,
        icon: Scale,
        exact: true,
        requiredModule: "conflict_check",
      },
    ],
  },
  {
    id: "finance",
    label: "Finance",
    defaultExpanded: false,
    items: [
      {
        label: "Invoices",
        href: (slug) => `/org/${slug}/invoices`,
        icon: Receipt,
        requiredCapability: "INVOICING",
      },
      {
        label: "Billing Runs",
        href: (slug) => `/org/${slug}/invoices/billing-runs`,
        icon: Layers,
        requiredCapability: "INVOICING",
        requiredModule: "bulk_billing",
      },
      {
        label: "Profitability",
        href: (slug) => `/org/${slug}/profitability`,
        icon: TrendingUp,
        exact: true,
        requiredCapability: "FINANCIAL_VISIBILITY",
      },
      {
        label: "Reports",
        href: (slug) => `/org/${slug}/reports`,
        icon: BarChart3,
        exact: true,
        requiredCapability: "FINANCIAL_VISIBILITY",
      },
      {
        label: "Trust Accounting",
        href: (slug) => `/org/${slug}/trust-accounting`,
        icon: Scale,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Transactions",
        href: (slug) => `/org/${slug}/trust-accounting/transactions`,
        icon: ArrowLeftRight,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Client Ledgers",
        href: (slug) => `/org/${slug}/trust-accounting/client-ledgers`,
        icon: BookOpen,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Reconciliation",
        href: (slug) => `/org/${slug}/trust-accounting/reconciliation`,
        icon: Calculator,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Interest",
        href: (slug) => `/org/${slug}/trust-accounting/interest`,
        icon: Landmark,
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
        label: "Trust Reports",
        href: (slug) => `/org/${slug}/trust-accounting/reports`,
        icon: FileBarChart,
        exact: true,
        requiredCapability: "VIEW_TRUST",
        requiredModule: "trust_accounting",
      },
      {
        label: "Tariffs",
        href: (slug) => `/org/${slug}/legal/tariffs`,
        icon: FileSpreadsheet,
        exact: true,
        requiredModule: "lssa_tariff",
      },
    ],
  },
  {
    id: "team",
    label: "Team",
    defaultExpanded: true,
    items: [
      {
        label: "Team",
        href: (slug) => `/org/${slug}/team`,
        icon: Users,
        exact: true,
      },
      {
        label: "Resources",
        href: (slug) => `/org/${slug}/resources`,
        icon: Users,
        exact: true,
        requiredCapability: "RESOURCE_PLANNING",
        requiredModule: "resource_planning",
      },
      {
        label: "Utilization",
        href: (slug) => `/org/${slug}/resources/utilization`,
        icon: TrendingUp,
        exact: true,
        requiredCapability: "RESOURCE_PLANNING",
        requiredModule: "resource_planning",
      },
    ],
  },
];

/**
 * Utility items pinned to the sidebar footer.
 * Not part of any NavGroup — always visible.
 */
export const UTILITY_ITEMS: NavItem[] = [
  {
    label: "Notifications",
    href: (slug) => `/org/${slug}/notifications`,
    icon: Bell,
    exact: true,
  },
  {
    label: "Settings",
    href: (slug) => `/org/${slug}/settings/general`,
    icon: Settings,
  },
  {
    label: "Help",
    href: () => docsLink(""),
    icon: BookOpen,
    external: true,
  },
];

/**
 * Settings page items for command palette indexing.
 * Mirrors the settingsCards array from the settings page.
 */
export const SETTINGS_ITEMS: SettingsItem[] = [
  {
    title: "Billing",
    description: "Manage your subscription and view usage",
    href: (slug) => `/org/${slug}/settings/billing`,
  },
  {
    title: "Notifications",
    description: "Configure notification preferences",
    href: (slug) => `/org/${slug}/settings/notifications`,
  },
  {
    title: "Rates & Currency",
    description: "Manage billing rates, cost rates, and default currency",
    href: (slug) => `/org/${slug}/settings/rates`,
  },
  {
    title: "Tax",
    description: "Configure tax registration, labels, and inclusive pricing",
    href: (slug) => `/org/${slug}/settings/tax`,
  },
  {
    title: "Time Tracking",
    description: "Configure time reminders and default expense markup",
    href: (slug) => `/org/${slug}/settings/time-tracking`,
  },
  {
    title: "Custom Fields",
    description:
      "Define custom fields and groups for projects, tasks, and customers",
    href: (slug) => `/org/${slug}/settings/custom-fields`,
  },
  {
    title: "Tags",
    description: "Manage org-wide tags for projects, tasks, and customers",
    href: (slug) => `/org/${slug}/settings/tags`,
  },
  {
    title: "Templates",
    description: "Manage document templates and branding",
    href: (slug) => `/org/${slug}/settings/templates`,
  },
  {
    title: "Clauses",
    description: "Manage reusable clause library for document generation",
    href: (slug) => `/org/${slug}/settings/clauses`,
  },
  {
    title: "Checklists",
    description: "Manage checklist templates for customer onboarding",
    href: (slug) => `/org/${slug}/settings/checklists`,
  },
  {
    title: "Document Acceptance",
    description:
      "Configure default expiry period for document acceptance requests",
    href: (slug) => `/org/${slug}/settings/acceptance`,
  },
  {
    title: "Compliance",
    description:
      "Configure retention policies, dormancy thresholds, and data requests",
    href: (slug) => `/org/${slug}/settings/compliance`,
  },
  {
    title: "Project Templates",
    description: "Create and manage reusable project blueprints",
    href: (slug) => `/org/${slug}/settings/project-templates`,
  },
  {
    title: "Project Naming",
    description: "Configure auto-naming patterns for new projects",
    href: (slug) => `/org/${slug}/settings/project-naming`,
  },
  {
    title: "Request Templates",
    description:
      "Create and manage reusable information request templates",
    href: (slug) => `/org/${slug}/settings/request-templates`,
  },
  {
    title: "Request Settings",
    description:
      "Configure default reminder interval for information requests",
    href: (slug) => `/org/${slug}/settings/request-settings`,
  },
  {
    title: "Batch Billing",
    description:
      "Configure async thresholds, email rate limits, and default billing currency",
    href: (slug) => `/org/${slug}/settings/batch-billing`,
    adminOnly: true,
    requiredModule: "bulk_billing",
  },
  {
    title: "Capacity",
    description: "Set default weekly capacity hours for team members",
    href: (slug) => `/org/${slug}/settings/capacity`,
  },
  {
    title: "Email",
    description: "View email delivery logs, stats, and rate limits",
    href: (slug) => `/org/${slug}/settings/email`,
    adminOnly: true,
  },
  {
    title: "Automations",
    description:
      "Create rules to automate tasks, notifications, and workflows",
    href: (slug) => `/org/${slug}/settings/automations`,
    adminOnly: true,
    requiredModule: "automation_builder",
  },
  {
    title: "Roles & Permissions",
    description: "Define custom roles and manage team permissions.",
    href: (slug) => `/org/${slug}/settings/roles`,
    adminOnly: true,
  },
  {
    title: "Organization",
    description: "Update org name, logo, and details",
    href: (slug) => `/org/${slug}/settings/general`,
  },
  {
    title: "Security",
    description: "Configure authentication and access policies",
    href: () => "#",
    comingSoon: true,
  },
  {
    title: "Integrations",
    description: "Connect third-party tools and services",
    href: (slug) => `/org/${slug}/settings/integrations`,
  },
  {
    title: "Trust Accounting",
    description: "Manage trust accounts, approval settings, and LPFF rates",
    href: (slug) => `/org/${slug}/settings/trust-accounting`,
    adminOnly: true,
    requiredModule: "trust_accounting",
  },
  {
    title: "Features",
    description: "Enable additional features for your organization",
    href: (slug) => `/org/${slug}/settings/features`,
    adminOnly: true,
  },
];

/**
 * Backward-compatible flat array derived from NAV_GROUPS + UTILITY_ITEMS.
 * Used by MobileSidebar which iterates over this array
 * to render all sidebar items including Notifications and Settings.
 */
export const NAV_ITEMS: NavItem[] = [
  ...NAV_GROUPS.flatMap((g) => g.items),
  ...UTILITY_ITEMS,
];
