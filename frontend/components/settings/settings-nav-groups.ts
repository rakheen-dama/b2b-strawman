const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const API_BASE =
  AUTH_MODE === "keycloak" ? "" : process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

export interface SettingsNavItem {
  label: string;
  href: string;
  adminOnly?: boolean;
  comingSoon?: boolean;
  /** If set, settings item only shows when the org has this module enabled */
  requiredModule?: string;
  /** If set, a pending-count badge will be rendered next to the label */
  pendingCountEndpoint?: string;
}

export interface SettingsNavGroup {
  id: string;
  label: string;
  items: SettingsNavItem[];
}

export const SETTINGS_NAV_GROUPS: SettingsNavGroup[] = [
  {
    id: "general",
    label: "General",
    items: [
      { label: "General", href: "general" },
      { label: "Billing", href: "billing" },
      { label: "Notifications", href: "notifications" },
      { label: "Email", href: "email", adminOnly: true },
      { label: "Security", href: "", comingSoon: true },
    ],
  },
  {
    id: "work",
    label: "Work",
    items: [
      { label: "Time Tracking", href: "time-tracking" },
      { label: "Project Templates", href: "project-templates" },
      { label: "Project Naming", href: "project-naming" },
      {
        label: "Automations",
        href: "automations",
        adminOnly: true,
        requiredModule: "automation_builder",
      },
      {
        label: "AI Review Queue",
        href: "automations/ai-queue",
        adminOnly: true,
        requiredModule: "automation_builder",
        pendingCountEndpoint: `${API_BASE}/api/assistant/invocations?status=PENDING_APPROVAL&size=0`,
      },
    ],
  },
  {
    id: "documents",
    label: "Documents",
    items: [
      { label: "Templates", href: "templates" },
      { label: "Clauses", href: "clauses" },
      { label: "Checklists", href: "checklists" },
      { label: "Document Acceptance", href: "acceptance" },
    ],
  },
  {
    id: "finance",
    label: "Finance",
    items: [
      { label: "Rates & Currency", href: "rates" },
      { label: "Tax", href: "tax" },
      {
        label: "Batch Billing",
        href: "batch-billing",
        adminOnly: true,
        requiredModule: "bulk_billing",
      },
      { label: "Capacity", href: "capacity" },
      { label: "Collections", href: "collections", adminOnly: true },
      {
        label: "Trust Accounting",
        href: "trust-accounting",
        adminOnly: true,
        requiredModule: "trust_accounting",
      },
    ],
  },
  {
    id: "clients",
    label: "Clients",
    items: [
      { label: "Pipeline", href: "pipeline", adminOnly: true },
      { label: "Custom Fields", href: "custom-fields" },
      { label: "Tags", href: "tags" },
      { label: "Request Templates", href: "request-templates" },
      { label: "Request Settings", href: "request-settings" },
      { label: "Compliance", href: "compliance" },
      { label: "Data Protection", href: "data-protection", adminOnly: true },
    ],
  },
  {
    id: "features",
    label: "Features",
    items: [
      { label: "Features", href: "features", adminOnly: true },
      { label: "Packs", href: "packs", adminOnly: true },
    ],
  },
  {
    id: "access",
    label: "Access & Integrations",
    items: [
      { label: "Roles & Permissions", href: "roles", adminOnly: true },
      { label: "Audit log", href: "audit-log", adminOnly: true },
      { label: "Integrations", href: "integrations" },
      { label: "AI Configuration", href: "ai", adminOnly: true },
    ],
  },
];
