import { LayoutDashboard, ClipboardList, CalendarDays, FolderOpen, FileText, Users, UserRound, Bell, TrendingUp, BarChart3, Receipt, CalendarClock, ShieldCheck, Settings, type LucideIcon } from "lucide-react";

export interface NavItem {
  label: string;
  href: (slug: string) => string;
  icon: LucideIcon;
  /** Use exact match instead of startsWith for active detection */
  exact?: boolean;
  /** If set, sidebar only shows this item when the user has this capability */
  requiredCapability?: string;
}

export const NAV_ITEMS: NavItem[] = [
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
    label: "Projects",
    href: (slug) => `/org/${slug}/projects`,
    icon: FolderOpen,
  },
  {
    label: "Documents",
    href: (slug) => `/org/${slug}/documents`,
    icon: FileText,
    exact: true,
  },
  {
    label: "Customers",
    href: (slug) => `/org/${slug}/customers`,
    icon: UserRound,
    requiredCapability: "CUSTOMER_MANAGEMENT",
  },
  {
    label: "Team",
    href: (slug) => `/org/${slug}/team`,
    icon: Users,
    exact: true,
  },
  {
    label: "Notifications",
    href: (slug) => `/org/${slug}/notifications`,
    icon: Bell,
    exact: true,
  },
  {
    label: "Resources",
    href: (slug) => `/org/${slug}/resources`,
    icon: Users,
    exact: true,
    requiredCapability: "RESOURCE_PLANNING",
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
    label: "Invoices",
    href: (slug) => `/org/${slug}/invoices`,
    icon: Receipt,
    requiredCapability: "INVOICING",
  },
  {
    label: "Recurring Schedules",
    href: (slug) => `/org/${slug}/schedules`,
    icon: CalendarClock,
    requiredCapability: "PROJECT_MANAGEMENT",
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
    label: "Settings",
    href: (slug) => `/org/${slug}/settings`,
    icon: Settings,
  },
];
