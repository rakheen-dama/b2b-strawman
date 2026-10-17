/**
 * Vertical-terminology system — the single source of truth shared by the staff
 * app (`frontend`) and the customer `portal`.
 *
 * Before Wave 2.5 each app shipped its own copy of this map and they had
 * drifted; the reconciliation established one BASE map shared by both apps.
 * Phase 86 (ADR-332) reduced it to the single `legal-za` profile; the
 * mechanism (`TerminologyProvider`, `useTerminology`, `t()`) stays for Phase 87.
 *
 * The BASE map is the staff-app superset. Keys that only the staff app reads
 * (project placeholders, the audit-tab label) live here harmlessly: the portal
 * never calls `t()` with those keys, so carrying them costs nothing and keeps
 * one map to maintain.
 */
export const TERMINOLOGY_BASE: Record<string, Record<string, string>> = {
  "legal-za": {
    Project: "Matter",
    Projects: "Matters",
    project: "matter",
    projects: "matters",
    Task: "Action Item",
    Tasks: "Action Items",
    task: "action item",
    tasks: "action items",
    Customer: "Client",
    Customers: "Clients",
    customer: "client",
    customers: "clients",
    Proposal: "Engagement Letter",
    Proposals: "Engagement Letters",
    proposal: "engagement letter",
    proposals: "engagement letters",
    "Time Entry": "Time Recording",
    "Time Entries": "Time Recordings",
    "time entry": "time recording",
    "time entries": "time recordings",
    "Rate Card": "Tariff Schedule",
    "Rate Cards": "Tariff Schedules",
    Invoice: "Fee Note",
    Invoices: "Fee Notes",
    invoice: "fee note",
    invoices: "fee notes",
    Expense: "Disbursement",
    Expenses: "Disbursements",
    expense: "disbursement",
    expenses: "disbursements",
    Budget: "Fee Estimate",
    Budgets: "Fee Estimates",
    budget: "fee estimate",
    budgets: "fee estimates",
    Retainer: "Mandate",
    Retainers: "Mandates",
    retainer: "mandate",
    retainers: "mandates",
    "Active Projects": "Active Matters",
    "Project Health": "Matter Health",
    "Project Profitability": "Matter Profitability",
    "Create Customer": "Create Client",
    "Activate Customer": "Activate Client",
    "Offboard Customer": "Offboard Client",
    // Staff-app only (project create/edit dialogs + audit tab). The portal has
    // neither, so these keys are inert there.
    "project.namePlaceholder": "e.g. Dlamini v Road Accident Fund",
    "project.referencePlaceholder": "e.g. RAF-2026-001",
    "audit.tab": "Audit Trail",
  },
};

/**
 * The terminology map as seen by the staff app (`frontend`): the base map,
 * unmodified.
 */
export const TERMINOLOGY: Record<string, Record<string, string>> = TERMINOLOGY_BASE;

/**
 * The terminology map as seen by the customer `portal`. Phase 86 (ADR-332): with legal-za the
 * only profile there are no portal-only overrides left, so the portal reads the same map.
 */
export const PORTAL_TERMINOLOGY: Record<string, Record<string, string>> = TERMINOLOGY;

/**
 * Resolve the audit tab label, falling back to "Audit" when no terminology
 * mapping is present. The terminology hook returns the literal key when no
 * profile maps it — so we substitute a sensible default at the call site.
 *
 * Staff-app only (the portal has no audit tabs); lives here so the whole
 * terminology surface has one home.
 */
export function auditTabLabel(t: (term: string) => string): string {
  const v = t("audit.tab");
  return v === "audit.tab" ? "Audit" : v;
}
