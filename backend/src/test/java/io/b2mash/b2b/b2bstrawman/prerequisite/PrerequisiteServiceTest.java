package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
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
    var fd = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomer(Map.of("address_line1", "123 Main St"));
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
    var fd = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomer(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().code()).isEqualTo("MISSING_FIELD");
    assertThat(result.violations().getFirst().fieldSlug()).isEqualTo("address_line1");
  }

  @Test
  void checkForContext_noFieldsRequired_returnsPassed() {
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION);
    // Structural check for INVOICE_GENERATION needs customer and portal contacts
    mockCustomer(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void checkForContext_nullCustomFields_returnsViolationsForAllRequired() {
    var fd1 = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd1.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    var fd2 = createFieldDefinition("City", "city", FieldType.TEXT);
    fd2.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd1, fd2);
    mockCustomerWithNullFields();
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(2);
  }

  @Test
  void checkForContext_partiallyFilled_returnsOnlyMissingViolations() {
    var fd1 = createFieldDefinition("Address Line 1", "address_line1", FieldType.TEXT);
    fd1.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    var fd2 = createFieldDefinition("City", "city", FieldType.TEXT);
    fd2.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd1, fd2);
    mockCustomer(Map.of("address_line1", "123 Main St"));
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().fieldSlug()).isEqualTo("city");
  }

  @Test
  void checkForContext_contextWithNoMatchingFields_returnsPassed() {
    // Field is required for INVOICE_GENERATION, not PROPOSAL_SEND
    mockFieldDefinitions(PrerequisiteContext.PROPOSAL_SEND);
    // Structural check for PROPOSAL_SEND needs portal contacts
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.PROPOSAL_SEND, EntityType.CUSTOMER, CUSTOMER_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void violationContainsFieldSlugAndGroupName() {
    var fd = createFieldDefinition("Tax Number", "tax_number", FieldType.TEXT);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomer(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    var violation = result.violations().getFirst();
    assertThat(violation.fieldSlug()).isEqualTo("tax_number");
    assertThat(violation.groupName()).isNull(); // group lookup not implemented in this slice
  }

  @Test
  void violationContainsEntityTypeAndId() {
    var fd = createFieldDefinition("Country", "country", FieldType.DROPDOWN);
    fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
    mockFieldDefinitions(PrerequisiteContext.INVOICE_GENERATION, fd);
    mockCustomer(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

    var violation = result.violations().getFirst();
    assertThat(violation.entityType()).isEqualTo("CUSTOMER");
    assertThat(violation.entityId()).isEqualTo(CUSTOMER_ID);
    assertThat(violation.resolution()).contains("Country");
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
    mockCustomerWithEmail(null);
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
    mockCustomer(Map.of());
    mockPortalContactWithEmail();

    var result =
        service.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, CUSTOMER_ID);

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
        new Customer(
            "Test Customer",
            "test@test.com",
            null,
            null,
            null,
            MEMBER_ID,
            CustomerType.INDIVIDUAL,
            LifecycleStatus.PROSPECT);
    customer.setCustomFields(customFields);
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
  }

  private void mockCustomerWithNullFields() {
    var customer =
        new Customer(
            "Test Customer",
            "test@test.com",
            null,
            null,
            null,
            MEMBER_ID,
            CustomerType.INDIVIDUAL,
            LifecycleStatus.PROSPECT);
    customer.setCustomFields(null);
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
  }

  private void mockCustomerWithEmail(String email) {
    var customer =
        new Customer(
            "Test Customer",
            email,
            null,
            null,
            null,
            MEMBER_ID,
            CustomerType.INDIVIDUAL,
            LifecycleStatus.PROSPECT);
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
