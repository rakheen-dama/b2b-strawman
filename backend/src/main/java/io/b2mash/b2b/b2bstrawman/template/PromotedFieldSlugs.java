package io.b2mash.b2b.b2bstrawman.template;

import java.util.Set;

/**
 * Single source of truth for custom-field slugs that have been promoted to structural columns on
 * Customer, Project, and Invoice entities (Phase 63 / Epics 459–460).
 *
 * <p>Used by:
 *
 * <ul>
 *   <li>{@link VariableMetadataRegistry} to filter promoted slugs out of the dynamic custom-field
 *       group so they no longer appear under "Custom Fields" in the template variable picker.
 *   <li>{@link CustomerContextBuilder}, {@link ProjectContextBuilder}, {@link
 *       InvoiceContextBuilder} to inject backward-compatible {@code customFields.slug} aliases
 *       pointing at the promoted structural getters.
 * </ul>
 *
 * <p>Existing tenants may still have {@link
 * io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition} rows for these slugs until a follow-up
 * cleanup phase — filtering them here keeps the template picker clean without requiring a DB
 * migration.
 */
public final class PromotedFieldSlugs {

  public static final Set<String> CUSTOMER =
      Set.of(
          // common-customer (deleted)
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
          "physical_address");

  public static final Set<String> PROJECT =
      Set.of("reference_number", "priority", "engagement_type", "matter_type");

  public static final Set<String> INVOICE =
      Set.of("purchase_order_number", "tax_type", "billing_period_start", "billing_period_end");

  /**
   * Task entity promoted slugs — both backed by long-standing structural columns on {@code tasks}.
   * {@code priority} has been a structural column since V9 (exposed via {@link
   * io.b2mash.b2b.b2bstrawman.task.Task#getPriority()}); {@code estimated_hours} was added in V89
   * (Phase 63, exposed via {@link io.b2mash.b2b.b2bstrawman.task.Task#getEstimatedHours()}). Both
   * were historically duplicated into {@code common-task.json} as JSONB custom fields, which Epic
   * 462 removes to avoid double-seeding and column/custom-field drift.
   */
  public static final Set<String> TASK = Set.of("priority", "estimated_hours");

  private PromotedFieldSlugs() {
    // static-only
  }
}
