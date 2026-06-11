/**
 * Vertical-terminology system — the single source of truth shared by the staff
 * app (`frontend`) and the customer `portal`.
 *
 * Before Wave 2.5 each app shipped its own copy of this map and they had
 * drifted. The reconciliation (see `packages/shared/README` decision table in
 * the PR) established one BASE map plus an explicit, commented set of
 * portal-only overrides — never silent divergence.
 *
 * The BASE map is the staff-app superset. Keys that only the staff app reads
 * (project placeholders, the audit-tab label) live here harmlessly: the portal
 * never calls `t()` with those keys, so carrying them costs nothing and keeps
 * one map to maintain.
 */
export const TERMINOLOGY_BASE: Record<string, Record<string, string>> = {
  "consulting-za": {
    Customer: "Client",
    Customers: "Clients",
    customer: "client",
    customers: "clients",
    "Time Entry": "Time Log",
    "Time Entries": "Time Logs",
    "time entry": "time log",
    "time entries": "time logs",
    "Rate Card": "Billing Rates",
    "Rate Cards": "Billing Rates",
    "rate card": "billing rates",
    "rate cards": "billing rates",
    "Time Tracking": "Time Logs",
    "Rates & Currency": "Billing Rates",
    // Staff-app only (project create/edit dialogs). Portal has no such dialogs.
    "project.namePlaceholder": "e.g. Q4 Strategy Engagement",
    "project.referencePlaceholder": "e.g. ENG-2026-001",
  },
  "accounting-za": {
    Project: "Engagement",
    Projects: "Engagements",
    project: "engagement",
    projects: "engagements",
    Customer: "Client",
    Customers: "Clients",
    customer: "client",
    customers: "clients",
    Proposal: "Engagement Letter",
    Proposals: "Engagement Letters",
    proposal: "engagement letter",
    proposals: "engagement letters",
    "Rate Card": "Fee Schedule",
    "Rate Cards": "Fee Schedules",
    "Active Projects": "Active Engagements",
    "Project Health": "Engagement Health",
    "Project Profitability": "Engagement Profitability",
    "Create Customer": "Create Client",
    "Activate Customer": "Activate Client",
    "Offboard Customer": "Offboard Client",
    // Staff-app only (project create/edit dialogs). Portal has no such dialogs.
    "project.namePlaceholder": "e.g. FY2026 Audit",
    "project.referencePlaceholder": "e.g. ENG-2026-001",
  },
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
 * Portal-only terminology overrides, layered on top of {@link TERMINOLOGY_BASE}.
 *
 * These are intentional client-vs-staff divergences, NOT drift. The portal
 * sidebar seeds its nav labels from a fixed English lexicon
 * (`PORTAL_NAV_LABELS`) and then runs each label through `t()`. For the
 * `accounting-za` profile the seed label is the literal string "Matters", so
 * the portal needs `Matters → Engagements` to display the right word. The staff
 * app never feeds the literal "Matters" through `t()` (its nav starts from the
 * base term "Projects"), so it must NOT carry this mapping. Behaviour pinned by
 * `portal/components/__tests__/portal-sidebar.test.tsx`
 * ("renders 'Engagements' instead of 'Matters' for accounting-za").
 */
export const PORTAL_TERMINOLOGY_OVERRIDES: Record<string, Record<string, string>> = {
  "accounting-za": {
    Matters: "Engagements",
    matters: "engagements",
  },
};

/**
 * The terminology map as seen by the staff app (`frontend`): the base map,
 * unmodified.
 */
export const TERMINOLOGY: Record<string, Record<string, string>> = TERMINOLOGY_BASE;

/**
 * The terminology map as seen by the customer `portal`: the base map with the
 * portal-only overrides merged in per profile.
 */
export const PORTAL_TERMINOLOGY: Record<string, Record<string, string>> = Object.fromEntries(
  Object.entries(TERMINOLOGY_BASE).map(([profile, terms]) => [
    profile,
    { ...terms, ...(PORTAL_TERMINOLOGY_OVERRIDES[profile] ?? {}) },
  ]),
);

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
