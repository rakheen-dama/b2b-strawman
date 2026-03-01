package io.b2mash.b2b.b2bstrawman.prerequisite;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldUtils;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates prerequisite checks for actions that require data completeness. Checks custom field
 * values against fields marked as required for a given context.
 */
@Service
public class PrerequisiteService {

  private final FieldDefinitionService fieldDefinitionService;
  private final CustomerRepository customerRepository;
  private final ProjectTemplateService projectTemplateService;

  public PrerequisiteService(
      FieldDefinitionService fieldDefinitionService,
      CustomerRepository customerRepository,
      ProjectTemplateService projectTemplateService) {
    this.fieldDefinitionService = fieldDefinitionService;
    this.customerRepository = customerRepository;
    this.projectTemplateService = projectTemplateService;
  }

  /**
   * Checks whether all prerequisite fields are filled for the given context and entity.
   *
   * @param context the prerequisite context to evaluate
   * @param entityType the type of entity to check
   * @param entityId the ID of the entity to check
   * @return a PrerequisiteCheck with pass/fail and any violations
   */
  @Transactional(readOnly = true)
  public PrerequisiteCheck checkForContext(
      PrerequisiteContext context, EntityType entityType, UUID entityId) {
    List<FieldDefinition> requiredFields =
        fieldDefinitionService.getRequiredFieldsForContext(entityType, context);

    if (requiredFields.isEmpty()) {
      return PrerequisiteCheck.passed(context);
    }

    // Load the entity's custom fields
    Map<String, Object> customFields = loadCustomFields(entityType, entityId);

    // Evaluate each required field
    List<PrerequisiteViolation> violations =
        requiredFields.stream()
            .filter(fd -> !isFieldFilled(fd, customFields))
            .map(fd -> buildViolation(fd, context, entityType, entityId))
            .toList();

    if (violations.isEmpty()) {
      return PrerequisiteCheck.passed(context);
    }

    return new PrerequisiteCheck(false, context, violations);
  }

  /**
   * Checks engagement-level prerequisites for project creation from a template. Evaluates whether
   * the customer has all required custom fields filled as defined by the template.
   *
   * @param customerId the customer ID
   * @param templateId the project template ID
   * @return a PrerequisiteCheck with pass/fail and any violations
   */
  @Transactional(readOnly = true)
  public PrerequisiteCheck checkEngagementPrerequisites(UUID customerId, UUID templateId) {
    var requiredFields = projectTemplateService.getRequiredCustomerFields(templateId);

    if (requiredFields.isEmpty()) {
      return PrerequisiteCheck.passed(PrerequisiteContext.PROJECT_CREATION);
    }

    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    Map<String, Object> customFields = customer.getCustomFields();

    List<PrerequisiteViolation> violations =
        requiredFields.stream()
            .filter(fd -> !isFieldFilled(fd, customFields))
            .map(
                fd ->
                    buildViolation(
                        fd, PrerequisiteContext.PROJECT_CREATION, EntityType.CUSTOMER, customerId))
            .toList();

    if (violations.isEmpty()) {
      return PrerequisiteCheck.passed(PrerequisiteContext.PROJECT_CREATION);
    }

    return new PrerequisiteCheck(false, PrerequisiteContext.PROJECT_CREATION, violations);
  }

  private Map<String, Object> loadCustomFields(EntityType entityType, UUID entityId) {
    if (entityType == EntityType.CUSTOMER) {
      var customer =
          customerRepository
              .findById(entityId)
              .orElseThrow(() -> new ResourceNotFoundException("Customer", entityId));
      return customer.getCustomFields();
    }
    throw new UnsupportedOperationException(
        "Prerequisite checks not yet supported for entity type: " + entityType);
  }

  private boolean isFieldFilled(FieldDefinition fd, Map<String, Object> customFields) {
    Object value = customFields != null ? customFields.get(fd.getSlug()) : null;
    return CustomFieldUtils.isFieldValueFilled(fd, value);
  }

  private PrerequisiteViolation buildViolation(
      FieldDefinition fd, PrerequisiteContext context, EntityType entityType, UUID entityId) {
    return new PrerequisiteViolation(
        "MISSING_FIELD",
        fd.getName() + " is required for " + context.getDisplayLabel(),
        entityType.name(),
        entityId,
        fd.getSlug(),
        null,
        "Fill the " + fd.getName() + " field on the customer profile");
  }
}
