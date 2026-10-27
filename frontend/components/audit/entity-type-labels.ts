/**
 * Legal display labels for audit `entity_type` storage literals (Phase 88, 625D).
 *
 * The keys are backend storage literals and never change (ADR-342): `project` stays `project` on
 * the wire, in the URL and in Select values. Users see "Matter", "Client" and "Fee note" because
 * this map writes those words. It is a literal → label display map, not a terminology layer
 * (ADR-338).
 *
 * Coverage is pinned by `__tests__/entity-type-labels.test.ts`, which scans
 * `backend/src/main/java` for every emitted literal. Unknown literals (for example AI runtime
 * types) fall back to sentence case, matching the backend `AuditEventTypeRegistry.legalLabel`.
 */
export const ENTITY_TYPE_LABELS: Readonly<Record<string, string>> = Object.freeze({
  // Matters and clients
  project: "Matter",
  customer: "Client",
  project_budget: "Matter budget",
  project_member: "Matter team member",
  project_template: "Matter template",
  matter_closure: "Matter closure",
  // Billing
  invoice: "Fee note",
  invoice_line: "Fee note line", // defensive: not emitted today
  billing_run: "Fee run",
  legal_disbursement: "Disbursement",
  expense: "Expense",
  time_entry: "Time recording",
  billing_rate: "Billing rate",
  cost_rate: "Cost rate",
  tax_rate: "Tax rate",
  tariff_schedule: "Tariff schedule",
  tariff_item: "Tariff item",
  collection_activity: "Collections activity",
  cash_digest: "Cash digest",
  // Acceptance (covers any document sent for acceptance, not only engagement letters)
  acceptance_request: "Document for acceptance",
  // Trust accounting
  trust_transaction: "Trust transaction",
  trust_account: "Trust account",
  trust_investment: "Trust investment",
  trust_reconciliation: "Trust reconciliation",
  bank_statement: "Bank statement",
  bank_statement_line: "Bank statement line",
  interest_run: "Interest run",
  lpff_rate: "LPFF rate",
  // Work
  task: "Task",
  task_item: "Task item",
  comment: "Comment",
  court_date: "Court date",
  prescription_tracker: "Prescription tracker",
  adverse_party: "Adverse party",
  adverse_party_link: "Adverse party link",
  conflict_check: "Conflict check",
  // Documents and correspondence
  document: "Document",
  generated_document: "Generated document",
  document_template: "Document template",
  clause: "Clause",
  correspondence: "Correspondence",
  email: "Email",
  email_delivery_log: "Email delivery log",
  // Compliance
  information_request: "Information request",
  request_item: "Information request item",
  // Any firm checklist template (FICA onboarding is one of many), so no "FICA" in the label.
  checklist_instance: "Checklist",
  checklist_instance_item: "Checklist item",
  checklist_template: "Checklist template",
  data_subject_request: "Data subject request",
  retention_policy: "Retention policy",
  audit_export: "Audit export",
  security: "Security",
  compliance_audit_report: "Compliance report",
  compliance_audit_finding: "Compliance finding",
  // Firm administration
  member: "Member",
  pending_invitation: "Invitation",
  org_role: "Role",
  org_settings: "Firm settings",
  org_integration: "Integration",
  accounting_xero_connection: "Xero connection",
  subscription: "Subscription",
  portal_contact: "Portal contact", // defensive: client-activity child type, no emitter today
  tag: "Tag",
  saved_view: "Saved view",
  field_definition: "Custom field",
  field_group: "Field group",
  pack_install: "Pack",
  // AI and MCP
  ai_execution_gate: "AI gate",
  mcp_tool: "MCP tool",
  mcp_session: "MCP session",
});

/** `"foo_bar.baz"` → `"Foo bar baz"` (same shape as the backend `legalLabel` fallback). */
export function sentenceCaseKey(key: string): string {
  const words = key.split(/[._]+/).filter(Boolean).join(" ");
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : "";
}

/**
 * Legal label for an audit entity literal: the map first, then `fallbackLabel` (for example the
 * backend facet label), then sentence case of the key. `""` for an empty literal.
 */
export function entityTypeLabel(literal: string, fallbackLabel?: string): string {
  if (!literal) return "";
  // Own-property lookup: `?entityType=constructor` must not resolve to Object.prototype members.
  if (Object.hasOwn(ENTITY_TYPE_LABELS, literal)) return ENTITY_TYPE_LABELS[literal];
  return fallbackLabel || sentenceCaseKey(literal);
}

/**
 * Human name for an audit row's entity, read from the row's details. Only fee notes have one
 * today (`details.invoice_number`, e.g. "INV-0003"; absent on draft `invoice.created`). Returns
 * `null` when there is no usable string.
 */
export function entityDisplayName(
  entityType: string,
  details: Record<string, unknown> | null | undefined
): string | null {
  if (entityType !== "invoice" || !details) return null;
  const number = details.invoice_number;
  return typeof number === "string" && number.trim() ? number : null;
}
