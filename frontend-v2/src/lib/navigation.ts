import {
  Home,
  FolderKanban,
  Users,
  Receipt,
  FileText,
  BarChart3,
  Settings,
  type LucideIcon,
} from "lucide-react";

export interface NavZone {
  id: string;
  icon: LucideIcon;
  label: string;
  matchPrefixes: string[];
  subNav: SubNavItem[];
}

export interface SubNavItem {
  label: string;
  href: (slug: string) => string;
  exact?: boolean;
}

export const NAV_ZONES: NavZone[] = [
  {
    id: "home",
    icon: Home,
    label: "Home",
    matchPrefixes: ["/dashboard", "/my-work"],
    subNav: [
      { label: "Dashboard", href: (s) => `/org/${s}/dashboard`, exact: true },
      { label: "My Work", href: (s) => `/org/${s}/my-work`, exact: true },
    ],
  },
  {
    id: "work",
    icon: FolderKanban,
    label: "Work",
    matchPrefixes: ["/projects", "/schedules"],
    subNav: [
      { label: "Projects", href: (s) => `/org/${s}/projects` },
      { label: "Recurring Schedules", href: (s) => `/org/${s}/schedules` },
    ],
  },
  {
    id: "clients",
    icon: Users,
    label: "Clients",
    matchPrefixes: ["/customers"],
    subNav: [
      { label: "Customers", href: (s) => `/org/${s}/customers` },
    ],
  },
  {
    id: "money",
    icon: Receipt,
    label: "Money",
    matchPrefixes: ["/invoices", "/retainers"],
    subNav: [
      { label: "Invoices", href: (s) => `/org/${s}/invoices` },
      { label: "Retainers", href: (s) => `/org/${s}/retainers` },
    ],
  },
  {
    id: "docs",
    icon: FileText,
    label: "Docs",
    matchPrefixes: ["/documents"],
    subNav: [
      { label: "Documents", href: (s) => `/org/${s}/documents` },
    ],
  },
  {
    id: "reports",
    icon: BarChart3,
    label: "Reports",
    matchPrefixes: ["/profitability", "/reports"],
    subNav: [
      { label: "Profitability", href: (s) => `/org/${s}/profitability` },
      { label: "Reports", href: (s) => `/org/${s}/reports` },
    ],
  },
  {
    id: "admin",
    icon: Settings,
    label: "Admin",
    matchPrefixes: ["/settings", "/team", "/compliance", "/notifications"],
    subNav: [],
  },
];

export function getActiveZone(
  pathname: string,
  slug: string,
): NavZone | undefined {
  const relative = pathname.replace(`/org/${slug}`, "");
  return NAV_ZONES.find((zone) =>
    zone.matchPrefixes.some((prefix) => relative.startsWith(prefix)),
  );
}

export function isSubNavActive(
  item: SubNavItem,
  pathname: string,
  slug: string,
): boolean {
  const href = item.href(slug);
  return item.exact ? pathname === href : pathname.startsWith(href);
}
