package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StructuralPrerequisiteCheckTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Test
  void invoiceGeneration_allFieldsOnEntityColumns_returnsEmptyViolations() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.INVOICE_GENERATION);

    assertThat(violations).isEmpty();
  }

  @Test
  void invoiceGeneration_allFieldsInJsonb_returnsEmptyViolations() {
    // Backward compat: data only in JSONB, not entity columns
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setCustomFields(
        Map.of(
            "address_line1", "123 Main St",
            "city", "Johannesburg",
            "country", "ZA",
            "tax_number", "VAT123456"));

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.INVOICE_GENERATION);

    assertThat(violations).isEmpty();
  }

  @Test
  void invoiceGeneration_missingAddressLine1_returnsViolation() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    // addressLine1 is null on both entity column and JSONB
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.INVOICE_GENERATION);

    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst().code()).isEqualTo("STRUCTURAL");
    assertThat(violations.getFirst().fieldSlug()).isEqualTo("address_line1");
    assertThat(violations.getFirst().message()).contains("Address Line 1");
    assertThat(violations.getFirst().message()).contains("Invoice Generation");
  }

  @Test
  void proposalSend_missingContactEmail_returnsViolation() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setContactName("John Doe");
    // contactEmail is null on both entity column and JSONB
    customer.setAddressLine1("123 Main St");

    var violations = StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.PROPOSAL_SEND);

    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst().code()).isEqualTo("STRUCTURAL");
    assertThat(violations.getFirst().fieldSlug()).isEqualTo("contact_email");
    assertThat(violations.getFirst().message()).contains("Contact Email");
    assertThat(violations.getFirst().message()).contains("Proposal Sending");
  }

  @Test
  void proposalSend_allFieldsPresent_returnsEmptyViolations() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setContactName("John Doe");
    customer.setContactEmail("john@test.com");
    customer.setAddressLine1("123 Main St");

    var violations = StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.PROPOSAL_SEND);

    assertThat(violations).isEmpty();
  }

  @Test
  void invoiceGeneration_multipleFieldsMissing_returnsMultipleViolations() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    // All required fields are null on both entity and JSONB

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.INVOICE_GENERATION);

    // GAP-L-62: tax_number relaxed from hard-block to soft-warn at draft; only
    // address/city/country remain as structural prerequisites for draft creation.
    assertThat(violations).hasSize(3);
    assertThat(violations)
        .extracting(PrerequisiteViolation::fieldSlug)
        .containsExactly("address_line1", "city", "country");
  }

  @Test
  void invoiceGeneration_missingOnlyTaxNumber_passes() {
    // GAP-L-62: draft creation must succeed when tax_number is the only missing
    // field — it is soft-warned, not hard-blocked. Enforced at send via
    // StructuralPrerequisiteCheck.checkInvoiceSendOnly(...).
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    // taxNumber intentionally left null

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.INVOICE_GENERATION);

    assertThat(violations).isEmpty();
  }

  @Test
  void invoiceSendOnly_missingTaxNumber_returnsViolation() {
    // GAP-L-62: hard-block at send time when tax_number is missing.
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    // taxNumber intentionally left null on both entity + JSONB

    var violations = StructuralPrerequisiteCheck.checkInvoiceSendOnly(customer);

    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst().code()).isEqualTo("STRUCTURAL");
    assertThat(violations.getFirst().fieldSlug()).isEqualTo("tax_number");
    assertThat(violations.getFirst().message()).contains("Tax Number");
    assertThat(violations.getFirst().message()).contains("send an invoice");
  }

  @Test
  void invoiceSendOnly_taxNumberPresent_passes() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setTaxNumber("VAT123456");

    var violations = StructuralPrerequisiteCheck.checkInvoiceSendOnly(customer);

    assertThat(violations).isEmpty();
  }

  @Test
  void isTaxNumberMissing_reflectsEntityAndJsonbSources() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    assertThat(StructuralPrerequisiteCheck.isTaxNumberMissing(customer)).isTrue();

    customer.setTaxNumber("VAT123");
    assertThat(StructuralPrerequisiteCheck.isTaxNumberMissing(customer)).isFalse();

    customer.setTaxNumber(null);
    customer.setCustomFields(Map.of("tax_number", "VAT-JSONB"));
    assertThat(StructuralPrerequisiteCheck.isTaxNumberMissing(customer)).isFalse();
  }

  @Test
  void blankEntityColumnFields_fallsBackToJsonb() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setAddressLine1("  "); // blank entity column
    customer.setCustomFields(Map.of("address_line1", "123 Main St")); // but JSONB has value
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.INVOICE_GENERATION);

    assertThat(violations).isEmpty(); // JSONB fallback saves it
  }

  @Test
  void blankBothEntityAndJsonb_producesViolation() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setAddressLine1("  "); // blank
    customer.setCustomFields(Map.of("address_line1", "  ")); // also blank in JSONB
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.INVOICE_GENERATION);

    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst().fieldSlug()).isEqualTo("address_line1");
  }

  @Test
  void lifecycleActivation_mirrorsInvoiceGenerationFields() {
    // LIFECYCLE_ACTIVATION uses the same structural check set as INVOICE_GENERATION so that a
    // customer cannot transition to ACTIVE while missing billing address / tax info.
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    // No promoted fields set → expect all four violations.

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.LIFECYCLE_ACTIVATION);

    assertThat(violations).hasSize(4);
    assertThat(violations)
        .extracting(PrerequisiteViolation::fieldSlug)
        .containsExactlyInAnyOrder("address_line1", "city", "country", "tax_number");
  }

  @Test
  void lifecycleActivation_allFieldsPresent_returnsEmptyViolations() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.LIFECYCLE_ACTIVATION);

    assertThat(violations).isEmpty();
  }

  @Test
  void projectCreationContext_returnsEmptyViolations() {
    // Non-covered context still returns empty (structural check is opt-in per context).
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.PROJECT_CREATION);

    assertThat(violations).isEmpty();
  }

  @Test
  void coveredSlugs_returnsFieldsForCoveredContextsOnly() {
    // GAP-L-62: tax_number is NOT covered by INVOICE_GENERATION any more (moved to send-only).
    assertThat(StructuralPrerequisiteCheck.coveredSlugs(PrerequisiteContext.INVOICE_GENERATION))
        .containsExactlyInAnyOrder("address_line1", "city", "country");
    assertThat(StructuralPrerequisiteCheck.coveredSlugs(PrerequisiteContext.PROPOSAL_SEND))
        .containsExactlyInAnyOrder("contact_name", "contact_email", "address_line1");
    assertThat(StructuralPrerequisiteCheck.coveredSlugs(PrerequisiteContext.LIFECYCLE_ACTIVATION))
        .containsExactlyInAnyOrder("address_line1", "city", "country", "tax_number");
    assertThat(StructuralPrerequisiteCheck.coveredSlugs(PrerequisiteContext.PROJECT_CREATION))
        .isEmpty();
    assertThat(StructuralPrerequisiteCheck.coveredSlugs(PrerequisiteContext.DOCUMENT_GENERATION))
        .isEmpty();
  }
}
