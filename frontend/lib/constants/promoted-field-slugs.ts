/**
 * Slugs for custom fields that have been promoted to first-class columns on
 * the Customer entity. These must be filtered out of `CustomFieldSection`
 * rendering so they do not show up twice on the customer detail page.
 *
 * This set mirrors `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PromotedFieldSlugs.java`
 * (`PromotedFieldSlugs.CUSTOMER`). Keep the two in sync.
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
