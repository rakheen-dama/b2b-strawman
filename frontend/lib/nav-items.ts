import { LayoutDashboard, FolderOpen, FileText, Users, UserRound, Settings, type LucideIcon } from "lucide-react";

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
    label: "Settings",
    href: (slug) => `/org/${slug}/settings`,
    icon: Settings,
  },
];
