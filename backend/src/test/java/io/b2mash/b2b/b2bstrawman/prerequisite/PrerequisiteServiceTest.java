package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.setupstatus.DocumentGenerationReadinessService;
import io.b2mash.b2b.b2bstrawman.setupstatus.TemplateReadiness;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrerequisiteServiceTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Mock private FieldDefinitionService fieldDefinitionService;
  @Mock private CustomerRepository customerRepository;
  @Mock private ProjectTemplateService projectTemplateService;
  @Mock private PortalContactRepository portalContactRepository;
  @Mock private DocumentGenerationReadinessService documentGenerationReadinessService;
  @Mock private CustomerProjectRepository customerProjectRepository;

  private PrerequisiteService service;

  @BeforeEach
  void setUp() {
    service =
        new PrerequisiteService(
            fieldDefinitionService,
            customerRepository,
            projectTemplateService,
            portalContactRepository,
            documentGenerationReadinessService,
            customerProjectRepository);
  }

  @Test
  void checkForContext_allFieldsFilled_returnsPassed() {
    // Use a non-promoted custom field to test JSONB check path
    var fd = createFieldDefinition("VAT Number (custom)", "vat_number_custom", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomerWithPromotedFields(Map.of("vat_number_custom", "VAT123"));
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertThat(result.context()).isEqualTo(PrerequisiteContext.INVOICE_GENERATION);
  }

  @Test
  void checkForContext_missingRequiredField_returnsFailed() {
    // Use a non-promoted custom field to test JSONB check path
    var fd = createFieldDefinition("Court Type", "court_type", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomerWithPromotedFields(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().code()).isEqualTo("MISSING_FIELD");
    assertThat(result.violations().getFirst().fieldSlug()).isEqualTo("court_type");
  }

  @Test
  void checkForContext_noFieldsRequired_returnsPassed() {
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION);
    // Structural check for INVOICE_GENERATION needs customer with promoted fields + portal contacts
    mockCustomerWithPromotedFields(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void checkForContext_nullCustomFields_returnsViolationsForAllRequired() {
    // Use non-promoted custom fields to test JSONB check path
    var fd1 = createFieldDefinition("Court Type", "court_type", FieldType.TEXT);
    fd1.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    var fd2 = createFieldDefinition("Case Number", "case_number", FieldType.TEXT);
    fd2.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd1, fd2);
    // Customer with null JSONB but promoted fields set (so structural checks pass)
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Test Customer", "test@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(null);
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(2);
  }

  @Test
  void checkForContext_partiallyFilled_returnsOnlyMissingViolations() {
    // Use non-promoted custom fields to test JSONB check path
    var fd1 = createFieldDefinition("Court Type", "court_type", FieldType.TEXT);
    fd1.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    var fd2 = createFieldDefinition("Case Number", "case_number", FieldType.TEXT);
    fd2.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd1, fd2);
    mockCustomerWithPromotedFields(Map.of("court_type", "high_court"));
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().fieldSlug()).isEqualTo("case_number");
  }

  @Test
  void checkForContext_contextWithNoMatchingFields_returnsPassed() {
    // Field is required for INVOICE_GENERATION, not PROPOSAL_SEND
    mockFieldDefinitions(PrerequisiteContext.PROPOSAL_SEND);
    // Structural check for PROPOSAL_SEND needs customer with promoted fields + portal contacts
    mockCustomerWithPromotedFields(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.PROPOSAL_SEND, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void violationContainsFieldSlugAndGroupName() {
    // Use a non-promoted field so the JSONB check path produces the violation
    var fd = createFieldDefinition("Court Type", "court_type", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomerWithPromotedFields(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    var violation = result.violations().getFirst();
    assertThat(violation.fieldSlug()).isEqualTo("court_type");
    assertThat(violation.groupName()).isNull(); // group lookup not implemented in this slice
  }

  @Test
  void violationContainsEntityTypeAndId() {
    // Use a non-promoted field so the JSONB check produces the violation
    var fd = createFieldDefinition("Court District", "court_district", FieldType.DROPDOWN);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomerWithPromotedFields(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    var violation = result.violations().getFirst();
    assertThat(violation.entityType()).isEqualTo("CUSTOMER");
    assertThat(violation.entityId()).isEqualTo(CUSTOMER_ID);
    assertThat(violation.resolution()).contains("Court District");
  }

  @Test
  void checkEngagementPrerequisites_emptyTemplate_returnsPassed() {
    var templateId = UUID.randomUUID();
    when(projectTemplateService.getRequiredCustomerFields(templateId)).thenReturn(List.of());

    var result = service.checkEngagementPrerequisites(CUSTOMER_ID, templateId);

    assertThat(result.passed()).isTrue();
    assertThat(result.context()).isEqualTo(PrerequisiteContext.PROJECT_CREATION);
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void prerequisiteContext_displayLabels_areCorrect() {
    assertThat(PrerequisiteContext.LIFECYCLE_ACTIVATION.getDisplayLabel())
        .isEqualTo("Customer Activation");
    assertThat(PrerequisiteContext.INVOICE_GENERATION.getDisplayLabel())
        .isEqualTo("Invoice Generation");
    assertThat(PrerequisiteContext.PROPOSAL_SEND.getDisplayLabel()).isEqualTo("Proposal Sending");
    assertThat(PrerequisiteContext.DOCUMENT_GENERATION.getDisplayLabel())
        .isEqualTo("Document Generation");
    assertThat(PrerequisiteContext.PROJECT_CREATION.getDisplayLabel())
        .isEqualTo("Project Creation");
  }

  // --- Structural check tests (244.6) ---

  @Test
  void structuralCheck_invoiceGeneration_missingPortalContactAndEmail_returnsViolation() {
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION);
    // Customer with promoted fields set but NO email
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Test Customer", null, MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(Map.of());
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    when(portalContactRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().code()).isEqualTo("STRUCTURAL");
    assertThat(result.violations().getFirst().message())
        .contains("email address or portal contact");
  }

  @Test
  void structuralCheck_proposalSend_missingPortalContact_returnsViolation() {
    mockFieldDefinitions(PrerequisiteContext.PROPOSAL_SEND);
    // Customer with all promoted fields set (structural field checks pass)
    mockCustomerWithPromotedFields(Map.of());
    when(portalContactRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

    var result =
        service.checkForContext(
            PrerequisiteContext.PROPOSAL_SEND, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().code()).isEqualTo("STRUCTURAL");
    assertThat(result.violations().getFirst().message()).contains("portal contact");
  }

  @Test
  void structuralCheck_documentGeneration_delegatesToReadinessService() {
    mockFieldDefinitions(PrerequisiteContext.DOCUMENT_GENERATION);
    var readiness =
        new TemplateReadiness(
            UUID.randomUUID(), "Invoice Template", "invoice-template", false, List.of("org_name"));
    when(documentGenerationReadinessService.checkReadiness(
            TemplateEntityType.CUSTOMER, CUSTOMER_ID))
        .thenReturn(List.of(readiness));

    var result =
        service.checkForContext(
            PrerequisiteContext.DOCUMENT_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().code()).isEqualTo("STRUCTURAL");
    assertThat(result.violations().getFirst().message()).contains("org_name");
  }

  @Test
  void structuralCheck_invoiceGeneration_allPresent_noViolations() {
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION);
    mockCustomerWithPromotedFields(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  // --- 461.10: Structural promoted-field prerequisite check tests ---

  @Test
  void structuralFieldCheck_invoiceGeneration_customerWithCompleteAddress_passes() {
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION);
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Complete Customer", "complete@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(Map.of());
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void structuralFieldCheck_invoiceGeneration_customerMissingCity_returnsViolation() {
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION);
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "No City Customer", "nocity@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(Map.of());
    customer.setAddressLine1("123 Main St");
    // city is null
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().code()).isEqualTo("STRUCTURAL");
    assertThat(result.violations().getFirst().fieldSlug()).isEqualTo("city");
    assertThat(result.violations().getFirst().message()).contains("City");
  }

  @Test
  void structuralFieldCheck_proposalSend_customerMissingContactEmail_returnsViolation() {
    mockFieldDefinitions(PrerequisiteContext.PROPOSAL_SEND);
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "No Contact Customer", "nocontact@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(Map.of());
    customer.setContactName("John Doe");
    // contactEmail is null
    customer.setAddressLine1("123 Main St");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    // No portal contacts either (triggers both structural field check AND portal contact check)
    when(portalContactRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

    var result =
        service.checkForContext(
            PrerequisiteContext.PROPOSAL_SEND, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    // Should have structural violation for contact_email AND portal contact violation
    assertThat(result.violations()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(result.violations())
        .anyMatch(v -> "STRUCTURAL".equals(v.code()) && "contact_email".equals(v.fieldSlug()));
    assertThat(result.violations())
        .anyMatch(v -> "STRUCTURAL".equals(v.code()) && v.message().contains("portal contact"));
  }

  // --- Promoted-slug dedup / LIFECYCLE_ACTIVATION tests ---

  @Test
  void invoiceGeneration_promotedSlugFieldDefinition_producesExactlyOneStructuralViolation() {
    // A legacy FieldDefinition requires "address_line1" for INVOICE_GENERATION. After Epic 459
    // that slug is promoted; after 461B it is covered by StructuralPrerequisiteCheck. We must
    // produce EXACTLY ONE violation (structural), not two (custom-field + structural) and not
    // zero (silently dropped).
    var fd = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);

    // Customer missing address_line1 on BOTH entity column and JSONB
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "No Address Corp", "noaddr@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(Map.of());
    // address_line1 null
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    var addressViolations =
        result.violations().stream().filter(v -> "address_line1".equals(v.fieldSlug())).toList();
    assertThat(addressViolations).hasSize(1);
    assertThat(addressViolations.getFirst().code()).isEqualTo("STRUCTURAL");
  }

  @Test
  void invoiceGeneration_promotedSlugFilledInEntityColumn_producesNoViolation() {
    // Same legacy FieldDefinition, but this time the tenant has migrated data into the entity
    // column. No violation should be produced — neither the structural check nor the custom-field
    // check should fail.
    var fd = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomerWithPromotedFields(Map.of()); // entity columns set, JSONB empty
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void lifecycleActivation_promotedSlugFieldDefinition_notSilentlyDropped() {
    // A FieldDefinition requires "address_line1" for LIFECYCLE_ACTIVATION. Previously this was
    // silently dropped because the dedup filter used a global promoted-slug set but the
    // structural check only ran for INVOICE_GENERATION / PROPOSAL_SEND. Now LIFECYCLE_ACTIVATION
    // has structural coverage, so the missing field MUST produce a violation (structural).
    var fd = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("LIFECYCLE_ACTIVATION")));
    mockFieldDefinitions(PrerequisiteContext.LIFECYCLE_ACTIVATION, fd);

    // Customer missing address_line1 on BOTH entity column and JSONB
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Inactive Corp", "inactive@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(Map.of());
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

    var result =
        service.checkForContext(
            PrerequisiteContext.LIFECYCLE_ACTIVATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> "address_line1".equals(v.fieldSlug()) && "STRUCTURAL".equals(v.code()));
  }

  @Test
  void lifecycleActivation_promotedSlugInEntityColumn_passes() {
    // Symmetric happy path: tenant has migrated data → no violation for the promoted slug.
    var fd = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("LIFECYCLE_ACTIVATION")));
    mockFieldDefinitions(PrerequisiteContext.LIFECYCLE_ACTIVATION, fd);
    mockCustomerWithPromotedFields(Map.of());

    var result =
        service.checkForContext(
            PrerequisiteContext.LIFECYCLE_ACTIVATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void checkEngagementPrerequisites_promotedSlugInEntityColumn_passes() {
    // Engagement (project-template) check: a required custom field is a promoted slug and the
    // customer has it ONLY in the entity column. Must pass — previously the service read only
    // JSONB and falsely reported missing.
    var templateId = UUID.randomUUID();
    var fd = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    when(projectTemplateService.getRequiredCustomerFields(templateId)).thenReturn(List.of(fd));

    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Migrated Corp", "mig@test.com", MEMBER_ID, LifecycleStatus.ACTIVE);
    customer.setCustomFields(Map.of()); // JSONB empty
    customer.setAddressLine1("123 Main St"); // entity column set
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

    var result = service.checkEngagementPrerequisites(CUSTOMER_ID, templateId);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  // --- Helper methods ---

  private FieldDefinition createFieldDefinition(String name, String slug, FieldType fieldType) {
    return new FieldDefinition(EntityType.CUSTOMER, name, slug, fieldType);
  }

  private void mockFieldDefinitions(PrerequisiteContext context, FieldDefinition... definitions) {
    when(fieldDefinitionService.getRequiredFieldsForContext(eq(EntityType.CUSTOMER), eq(context)))
        .thenReturn(List.of(definitions));
  }

  private void mockCustomer(Map<String, Object> customFields) {
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Test Customer", "test@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(customFields);
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
  }

  /**
   * Creates a mock customer with JSONB custom fields AND promoted entity columns set. Use this when
   * testing INVOICE_GENERATION or PROPOSAL_SEND contexts where structural checks will run.
   */
  private void mockCustomerWithPromotedFields(Map<String, Object> customFields) {
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Test Customer", "test@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(customFields);
    // Set promoted fields to satisfy structural checks
    customer.setAddressLine1("123 Main St");
    customer.setCity("Johannesburg");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    customer.setContactName("Test Contact");
    customer.setContactEmail("contact@test.com");
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
  }

  private void mockCustomerWithNullFields() {
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Test Customer", "test@test.com", MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(null);
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
  }

  private void mockCustomerWithEmail(String email) {
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "Test Customer", email, MEMBER_ID, LifecycleStatus.PROSPECT);
    customer.setCustomFields(Map.of());
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
  }

  private void mockPortalContactWithEmail() {
    var contact =
        new PortalContact(
            "org-1",
            CUSTOMER_ID,
            "contact@test.com",
            "Test Contact",
            PortalContact.ContactRole.PRIMARY);
    when(portalContactRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(contact));
  }
}
