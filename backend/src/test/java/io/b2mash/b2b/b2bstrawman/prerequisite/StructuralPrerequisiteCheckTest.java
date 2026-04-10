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

    assertThat(violations).hasSize(4);
    assertThat(violations)
        .extracting(PrerequisiteViolation::fieldSlug)
        .containsExactly("address_line1", "city", "country", "tax_number");
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
  void unknownContext_returnsEmptyViolations() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Test Corp", "test@test.com", MEMBER_ID);

    var violations =
        StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.LIFECYCLE_ACTIVATION);

    assertThat(violations).isEmpty();
  }
}
