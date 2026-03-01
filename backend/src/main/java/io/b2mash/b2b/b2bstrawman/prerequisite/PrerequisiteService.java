package io.b2mash.b2b.b2bstrawman.prerequisite;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldUtils;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.setupstatus.DocumentGenerationReadinessService;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates prerequisite checks for actions that require data completeness. Checks custom field
 * values against fields marked as required for a given context, and performs structural checks for
 * action-point prerequisites (e.g., portal contact existence, billing address).
 */
@Service
public class PrerequisiteService {

  private final FieldDefinitionService fieldDefinitionService;
  private final CustomerRepository customerRepository;
  private final ProjectTemplateService projectTemplateService;
  private final PortalContactRepository portalContactRepository;
  private final DocumentGenerationReadinessService documentGenerationReadinessService;
  private final CustomerProjectRepository customerProjectRepository;

  public PrerequisiteService(
      FieldDefinitionService fieldDefinitionService,
      CustomerRepository customerRepository,
      ProjectTemplateService projectTemplateService,
      PortalContactRepository portalContactRepository,
      DocumentGenerationReadinessService documentGenerationReadinessService,
      CustomerProjectRepository customerProjectRepository) {
    this.fieldDefinitionService = fieldDefinitionService;
    this.customerRepository = customerRepository;
    this.projectTemplateService = projectTemplateService;
    this.portalContactRepository = portalContactRepository;
    this.documentGenerationReadinessService = documentGenerationReadinessService;
    this.customerProjectRepository = customerProjectRepository;
  }

  /**
   * Checks whether all prerequisite fields are filled for the given context and entity. Combines
   * custom field checks with structural checks (e.g., portal contact existence).
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

    List<PrerequisiteViolation> violations = new ArrayList<>();

    if (!requiredFields.isEmpty()) {
      // Load the entity's custom fields
      Map<String, Object> customFields = loadCustomFields(entityType, entityId);

      // Evaluate each required field
      violations.addAll(
          requiredFields.stream()
              .filter(fd -> !isFieldFilled(fd, customFields))
              .map(fd -> buildViolation(fd, context, entityType, entityId))
              .toList());
    }

    // Add structural checks
    violations.addAll(checkStructural(context, entityType, entityId));

    if (violations.isEmpty()) {
      return PrerequisiteCheck.passed(context);
    }

    return new PrerequisiteCheck(false, context, violations);
  }

  /**
   * Resolves the customer ID linked to a project via the customer-project relationship.
   *
   * @param projectId the project ID
   * @return the customer UUID
   * @throws ResourceNotFoundException if no customer is linked to the project
   */
  public UUID resolveCustomerIdFromProject(UUID projectId) {
    return customerProjectRepository
        .findFirstCustomerByProjectId(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("CustomerProject", projectId));
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

  private List<PrerequisiteViolation> checkStructural(
      PrerequisiteContext context, EntityType entityType, UUID entityId) {
    List<PrerequisiteViolation> violations = new ArrayList<>();

    switch (context) {
      case INVOICE_GENERATION -> {
        if (entityType == EntityType.CUSTOMER) {
          // Check customer has portal contact with email or customer.email exists
          var customer =
              customerRepository
                  .findById(entityId)
                  .orElseThrow(() -> new ResourceNotFoundException("Customer", entityId));
          var contacts = portalContactRepository.findByCustomerId(entityId);
          boolean hasContactWithEmail =
              contacts.stream().anyMatch(c -> c.getEmail() != null && !c.getEmail().isBlank());
          if (!hasContactWithEmail
              && (customer.getEmail() == null || customer.getEmail().isBlank())) {
            violations.add(
                new PrerequisiteViolation(
                    "STRUCTURAL",
                    "Customer must have an email address or portal contact for invoice delivery",
                    entityType.name(),
                    entityId,
                    null,
                    null,
                    "Add a portal contact with email on the customer detail page,"
                        + " or set the customer email"));
          }
        }
      }
      case PROPOSAL_SEND -> {
        if (entityType == EntityType.CUSTOMER) {
          // Check customer has portal contact with email
          var contacts = portalContactRepository.findByCustomerId(entityId);
          boolean hasContactWithEmail =
              contacts.stream().anyMatch(c -> c.getEmail() != null && !c.getEmail().isBlank());
          if (!hasContactWithEmail) {
            violations.add(
                new PrerequisiteViolation(
                    "STRUCTURAL",
                    "Customer must have a portal contact with an email address to send a proposal",
                    entityType.name(),
                    entityId,
                    null,
                    null,
                    "Add a portal contact with email on the customer detail page"));
          }
        }
      }
      case DOCUMENT_GENERATION -> {
        // Delegate to DocumentGenerationReadinessService
        if (entityType == EntityType.CUSTOMER) {
          var readinessList =
              documentGenerationReadinessService.checkReadiness(
                  TemplateEntityType.CUSTOMER, entityId);
          for (var readiness : readinessList) {
            if (!readiness.ready()) {
              for (var missing : readiness.missingFields()) {
                violations.add(
                    new PrerequisiteViolation(
                        "STRUCTURAL",
                        missing + " is required for document template: " + readiness.templateName(),
                        entityType.name(),
                        entityId,
                        null,
                        null,
                        "Complete the " + missing + " field for document generation"));
              }
            }
          }
        }
      }
      default -> {
        // No structural checks for other contexts (LIFECYCLE_ACTIVATION, PROJECT_CREATION)
      }
    }

    return violations;
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
