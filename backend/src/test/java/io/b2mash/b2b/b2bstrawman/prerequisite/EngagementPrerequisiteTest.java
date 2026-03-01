package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTagRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskItemRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
class EngagementPrerequisiteTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID TEMPLATE_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Mock private FieldDefinitionService fieldDefinitionService;
  @Mock private CustomerRepository customerRepository;
  @Mock private ProjectTemplateService projectTemplateService;
  @Mock private ProjectTemplateRepository templateRepository;
  @Mock private FieldDefinitionRepository fieldDefinitionRepository;
  @Mock private AuditService auditService;
  @Mock private EntityManager entityManager;
  @Mock private FieldGroupRepository fieldGroupRepository;
  @Mock private FieldGroupMemberRepository fieldGroupMemberRepository;
  @Mock private Query nativeQuery;
  @Mock private TemplateTaskRepository templateTaskRepository;
  @Mock private TemplateTaskItemRepository templateTaskItemRepository;
  @Mock private TemplateTagRepository templateTagRepository;
  @Mock private io.b2mash.b2b.b2bstrawman.tag.TagRepository tagLookupRepository;

  private PrerequisiteService prerequisiteService;

  @BeforeEach
  void setUp() {
    prerequisiteService =
        new PrerequisiteService(fieldDefinitionService, customerRepository, projectTemplateService);
  }

  // --- Tests 1-4: checkEngagementPrerequisites ---

  @Test
  void checkEngagement_allFieldsFilled_passes() {
    var fd = createCustomerFieldDefinition("Tax Number", "tax_number", FieldType.TEXT);
    when(projectTemplateService.getRequiredCustomerFields(TEMPLATE_ID)).thenReturn(List.of(fd));
    mockCustomer(Map.of("tax_number", "VAT123456"));

    var result = prerequisiteService.checkEngagementPrerequisites(CUSTOMER_ID, TEMPLATE_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.context()).isEqualTo(PrerequisiteContext.PROJECT_CREATION);
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void checkEngagement_missingField_returnsViolation() {
    var fd = createCustomerFieldDefinition("Tax Number", "tax_number", FieldType.TEXT);
    when(projectTemplateService.getRequiredCustomerFields(TEMPLATE_ID)).thenReturn(List.of(fd));
    mockCustomer(Map.of());

    var result = prerequisiteService.checkEngagementPrerequisites(CUSTOMER_ID, TEMPLATE_ID);

    assertThat(result.passed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().code()).isEqualTo("MISSING_FIELD");
    assertThat(result.violations().getFirst().fieldSlug()).isEqualTo("tax_number");
    assertThat(result.violations().getFirst().entityType()).isEqualTo("CUSTOMER");
    assertThat(result.violations().getFirst().entityId()).isEqualTo(CUSTOMER_ID);
  }

  @Test
  void checkEngagement_templateHasNoRequirements_passes() {
    when(projectTemplateService.getRequiredCustomerFields(TEMPLATE_ID)).thenReturn(List.of());

    var result = prerequisiteService.checkEngagementPrerequisites(CUSTOMER_ID, TEMPLATE_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.context()).isEqualTo(PrerequisiteContext.PROJECT_CREATION);
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void checkEngagement_inactiveFieldInTemplate_skipped() {
    // getRequiredCustomerFields already filters to active only, so return only active ones
    var activeFd = createCustomerFieldDefinition("City", "city", FieldType.TEXT);
    when(projectTemplateService.getRequiredCustomerFields(TEMPLATE_ID))
        .thenReturn(List.of(activeFd));
    mockCustomer(Map.of("city", "Johannesburg"));

    var result = prerequisiteService.checkEngagementPrerequisites(CUSTOMER_ID, TEMPLATE_ID);

    assertThat(result.passed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  // --- Tests 5-7: updateRequiredCustomerFields (via ProjectTemplateService) ---

  @Test
  void updateRequiredFields_validIds_succeeds() {
    var fd =
        createCustomerFieldDefinitionWithId(
            UUID.randomUUID(), "Tax Number", "tax_number", FieldType.TEXT);
    var template = createTemplate();
    when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
    when(fieldDefinitionRepository.findAllById(List.of(fd.getId()))).thenReturn(List.of(fd));
    when(templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId()))
        .thenReturn(List.of());
    when(templateTagRepository.findTagIdsByTemplateId(template.getId())).thenReturn(List.of());

    // Build a ProjectTemplateService for direct testing
    var templateService = buildProjectTemplateService();
    templateService.updateRequiredCustomerFields(TEMPLATE_ID, List.of(fd.getId()));

    assertThat(template.getRequiredCustomerFieldIds()).containsExactly(fd.getId());
    verify(templateRepository).save(template);
  }

  @Test
  void updateRequiredFields_nonExistentId_returns400() {
    var unknownId = UUID.randomUUID();
    var template = createTemplate();
    when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
    when(fieldDefinitionRepository.findAllById(List.of(unknownId))).thenReturn(List.of());

    var templateService = buildProjectTemplateService();

    assertThatThrownBy(
            () -> templateService.updateRequiredCustomerFields(TEMPLATE_ID, List.of(unknownId)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Field definition not found");
  }

  @Test
  void updateRequiredFields_nonCustomerField_returns400() {
    var projectField =
        createFieldDefinitionWithType(
            UUID.randomUUID(), "Scope", "scope", FieldType.TEXT, EntityType.PROJECT);
    var template = createTemplate();
    when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
    when(fieldDefinitionRepository.findAllById(List.of(projectField.getId())))
        .thenReturn(List.of(projectField));

    var templateService = buildProjectTemplateService();

    assertThatThrownBy(
            () ->
                templateService.updateRequiredCustomerFields(
                    TEMPLATE_ID, List.of(projectField.getId())))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("not a customer field");
  }

  // --- Test 8: field deactivation removes from templates ---

  @Test
  void fieldDeactivation_removesFromTemplates() {
    var fieldId = UUID.randomUUID();
    var fd =
        createCustomerFieldDefinitionWithId(fieldId, "Tax Number", "tax_number", FieldType.TEXT);
    when(fieldDefinitionRepository.findById(fieldId)).thenReturn(Optional.of(fd));
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(eq("fieldId"), anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(eq("fieldIdJsonb"), anyString())).thenReturn(nativeQuery);
    when(nativeQuery.executeUpdate()).thenReturn(1);

    var fieldDefService =
        new FieldDefinitionService(
            fieldDefinitionRepository,
            auditService,
            entityManager,
            fieldGroupRepository,
            fieldGroupMemberRepository);
    fieldDefService.deactivate(fieldId);

    verify(entityManager).createNativeQuery(anyString());
    verify(nativeQuery).executeUpdate();
    assertThat(fd.isActive()).isFalse();
  }

  // --- Helper methods ---

  private FieldDefinition createCustomerFieldDefinition(
      String name, String slug, FieldType fieldType) {
    return new FieldDefinition(EntityType.CUSTOMER, name, slug, fieldType);
  }

  private FieldDefinition createCustomerFieldDefinitionWithId(
      UUID id, String name, String slug, FieldType fieldType) {
    var fd = new FieldDefinition(EntityType.CUSTOMER, name, slug, fieldType);
    // Use reflection to set the ID since it's @GeneratedValue
    try {
      var idField = FieldDefinition.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(fd, id);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field definition ID", e);
    }
    return fd;
  }

  private FieldDefinition createFieldDefinitionWithType(
      UUID id, String name, String slug, FieldType fieldType, EntityType entityType) {
    var fd = new FieldDefinition(entityType, name, slug, fieldType);
    try {
      var idField = FieldDefinition.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(fd, id);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field definition ID", e);
    }
    return fd;
  }

  private ProjectTemplate createTemplate() {
    var template =
        new ProjectTemplate(
            "Test Template", "{customer} - Audit", "Description", true, "MANUAL", null, MEMBER_ID);
    try {
      var idField = ProjectTemplate.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(template, TEMPLATE_ID);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set template ID", e);
    }
    return template;
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

  private ProjectTemplateService buildProjectTemplateService() {
    return new ProjectTemplateService(
        templateRepository,
        templateTaskRepository,
        templateTaskItemRepository,
        templateTagRepository,
        tagLookupRepository,
        null, // projectTaskRepository
        null, // taskItemRepository
        null, // projectRepository
        null, // projectMemberRepository
        null, // scheduleRepository
        null, // auditService
        null, // eventPublisher
        null, // customerRepository
        null, // customerProjectRepository
        null, // entityTagRepository
        null, // nameTokenResolver
        fieldDefinitionRepository,
        null); // prerequisiteService
  }
}
