/**
 * Slugs for custom fields that have been promoted to first-class columns on
 * the Customer, Project, Task, and Invoice entities. These must be filtered
 * out of `CustomFieldSection` rendering so they do not show up twice on
 * detail pages.
 *
 * These sets mirror
 * `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PromotedFieldSlugs.java`.
 * Keep the two in sync.
 */
export const PROMOTED_CUSTOMER_SLUGS: ReadonlySet<string> = new Set([
  // common-customer (deleted pack)
  "address_line1",
  "address_line2",
  "city",
  "state_province",
  "postal_code",
  "country",
  "tax_number",
  "phone",
  // accounting-za-customer
  "vat_number",
  "primary_contact_name",
  "primary_contact_email",
  "primary_contact_phone",
  "acct_company_registration_number",
  "acct_entity_type",
  "financial_year_end",
  "registered_address",
  // legal-za-customer
  "registration_number",
  "client_type",
  "physical_address",
]);

/**
 * PROJECT promoted slugs (Epic 464 / Phase 63).
 *
 * Note: `engagement_type` and `matter_type` both map to the structural
 * `work_type` column depending on the active vertical profile (accounting
 * vs legal). The frontend camelCase field is `workType`.
 */
export const PROMOTED_PROJECT_SLUGS: ReadonlySet<string> = new Set([
  "reference_number",
  "priority",
  "engagement_type",
  "matter_type",
]);

/** TASK promoted slugs (Epic 464 / Phase 63). */
export const PROMOTED_TASK_SLUGS: ReadonlySet<string> = new Set([
  "priority",
  "estimated_hours",
]);

/**
 * INVOICE promoted slugs (Epic 464 / Phase 63).
 *
 * Note: backend slug is `purchase_order_number` (frontend camelCase: `poNumber`).
 */
export const PROMOTED_INVOICE_SLUGS: ReadonlySet<string> = new Set([
  "purchase_order_number",
  "tax_type",
  "billing_period_start",
  "billing_period_end",
]);
