import { LayoutDashboard, ClipboardList, FolderOpen, FileText, Users, UserRound, Bell, TrendingUp, Settings, type LucideIcon } from "lucide-react";

export interface NavItem {
  label: string;
  href: (slug: string) => string;
  icon: LucideIcon;
  /** Use exact match instead of startsWith for active detection */
  exact?: boolean;
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
    label: "Profitability",
    href: (slug) => `/org/${slug}/profitability`,
    icon: TrendingUp,
    exact: true,
  },
  {
    label: "Settings",
    href: (slug) => `/org/${slug}/settings`,
    icon: Settings,
  },
];
